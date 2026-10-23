package cz.tankono.widget.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import cz.tankono.widget.data.prefs.SettingsStore
import cz.tankono.widget.util.AppLogger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.Calendar
import java.util.concurrent.TimeUnit

object WorkScheduler {

    private const val PERIODIC_WORK_NAME = "tankono_update_periodic"
    private const val ONE_TIME_WORK_NAME = "tankono_update_now"
    private const val PUMP_SYNC_WORK_NAME = "tankono_pump_sync"
    private const val PUMP_SYNC_ONE_TIME = "tankono_pump_sync_now"
    private const val UPDATE_CHECK_WORK_NAME = "tankono_update_check_daily"

    fun schedule(context: Context) {
        val interval = try {
            runBlocking { SettingsStore(context).settings.first().effectiveIntervalMin.toLong() }
        } catch (t: Throwable) {
            AppLogger.w("Nelze načíst nastavení, používám 60 min")
            60L
        }

        AppLogger.i("WorkScheduler: plánuji periodický update každých $interval min")

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

    fun runNow(context: Context) {
        AppLogger.i("WorkScheduler: spouštím jednorázový update ceníku")
        val request = OneTimeWorkRequestBuilder<UpdateWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueue(request)
    }

    fun schedulePumpSync(context: Context) {
        AppLogger.i("WorkScheduler: plánuji denní sync pump (24 h)")

        val request = PeriodicWorkRequestBuilder<PumpSyncWorker>(
            24, TimeUnit.HOURS
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                1, TimeUnit.MINUTES
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PUMP_SYNC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun runPumpSyncNow(context: Context) {
        AppLogger.i("WorkScheduler: spouštím jednorázový sync pump")
        val request = OneTimeWorkRequestBuilder<PumpSyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueue(request)
    }

    /**
     * Naplánuje denní kontrolu aktualizací na 15:00.
     */
    fun scheduleUpdateCheck(context: Context) {
        AppLogger.i("WorkScheduler: plánuji denní kontrolu aktualizací (nejbližší 15:00)")

        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 15)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            // Pokud je už po 15:00, naplánujeme to na zítra
            if (before(now)) {
                add(Calendar.DAY_OF_MONTH, 1)
            }
        }
        
        val delayMs = target.timeInMillis - now.timeInMillis
        AppLogger.d("WorkScheduler: první kontrola za ${delayMs / 1000 / 60} min")

        val request = OneTimeWorkRequestBuilder<UpdateCheckWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            UPDATE_CHECK_WORK_NAME,
            ExistingWorkPolicy.REPLACE, // REPLACE zajistí, že při každém otevření appky se časovač srovná na 15:00
            request
        )
    }
}