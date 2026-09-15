package cz.tankono.widget.data.repo

import android.content.Context
import cz.tankono.widget.data.db.AppDatabase
import cz.tankono.widget.data.db.PumpEntity
import cz.tankono.widget.data.remote.PumpScraper
import cz.tankono.widget.util.AppLogger

class PumpRepository(private val context: Context) {

    private val dao = AppDatabase.getInstance(context).pumpDao()

    /**
     * Aktualizuje seznam pump:
     * 1. Stáhne aktuální seznam z webu
     * 2. Uloží do DB (zachová GPS z existujících záznamů)
     */
    suspend fun refreshPumpList(): Int {
        AppLogger.i("PumpRepository: aktualizuji seznam pump…")

        val newPumps = PumpScraper.fetchPumpList()

        if (newPumps.isEmpty()) {
            AppLogger.w("PumpRepository: staženo 0 pump – neukládám")
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
     */
    suspend fun refreshGpsForAllPumps(): Int {
        val pumpsWithoutGps = dao.getPumpsWithoutGps()

        if (pumpsWithoutGps.isEmpty()) {
            AppLogger.d("PumpRepository: všechny pumpy mají GPS")
            return 0
        }

        AppLogger.i("PumpRepository: stahuji GPS pro ${pumpsWithoutGps.size} pump…")

        var successCount = 0

        for (pump in pumpsWithoutGps) {
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
            // Krátká pauza mezi requesty (aby web nezablokoval)
            kotlinx.coroutines.delay(300)
        }

        AppLogger.i("PumpRepository: GPS stažena pro $successCount / ${pumpsWithoutGps.size} pump")
        return successCount
    }

    suspend fun getAll(): List<PumpEntity> = dao.getAll()

    suspend fun count(): Int = dao.count()

    suspend fun getPumpsWithoutGps(): List<PumpEntity> = dao.getPumpsWithoutGps()
}