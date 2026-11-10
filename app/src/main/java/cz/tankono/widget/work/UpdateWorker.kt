package cz.tankono.widget.work

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import cz.tankono.widget.data.prefs.SettingsStore
import cz.tankono.widget.data.repo.PriceRepository
import cz.tankono.widget.notify.Notifier
import cz.tankono.widget.util.AppLogger
import cz.tankono.widget.util.LocationProvider
import cz.tankono.widget.widget.TankOnoWidget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

class UpdateWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        AppLogger.i("=== UpdateWorker START (id=${id}) ===")

        // Pokus o foreground notifikaci
        try {
            val notification = Notifier.getUpdateInProgressNotification(applicationContext)
            val foregroundInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ForegroundInfo(1002, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                ForegroundInfo(1002, notification)
            }
            setForeground(foregroundInfo)
            AppLogger.d("Foreground notifikace nastavena")
        } catch (t: Throwable) {
            AppLogger.w("Nelze nastavit foreground notifikaci: ${t.message}")
            // Pokud selže foreground, zkusíme to bez něj (např. na starých Androidech)
        }

        // Pokud systém Workera zrušil, končíme, aniž bychom dělali chybu
        if (isStopped) {
            AppLogger.w("UpdateWorker: Worker byl zrušen před začátkem práce.")
            return Result.failure()
        }

        return try {
            val repo = PriceRepository(applicationContext)
            val changed = repo.refresh()

            if (isStopped) {
                AppLogger.w("UpdateWorker: Worker zrušen během refresh.")
                return Result.failure()
            }

            // Zkusit obnovit polohu do cache.
            try {
                val loc = LocationProvider.getCurrentLocation(applicationContext)
                if (loc != null) {
                    SettingsStore(applicationContext).saveCachedLocation(loc.first, loc.second)
                    AppLogger.d("UpdateWorker: cache polohy aktualizována")
                }
            } catch (t: Throwable) {
                AppLogger.w("UpdateWorker: získání polohy selhalo: ${t.message}")
            }

            if (changed) {
                AppLogger.i("Nový ceník – notifikace")
                val settings = SettingsStore(applicationContext).settings.first()
                Notifier.notifyNewPrices(applicationContext, settings.notificationSound)
            } else {
                AppLogger.d("Ceník beze změny")
            }

            TankOnoWidget.requestUpdate(applicationContext)
            AppLogger.d("Požadavek na překreslení widgetu odeslán")

            Result.success()
        } catch (e: CancellationException) {
            AppLogger.w("UpdateWorker: zrušen (CancellationException)")
            Result.failure()
        } catch (t: Throwable) {
            AppLogger.e("Chyba v UpdateWorker", t)
            Result.retry()
        }
    }
}