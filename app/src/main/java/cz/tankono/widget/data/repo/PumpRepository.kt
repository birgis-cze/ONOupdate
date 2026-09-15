package cz.tankono.widget.data.repo

import android.content.Context
import cz.tankono.widget.data.db.AppDatabase
import cz.tankono.widget.data.db.PumpEntity
import cz.tankono.widget.data.remote.PumpScraper
import cz.tankono.widget.util.AppLogger
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Stav průběhu aktualizace pump pro progress bar v UI.
 */
sealed interface PumpRefreshProgress {
    data object Idle : PumpRefreshProgress
    data object FetchingList : PumpRefreshProgress
    data class FetchingGps(val done: Int, val total: Int) : PumpRefreshProgress
    data object Done : PumpRefreshProgress
    data class Error(val message: String) : PumpRefreshProgress
}

data class SyncResult(
    val added: Int,
    val removed: Int,
    val updated: Int,
    val failed: Boolean = false
)

class PumpRepository(private val context: Context) {

    companion object {
        /** TTL pro GPS – po 30 dnech se GPS znovu stáhne. */
        private const val GPS_TTL_MS = 30L * 24 * 60 * 60 * 1000
    }

    private val dao = AppDatabase.getInstance(context).pumpDao()

    private val _progress = MutableStateFlow<PumpRefreshProgress>(PumpRefreshProgress.Idle)
    val progress: StateFlow<PumpRefreshProgress> = _progress.asStateFlow()

    private suspend fun fetchWebList(): List<PumpEntity>? {
        _progress.value = PumpRefreshProgress.FetchingList
        AppLogger.i("PumpRepository: stahuji seznam pump z webu…")
        return try {
            val pumps = PumpScraper.fetchPumpList()
            if (pumps.isEmpty()) {
                AppLogger.w("PumpRepository: web vrátil 0 pump")
                null
            } else pumps
        } catch (t: Throwable) {
            AppLogger.e("PumpRepository: chyba při stahování seznamu", t)
            null
        }
    }

    /**
     * Synchronizuje DB se seznamem z webu.
     */
    suspend fun syncWithWeb(): SyncResult {
        val webPumps = fetchWebList()
            ?: return SyncResult(0, 0, 0, failed = true)

        val webIds = webPumps.map { it.id }.toSet()
        val dbPumps = dao.getAll()
        val dbIds = dbPumps.map { it.id }.toSet()

        // 1. SMAZAT pumpy, které už na webu nejsou
        val toDelete = dbPumps.filter { it.id !in webIds }
        toDelete.forEach { dao.deleteById(it.id) }
        if (toDelete.isNotEmpty()) {
            AppLogger.i("PumpRepository: smazáno ${toDelete.size} pump (už nejsou na webu)")
        }

        // 2. PŘIDAT nové + AKTUALIZOVAT existující
        val existingById = dbPumps.associateBy { it.id }
        val merged = webPumps.map { webPump ->
            val existing = existingById[webPump.id]
            if (existing != null) {
                webPump.copy(
                    lat = existing.lat,
                    lng = existing.lng,
                    lastUpdated = existing.lastUpdated
                )
            } else {
                webPump
            }
        }
        dao.insertAll(merged)

        val added = merged.count { it.id !in dbIds }
        val updated = merged.size - added
        AppLogger.i("PumpRepository: sync hotov – přidáno=$added, smazáno=${toDelete.size}, aktualizováno=$updated")

        return SyncResult(added = added, removed = toDelete.size, updated = updated)
    }

    /**
     * Stáhne GPS pro:
     * - pumpy, které GPS nemají
     * - pumpy, jejichž GPS je starší než 30 dní (TTL)
     *
     * @return počet úspěšně stažených GPS
     */
    suspend fun refreshGpsForAllPumps(): Int {
        // 1. Pumpy bez GPS
        val withoutGps = dao.getPumpsWithoutGps()

        // 2. Pumpy se starou GPS (> 30 dní)
        val cutoff = System.currentTimeMillis() - GPS_TTL_MS
        val staleGps = dao.getPumpsWithStaleGps(cutoff)

        // Sloučit (bez duplicit – teoreticky se nemůžou překrývat, ale pro jistotu)
        val toFetch = (withoutGps + staleGps).distinctBy { it.id }

        if (toFetch.isEmpty()) {
            AppLogger.d("PumpRepository: všechny pumpy mají aktuální GPS")
            return 0
        }

        AppLogger.i(
            "PumpRepository: stahuji GPS pro ${toFetch.size} pump " +
            "(bez GPS: ${withoutGps.size}, staré: ${staleGps.size})"
        )
        _progress.value = PumpRefreshProgress.FetchingGps(0, toFetch.size)

        var successCount = 0
        for ((index, pump) in toFetch.withIndex()) {
            val gps = PumpScraper.fetchGpsForPump(pump)
            if (gps != null) {
                dao.updateGps(
                    id = pump.id,
                    lat = gps.first,
                    lng = gps.second,
                    time = System.currentTimeMillis()
                )
                successCount++
            }
            _progress.value = PumpRefreshProgress.FetchingGps(index + 1, toFetch.size)
            // Randomizovaný delay 500–800 ms (ochrana proti blokaci)
            PumpScraper.delayBetweenRequests()
        }

        AppLogger.i("PumpRepository: GPS stažena pro $successCount / ${toFetch.size} pump")
        return successCount
    }

    fun resetProgress() {
        _progress.value = PumpRefreshProgress.Idle
    }

    suspend fun getAll(): List<PumpEntity> = dao.getAll()
    suspend fun count(): Int = dao.count()
    suspend fun getPumpsWithoutGps(): List<PumpEntity> = dao.getPumpsWithoutGps()

    suspend fun findNearestPump(userLat: Double, userLng: Double): PumpEntity? {
        val pumps = dao.getAllWithGps()
        if (pumps.isEmpty()) {
            AppLogger.w("PumpRepository: žádné pumpy s GPS v DB")
            return null
        }
        return pumps.minByOrNull { haversineKm(userLat, userLng, it.lat!!, it.lng!!) }
    }

    suspend fun getAllSortedByDistance(
        userLat: Double,
        userLng: Double
    ): List<Pair<PumpEntity, Double>> {
        return dao.getAllWithGps()
            .map { it to haversineKm(userLat, userLng, it.lat!!, it.lng!!) }
            .sortedBy { it.second }
    }

    private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2.0) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2.0)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }
}