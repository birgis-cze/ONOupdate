package cz.tankono.widget.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import cz.tankono.widget.data.prefs.SettingsStore
import cz.tankono.widget.data.repo.PriceRepository
import cz.tankono.widget.notify.Notifier
import cz.tankono.widget.util.AppLogger
import cz.tankono.widget.util.LocationProvider
import cz.tankono.widget.widget.TankOnoWidget
import kotlinx.coroutines.flow.first

class UpdateWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        AppLogger.i("=== UpdateWorker START (id=${id}) ===")

        // Vytvoříme ForegroundInfo. Android tuto notifikaci zobrazí 
        // a sám ji smaže, jakmile Worker skončí.
        try {
            val notification = Notifier.getUpdateInProgressNotification(applicationContext)
            val foregroundInfo = ForegroundInfo(1002, notification)
            setForeground(foregroundInfo)
            AppLogger.d("Foreground notifikace nastavena")
        } catch (t: Throwable) {
            // Pokud se nepodaří nastavit foreground (např. chybí oprávnění), 
            // nespadneme, jen zalogujeme chybu a budeme pokračovat.
            AppLogger.w("Nelze nastavit foreground notifikaci: ${t.message}")
        }

        return try {
            val repo = PriceRepository(applicationContext)
            val changed = repo.refresh()

            // Zkusit obnovit polohu do cache.
            try {
                val loc = LocationProvider.getCurrentLocation(applicationContext)
                if (loc != null) {
                    SettingsStore(applicationContext).saveCachedLocation(loc.first, loc.second)
                    AppLogger.d(
                        "UpdateWorker: cache polohy aktualizována (${"%.4f".format(loc.first)}, ${"%.4f".format(loc.second)})"
                    )
                } else {
                    AppLogger.d("UpdateWorker: polohu nezískáno, použije se cache")
                }
            } catch (t: Throwable) {
                AppLogger.w("UpdateWorker: získání polohy selhalo: ${t.message}")
            }

            if (changed) {
                AppLogger.i("Nový ceník – notifikace")
                // Získáme nastavení a předáme zvuk
                val settings = SettingsStore(applicationContext).settings.first()
                Notifier.notifyNewPrices(applicationContext, settings.notificationSound)
            } else {
                AppLogger.d("Ceník beze změny")
            }

            // Překreslit widget
            TankOnoWidget.requestUpdate(applicationContext)
            AppLogger.d("Požadavek na překreslení widgetu odeslán")

            // TADY UŽ NENÍ POTŘEBA hideUpdateInProgress.
            // Android notifikaci smaže sám, jakmile tato funkce vrátí Result.success()
            Result.success()
        } catch (t: Throwable) {
            AppLogger.e("Chyba v UpdateWorker", t)
            // Android notifikaci smaže i při Result.retry()
            Result.retry()
        }
    }
}