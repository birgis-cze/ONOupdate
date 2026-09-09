package com.tankono.widget

import android.content.Context
import androidx.work.*
import java.util.Calendar
import java.util.concurrent.TimeUnit

object TankONOWidgetScheduler {

    fun scheduleUpdates(context: Context) {
        // Základní – každou hodinu
        val hourlyRequest = PeriodicWorkRequestBuilder<UpdateWorker>(
            1, TimeUnit.HOURS
        ).setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
        ).build()

        // Špička – každých 10 minut (14:30–16:00)
        val peakRequest = PeriodicWorkRequestBuilder<UpdateWorker>(
            10, TimeUnit.MINUTES
        ).setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
        ).setInitialDelay(
            getInitialDelayToPeak(),
            TimeUnit.MILLISECONDS
        ).build()

        val workManager = WorkManager.getInstance(context)

        workManager.enqueueUniquePeriodicWork(
            "hourly_update",
            ExistingPeriodicWorkPolicy.KEEP,
            hourlyRequest
        )

        workManager.enqueueUniquePeriodicWork(
            "peak_update",
            ExistingPeriodicWorkPolicy.KEEP,
            peakRequest
        )
    }

    private fun getInitialDelayToPeak(): Long {
        val now = Calendar.getInstance()
        val peakStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 14)
            set(Calendar.MINUTE, 30)
            set(Calendar.SECOND, 0)
        }

        return if (now.after(peakStart)) {
            // Už je po 14:30 – spustí se zítra
            peakStart.add(Calendar.DAY_OF_YEAR, 1)
            peakStart.timeInMillis - now.timeInMillis
        } else {
            peakStart.timeInMillis - now.timeInMillis
        }
    }
}