package cz.tankono.widget.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import cz.tankono.widget.data.prefs.SettingsStore
import cz.tankono.widget.data.repo.PumpRepository
import cz.tankono.widget.util.AppLogger

/**
 * Worker pro denní synchronizaci pump.
 * - Stáhne seznam pump z webu (přidá nové, smaže zrušené)
 * - Došti GPS pro pumpy, které ho ještě nemají
 * - Uloží timestamp do SettingsStore
 *
 * Běží 1× za 24 h (plánuje WorkScheduler.schedulePumpSync()).
 */
class PumpSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        AppLogger.i("=== PumpSyncWorker START (id=$id) ===")

        return try {
            val repo = PumpRepository(applicationContext)
            val settingsStore = SettingsStore(applicationContext)

            // 1. Sync seznamu pump
            val sync = repo.syncWithWeb()
            if (sync.failed) {
                AppLogger.w("PumpSyncWorker: sync selhal – plánuji retry")
                return Result.retry()
            }
            AppLogger.i("PumpSyncWorker: sync OK – +${sync.added} / -${sync.removed} / ~${sync.updated}")

            // 2. Doštahnout GPS pro nové pumpy
            val gpsCount = repo.refreshGpsForAllPumps()
            AppLogger.i("PumpSyncWorker: GPS došti pro $gpsCount pump")

            // 3. Uložit timestamp
            settingsStore.saveLastPumpSync(System.currentTimeMillis())

            repo.resetProgress()
            AppLogger.i("=== PumpSyncWorker DONE ===")
            Result.success()
        } catch (t: Throwable) {
            AppLogger.e("PumpSyncWorker: chyba", t)
            Result.retry()
        }
    }
}