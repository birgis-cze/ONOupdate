package cz.tankono.widget.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import cz.tankono.widget.data.prefs.SettingsStore
import cz.tankono.widget.util.AppLogger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.concurrent.TimeUnit

object WorkScheduler {

    private const val PERIODIC_WORK_NAME = "tankono_update_periodic"
    private const val ONE_TIME_WORK_NAME = "tankono_update_now"
    private const val PUMP_SYNC_WORK_NAME = "tankono_pump_sync"
    private const val PUMP_SYNC_ONE_TIME = "tankono_pump_sync_now"

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

        try {
            val infos = WorkManager.getInstance(context)
                .getWorkInfosForUniqueWork(PERIODIC_WORK_NAME)
                .get()
            infos.forEach { info ->
                AppLogger.i("WorkScheduler: stav=${info.state}, nextScheduleTime=${info.nextScheduleTimeMillis}")
            }
        } catch (t: Throwable) {
            AppLogger.e("WorkScheduler: nelze získat stav", t)
        }
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

    /**
     * Naplánuje denní sync pump (1× za 24 h).
     * Používá KEEP, aby se při každém otevření appky neresetoval cyklus.
     */
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

        try {
            val infos = WorkManager.getInstance(context)
                .getWorkInfosForUniqueWork(PUMP_SYNC_WORK_NAME)
                .get()
            infos.forEach { info ->
                AppLogger.i("WorkScheduler: pump sync state=${info.state}, next=${info.nextScheduleTimeMillis}")
            }
        } catch (t: Throwable) {
            AppLogger.e("WorkScheduler: nelze získat stav pump sync", t)
        }
    }

    /**
     * Jednorázový sync pump – pro tlačítko "Aktualizovat data".
     */
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
}