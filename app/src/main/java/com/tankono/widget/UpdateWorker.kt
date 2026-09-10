package com.tankono.widget

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UpdateWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "UpdateWorker"
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "tankono_channel"
        
        // ✅ ZABRÁNÍ DUPLICITNÍM BĚHŮM
        @Volatile
        private var isRunning = false
    }

    override suspend fun doWork(): Result {
        val ctx = applicationContext

        if (isRunning) {
            DebugHelper.log(ctx, TAG, "⚠️ UpdateWorker už běží, přeskakuji")
            return Result.success()
        }
        isRunning = true

        try {
            DebugHelper.log(ctx, TAG, "=== UPDATEWORKER SPUŠTĚN ===")

            val previous = DataManager.getPrices(ctx)
            DebugHelper.log(ctx, TAG, "Předchozí: N95=${previous?.n95}")

            val success = DataFetcher.fetchAndSave(ctx)

            if (!success) {
                DebugHelper.log(ctx, TAG, "❌ Stahování selhalo")
                return Result.failure()
            }

            val current = DataManager.getPrices(ctx)
            DebugHelper.log(ctx, TAG, "Nové: N95=${current?.n95}, NM=${current?.nm}, EUR=${current?.euro}")

            if (previous != null && current != null) {
                checkAndNotify(ctx, previous, current)
            }

            // ✅ updateAll na Main
            withContext(Dispatchers.Main) {
                try {
                    TankONOWidget().updateAll(ctx)
                    DebugHelper.log(ctx, TAG, "✅ Widgety aktualizovány")
                } catch (e: Exception) {
                    DebugHelper.log(ctx, TAG, "❌ Chyba: ${e.message}")
                }
            }

            DebugHelper.log(ctx, TAG, "=== ✅ HOTOVO ===")
            return Result.success()

        } catch (e: Exception) {
            DebugHelper.log(ctx, TAG, "❌ CHYBA: ${e.message}")
            return Result.failure()
        } finally {
            isRunning = false
        }
    }

    private fun checkAndNotify(context: Context, previous: PriceData, current: PriceData) {
        val changes = mutableListOf<String>()
        val diff = 0.5

        checkChange(previous.n95, current.n95, "Natural 95", diff)?.let { changes.add(it) }
        checkChange(previous.n95p, current.n95p, "Natural 95+", diff)?.let { changes.add(it) }
        checkChange(previous.n98, current.n98, "Natural 98", diff)?.let { changes.add(it) }
        checkChange(previous.diesel, current.diesel, "Diesel", diff)?.let { changes.add(it) }
        checkChange(previous.dieselPlus, current.dieselPlus, "Diesel+", diff)?.let { changes.add(it) }
        checkChange(previous.lpg, current.lpg, "LPG", diff)?.let { changes.add(it) }
        checkChange(previous.adBlue, current.adBlue, "AdBlue", diff)?.let { changes.add(it) }
        checkChange(previous.om, current.om, "Osobní myčka", diff)?.let { changes.add(it) }
        checkChange(previous.nm, current.nm, "Nákladní myčka", diff)?.let { changes.add(it) }
        checkChange(previous.euro, current.euro, "EUR", diff)?.let { changes.add(it) }

        if (changes.isNotEmpty()) {
            DataManager.saveChangeNotified(context, true)
            showNotification(context, changes)
        }
    }

    private fun checkChange(old: Double, new: Double, name: String, diff: Double): String? {
        return if (kotlin.math.abs(old - new) >= diff) {
            val direction = if (new > old) "↑" else "↓"
            "$name $direction ${String.format("%.2f", new)}"
        } else null
    }

    private fun showNotification(context: Context, changes: List<String>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Tank ONO Aktualizace",
                NotificationManager.IMPORTANCE_HIGH
            )
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("⛽ Změna cen Tank ONO")
            .setContentText(changes.joinToString("\n"))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }
}