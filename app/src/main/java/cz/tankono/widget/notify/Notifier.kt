package cz.tankono.widget.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import cz.tankono.widget.R

/**
 * Zobrazuje notifikaci o novém ceníku.
 * Kanál vytváříme lazy (při první notifikaci).
 */
object Notifier {

    private const val CHANNEL_ID = "tankono_prices"
    private const val NOTIF_ID = 1001

    /** Vytvoří kanál, pokud ještě neexistuje (idempotentní). */
    fun ensureChannel(ctx: Context) {
        val mgr = ctx.getSystemService(NotificationManager::class.java)
        if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Změna ceníku",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Upozornění na nový ceník Tank ONO"
            }
            mgr.createNotificationChannel(channel)
        }
    }

    /** Zobrazí notifikaci o novém ceníku. */
    fun notifyNewPrices(ctx: Context) {
        ensureChannel(ctx)

        val notification = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("Tank ONO")
            .setContentText("Byl zveřejněn nový ceník.")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        val mgr = ctx.getSystemService(NotificationManager::class.java)
        mgr.notify(NOTIF_ID, notification)
    }
}