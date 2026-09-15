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
 * Stav průběhu aktualizace pump.
 * Používá se pro progress bar v MainActivity.
 */
sealed interface PumpRefreshProgress {
    /** Nic neběží. */
    data object Idle : PumpRefreshProgress

    /** Stahuji seznam pump z webu. */
    data object FetchingList : PumpRefreshProgress

    /** Stahuji GPS pro pumpy bez GPS. */
    data class FetchingGps(val done: Int, val total: Int) : PumpRefreshProgress

    /** Vše hotovo. */
    data object Done : PumpRefreshProgress

    /** Chyba při stahování. */
    data class Error(val message: String) : PumpRefreshProgress
}

class PumpRepository(private val context: Context) {

    private val dao = AppDatabase.getInstance(context).pumpDao()

    private val _progress = MutableStateFlow<PumpRefreshProgress>(PumpRefreshProgress.Idle)
    /** Flow průběhu – sleduj v UI pro progress bar. */
    val progress: StateFlow<PumpRefreshProgress> = _progress.asStateFlow()

    /**
     * Aktualizuje seznam pump:
     * 1. Stáhne aktuální seznam z webu
     * 2. Uloží do DB (zachová GPS z existujících záznamů)
     */
    suspend fun refreshPumpList(): Int {
        _progress.value = PumpRefreshProgress.FetchingList
        AppLogger.i("PumpRepository: aktualizuji seznam pump…")

        val newPumps = try {
            PumpScraper.fetchPumpList()
        } catch (t: Throwable) {
            AppLogger.e("PumpRepository: chyba při stahování seznamu", t)
            _progress.value = PumpRefreshProgress.Error(t.message ?: "Chyba stahování seznamu")
            return 0
        }

        if (newPumps.isEmpty()) {
            AppLogger.w("PumpRepository: staženo 0 pump – neukládám")
            _progress.value = PumpRefreshProgress.Error("Staženo 0 pump")
            return 0
        }

        // Zachovat GPS z existujících záznamů
        val existing = dao.getAll().associateBy { it.id }

        val merged = newPumps.map { newPump ->
            val oldPump = existing[newPump.id]
            if (oldPump != null && oldPump.lat != null && oldPump.lng != null) {
                newPump.copy(
                    lat = oldPump.lat,
                    lng = oldPump.lng,
                    lastUpdated = oldPump.lastUpdated
                )
            } else {
                newPump
            }
        }

        dao.insertAll(merged)
        AppLogger.i("PumpRepository: uloženo ${merged.size} pump")

        return merged.size
    }

    /**
     * Stáhne GPS pro všechny pumpy, které ho ještě nemají.
     * Vrací počet úspěšně stažených GPS.
     * Průběh hlásí přes [progress].
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
            // Krátká pauza mezi requesty (aby web nezablokoval)
            delay(300)
        }

        AppLogger.i("PumpRepository: GPS stažena pro $successCount / $total pump")
        return successCount
    }

    /**
     * Nastaví progress na Idle (volat po dokončení celého refresh flow).
     */
    fun resetProgress() {
        _progress.value = PumpRefreshProgress.Idle
    }

    suspend fun getAll(): List<PumpEntity> = dao.getAll()

    suspend fun count(): Int = dao.count()

    suspend fun getPumpsWithoutGps(): List<PumpEntity> = dao.getPumpsWithoutGps()

    /**
     * Najde nejbližší pumpu k zadané GPS pozici.
     * Vrací null, pokud v DB nejsou žádné pumpy s GPS.
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
     * Vrátí všechny pumpy s GPS seřazené podle vzdálenosti od zadané pozice.
     * Vrací List<Pair<pumpa, vzdálenost v km>>.
     */
    suspend fun getAllSortedByDistance(
        userLat: Double,
        userLng: Double
    ): List<Pair<PumpEntity, Double>> {
        val pumps = dao.getAllWithGps()
        return pumps
            .map { it to haversineKm(userLat, userLng, it.lat!!, it.lng!!) }
            .sortedBy { it.second }
    }

    /**
     * Vypočítá vzdálenost mezi dvěma GPS body pomocí Haversine.
     * @return vzdálenost v km
     */
    private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0 // poloměr Země v km
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2.0) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2.0)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }
}