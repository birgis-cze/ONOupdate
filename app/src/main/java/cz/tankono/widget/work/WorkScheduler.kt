package cz.tankono.widget.work

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import cz.tankono.widget.data.prefs.SettingsStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.concurrent.TimeUnit

/**
 * Plánuje aktualizace na pozadí pomocí WorkManager.
 *
 * - Periodická práce: v intervalu dle nastavení (min 15 minut – Android limit)
 * - Jednorázová práce: okamžitá aktualizace (po kliku, po uložení nastavení)
 */
object WorkScheduler {

    private const val TAG = "WorkScheduler"
    private const val PERIODIC_WORK_NAME = "tankono_update_periodic"
    private const val ONE_TIME_WORK_NAME = "tankono_update_now"

    /**
     * Naplánuje periodickou aktualizaci dle aktuálního nastavení.
     * Používá `ExistingPeriodicWorkPolicy.UPDATE` – takže se perioda přepíše,
     * když uživatel v nastavení změní interval.
     */
    fun schedule(context: Context) {
        val interval = try {
            runBlocking { SettingsStore(context).settings.first().effectiveIntervalMin.toLong() }
        } catch (t: Throwable) {
            Log.w(TAG, "Nelze načíst nastavení, používám 60 min", t)
            60L
        }

        Log.d(TAG, "Plánuji periodický update každých $interval min")

        val request = PeriodicWorkRequestBuilder<UpdateWorker>(
            interval, TimeUnit.MINUTES
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                30, TimeUnit.SECONDS
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    /**
     * Okamžitě spustí jednorázovou aktualizaci (bez ohledu na periodu).
     * Použije se po kliku na widget, po uložení nastavení, po prvním spuštění.
     */
    fun runNow(context: Context) {
        Log.d(TAG, "Spouštím jednorázový update")
        val request = OneTimeWorkRequestBuilder<UpdateWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueue(request)
    }
}