package com.tankono.widget

import android.content.Context
import androidx.work.*
import java.util.Calendar
import java.util.concurrent.TimeUnit

object TankONOWidgetScheduler {

    fun scheduleUpdates(context: Context) {
        val workManager = WorkManager.getInstance(context)

        // Zrušíme staré plány
        workManager.cancelUniqueWork("hourly_update")
        workManager.cancelUniqueWork("peak_update")

        // Načteme nastavení
        val peakStart = MainActivity.getPeakStart(context)
        val peakEnd = MainActivity.getPeakEnd(context)
        val peakInterval = MainActivity.getPeakInterval(context)
        val offPeakInterval = MainActivity.getOffPeakInterval(context)

        // OKAMŽITÁ AKTUALIZACE
        val immediateRequest = OneTimeWorkRequestBuilder<UpdateWorker>().build()
        workManager.enqueue(immediateRequest)

        // Základní interval (mimo špičku)
        val baseRequest = PeriodicWorkRequestBuilder<UpdateWorker>(
            offPeakInterval.toLong(), TimeUnit.MINUTES
        ).setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
        ).build()

        // Špička
        val peakRequest = PeriodicWorkRequestBuilder<UpdateWorker>(
            peakInterval.toLong(), TimeUnit.MINUTES
        ).setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
        ).setInitialDelay(
            getInitialDelayToPeak(peakStart),
            TimeUnit.MILLISECONDS
        ).build()

        workManager.enqueueUniquePeriodicWork(
            "hourly_update",
            ExistingPeriodicWorkPolicy.REPLACE,
            baseRequest
        )

        workManager.enqueueUniquePeriodicWork(
            "peak_update",
            ExistingPeriodicWorkPolicy.REPLACE,
            peakRequest
        )

        DebugHelper.log(context, "Scheduler",
            "Naplánováno: špička $peakStart-$peakEnd každých $peakInterval min, mimo každých $offPeakInterval min")
    }

    private fun getInitialDelayToPeak(peakStart: String): Long {
        return try {
            val parts = peakStart.split(":")
            val h = parts[0].toInt()
            val m = parts[1].toInt()

            val now = Calendar.getInstance()
            val peakCalendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, h)
                set(Calendar.MINUTE, m)
                set(Calendar.SECOND, 0)
            }

            if (now.after(peakCalendar)) {
                peakCalendar.add(Calendar.DAY_OF_YEAR, 1)
            }

            peakCalendar.timeInMillis - now.timeInMillis
        } catch (e: Exception) {
            0L
        }
    }
}