package cz.tankono.widget.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import cz.tankono.widget.data.repo.PriceRepository
import cz.tankono.widget.notify.Notifier
import cz.tankono.widget.util.AppLogger
import cz.tankono.widget.widget.TankOnoWidget

class UpdateWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        AppLogger.i("=== UpdateWorker START (id=${id}) ===")

        // Zobrazit tichou notifikaci
        try {
            Notifier.showUpdateInProgress(applicationContext)
            AppLogger.d("Notifikace 'Aktualizuji…' zobrazena")
        } catch (t: Throwable) {
            AppLogger.w("Nelze zobrazit notifikaci: ${t.message}")
        }

        return try {
            val repo = PriceRepository(applicationContext)
            val changed = repo.refresh()

            if (changed) {
                AppLogger.i("Nový ceník – notifikace")
                Notifier.notifyNewPrices(applicationContext)
                TankOnoWidget.requestUpdate(applicationContext)  // ← jen když se něco změnilo
            } else {
                AppLogger.d("Ceník beze změny")
            }

            TankOnoWidget.requestUpdate(applicationContext)
            AppLogger.d("Požadavek na překreslení widgetu odeslán")

            // Skrýt tichou notifikaci
            try {
                Notifier.hideUpdateInProgress(applicationContext)
                AppLogger.d("Notifikace 'Aktualizuji…' skryta")
            } catch (t: Throwable) {
                AppLogger.w("Nelze skrýt notifikaci: ${t.message}")
            }

            Result.success()
        } catch (t: Throwable) {
            AppLogger.e("Chyba v UpdateWorker", t)

            // Skrýt notifikaci i při chybě
            try {
                Notifier.hideUpdateInProgress(applicationContext)
            } catch (_: Throwable) {}

            Result.retry()
        }
    }
}