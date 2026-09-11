package cz.tankono.widget.work

import android.content.Context
import android.util.Log
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
        AppLogger.d("=== UpdateWorker START ===")

        return try {
            val repo = PriceRepository(applicationContext)
            val changed = repo.refresh()

            if (changed) {
                AppLogger.i("Nový ceník – zobrazuji notifikaci")
                Notifier.notifyNewPrices(applicationContext)
            } else {
                AppLogger.d("Ceník beze změny – jen překreslím widget")
            }

            // VŽDY překreslit widget (i když se nic nezměnilo)
            TankOnoWidget.requestUpdate(applicationContext)
            AppLogger.d("Požadavek na překreslení widgetu odeslán")

            // Přeplánovat periodickou práci
            WorkScheduler.schedule(applicationContext)

            Result.success()
        } catch (t: Throwable) {
            AppLogger.e("Chyba v UpdateWorker", t)
            Result.retry()
        }
    }
}