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
     * 2. Uloží do DB (nahradí staré)
     *
     * Vrací počet pump.
     */
    suspend fun refreshPumpList(): Int {
        AppLogger.i("PumpRepository: aktualizuji seznam pump…")

        val newPumps = PumpScraper.fetchPumpList()

        if (newPumps.isEmpty()) {
            AppLogger.w("PumpRepository: staženo 0 pump – neukládám")
            return 0
        }

        // Zjistit existující pumpy (kvůli GPS)
        val existing = dao.getAll().associateBy { it.id }

        // Zachovat GPS z existujících záznamů
        val merged = newPumps.map { newPump ->
            val oldPump = existing[newPump.id]
            if (oldPump != null && oldPump.lat != null && oldPump.lng != null) {
                // Zachovat GPS
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
     * Vrátí všechny pumpy z DB.
     */
    suspend fun getAll(): List<PumpEntity> = dao.getAll()

    /**
     * Vrátí počet pump v DB.
     */
    suspend fun count(): Int = dao.count()

    /**
     * Vrátí pumpy bez GPS (pro Fázi 2b).
     */
    suspend fun getPumpsWithoutGps(): List<PumpEntity> = dao.getPumpsWithoutGps()
}