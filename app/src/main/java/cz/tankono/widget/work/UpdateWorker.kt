package cz.tankono.widget.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import cz.tankono.widget.notify.Notifier
import cz.tankono.widget.data.repo.PriceRepository
import cz.tankono.widget.widget.TankOnoWidget

/**
 * Worker, který:
 *  1. Zkontroluje aktuality a případně stáhne nový ceník
 *  2. Pokud se ceník změnil, zobrazí notifikaci
 *  3. Překreslí widget
 *  4. Přeplánuje periodickou práci (kdyby se změnil interval)
 */
class UpdateWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Log.d(TAG, "UpdateWorker start")

        return try {
            val repo = PriceRepository(applicationContext)
            val changed = repo.refresh()

            if (changed) {
                Log.d(TAG, "Nový ceník – zobrazuji notifikaci")
                Notifier.notifyNewPrices(applicationContext)
            } else {
                Log.d(TAG, "Ceník beze změny")
            }

            // Překreslit widget
            TankOnoWidget.requestUpdate(applicationContext)

            // Přeplánovat (kdyby se změnil interval v nastavení)
            WorkScheduler.schedule(applicationContext)

            Result.success()
        } catch (t: Throwable) {
            Log.e(TAG, "Chyba v UpdateWorker", t)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "UpdateWorker"
    }
}