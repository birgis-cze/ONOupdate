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

        return try {
            val repo = PriceRepository(applicationContext)
            val changed = repo.refresh()

            if (changed) {
                AppLogger.i("Nový ceník – notifikace")
                Notifier.notifyNewPrices(applicationContext)
            } else {
                AppLogger.d("Ceník beze změny")
            }

            TankOnoWidget.requestUpdate(applicationContext)
            AppLogger.d("Požadavek na překreslení widgetu odeslán")

            // POZOR: schedule už nevoláme tady, aby se neresetoval časovač
            // (periodická práce se automaticky znovu naplánuje)

            Result.success()
        } catch (t: Throwable) {
            AppLogger.e("Chyba v UpdateWorker", t)
            Result.retry()
        }
    }
}