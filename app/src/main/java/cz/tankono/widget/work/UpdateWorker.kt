package cz.tankono.widget.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import cz.tankono.widget.data.prefs.SettingsStore
import cz.tankono.widget.data.repo.PriceRepository
import cz.tankono.widget.notify.Notifier
import cz.tankono.widget.util.AppLogger
import cz.tankono.widget.util.LocationProvider
import cz.tankono.widget.widget.TankOnoWidget

class UpdateWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        AppLogger.i("=== UpdateWorker START (id=${id}) ===")

        try {
            Notifier.showUpdateInProgress(applicationContext)
            AppLogger.d("Notifikace 'Aktualizuji…' zobrazena")
        } catch (t: Throwable) {
            AppLogger.w("Nelze zobrazit notifikaci: ${t.message}")
        }

        return try {
            val repo = PriceRepository(applicationContext)
            val changed = repo.refresh()

            // Zkusit obnovit polohu do cache.
            // S ACCESS_BACKGROUND_LOCATION to funguje i na pozadí.
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
                Notifier.notifyNewPrices(applicationContext)
            } else {
                AppLogger.d("Ceník beze změny")
            }

            // Překreslit widget (jen jednou)
            TankOnoWidget.requestUpdate(applicationContext)
            AppLogger.d("Požadavek na překreslení widgetu odeslán")

            try {
                Notifier.hideUpdateInProgress(applicationContext)
                AppLogger.d("Notifikace 'Aktualizuji…' skryta")
            } catch (t: Throwable) {
                AppLogger.w("Nelze skrýt notifikaci: ${t.message}")
            }

            Result.success()
        } catch (t: Throwable) {
            AppLogger.e("Chyba v UpdateWorker", t)
            try {
                Notifier.hideUpdateInProgress(applicationContext)
            } catch (_: Throwable) {}
            Result.retry()
        }
    }
}