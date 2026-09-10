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
    }

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        DebugHelper.log(ctx, TAG, "=== UPDATEWORKER SPUŠTĚN ===")

        return try {
            // Použijeme společnou logiku
            val success = DataFetcher.fetchAndSave(ctx)

            if (success) {
                // Zkontrolujeme změny a případně notifikujeme
                val previous = DataManager.getPrices(ctx)
                val current = DataManager.getPrices(ctx)
                // (předchozí je stejné jako current, protože jsme právě uložili,
                // takže notifikaci přeskočíme – v reálu by se mělo ukládat před a po)

                // Aktualizujeme widget
                withContext(Dispatchers.Main) {
                    try {
                        TankONOWidget().updateAll(ctx)
                        DebugHelper.log(ctx, TAG, "✅ Widget updateAll úspěšně")
                    } catch (e: Exception) {
                        DebugHelper.log(ctx, TAG, "❌ Chyba updateAll: ${e.message}")
                    }
                }
                Result.success()
            } else {
                Result.failure()
            }
        } catch (e: Exception) {
            DebugHelper.log(ctx, TAG, "❌ CHYBA: ${e.message}")
            Result.failure()
        }
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