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

/**
 * Výsledek synchronizace se serverem.
 */
data class SyncResult(
    val added: Int,
    val removed: Int,
    val updated: Int,
    val failed: Boolean = false
)

class PumpRepository(private val context: Context) {

    private val dao = AppDatabase.getInstance(context).pumpDao()

    private val _progress = MutableStateFlow<PumpRefreshProgress>(PumpRefreshProgress.Idle)
    /** Flow průběhu – sleduj v UI pro progress bar. */
    val progress: StateFlow<PumpRefreshProgress> = _progress.asStateFlow()

    /**
     * Stáhne seznam pump z webu (BEZ GPS) a vrátí entity.
     * Slouží jako základ pro syncWithWeb().
     */
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
     * - Nové pumpy PŘIDÁ (bez GPS, GPS se došti později)
     * - Zmizelé pumpy SMAŽE
     * - Stávající pumpy AKTUALIZUJE (název, URL), ale GPS zachová
     *
     * @return SyncResult s počty změn, nebo SyncResult(0,0,0,failed=true) při chybě.
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

        // 2. PŘIDAT nové + AKTUALIZOVAT existující (zachovat GPS)
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
                webPump // nová pumpa bez GPS
            }
        }
        dao.insertAll(merged)

        val added = merged.count { it.id !in dbIds }
        val updated = merged.size - added
        AppLogger.i("PumpRepository: sync hotov – přidáno=$added, smazáno=${toDelete.size}, aktualizováno=$updated")

        return SyncResult(added = added, removed = toDelete.size, updated = updated)
    }

    /**
     * Stáhne GPS pro všechny pumpy, které ho ještě nemají.
     * @return počet úspěšně stažených GPS
     */
    suspend fun refreshGpsForAllPumps(): Int {
        val pumpsWithoutGps = dao.getPumpsWithoutGps()

        if (pumpsWithoutGps.isEmpty()) {
            AppLogger.d("PumpRepository: všechny pumpy mají GPS")
            return 0
        }

        val total = pumpsWithoutGps.size
        AppLogger.i("PumpRepository: stahuji GPS pro $total pump…")
        _progress.value = PumpRefreshProgress.FetchingGps(0, total)

        var successCount = 0
        for ((index, pump) in pumpsWithoutGps.withIndex()) {
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
            _progress.value = PumpRefreshProgress.FetchingGps(index + 1, total)
            delay(300) // pauza mezi requesty
        }

        AppLogger.i("PumpRepository: GPS stažena pro $successCount / $total pump")
        return successCount
    }

    /** Resetuje progress na Idle (volat po dokončení celého flow). */
    fun resetProgress() {
        _progress.value = PumpRefreshProgress.Idle
    }

    suspend fun getAll(): List<PumpEntity> = dao.getAll()
    suspend fun count(): Int = dao.count()
    suspend fun getPumpsWithoutGps(): List<PumpEntity> = dao.getPumpsWithoutGps()

    /**
     * Najde nejbližší pumpu k zadané pozici.
     */
    suspend fun findNearestPump(userLat: Double, userLng: Double): PumpEntity? {
        val pumps = dao.getAllWithGps()
        if (pumps.isEmpty()) {
            AppLogger.w("PumpRepository: žádné pumpy s GPS v DB")
            return null
        }
        return pumps.minByOrNull { haversineKm(userLat, userLng, it.lat!!, it.lng!!) }
    }

    /**
     * Vrátí všechny pumpy s GPS seřazené podle vzdálenosti.
     */
    suspend fun getAllSortedByDistance(
        userLat: Double,
        userLng: Double
    ): List<Pair<PumpEntity, Double>> {
        return dao.getAllWithGps()
            .map { it to haversineKm(userLat, userLng, it.lat!!, it.lng!!) }
            .sortedBy { it.second }
    }

    /** Haversine – vzdálenost v km. */
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