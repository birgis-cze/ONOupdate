package cz.tankono.widget.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import cz.tankono.widget.R

object Notifier {

    private const val CHANNEL_ID = "tankono_prices"
    private const val CHANNEL_SILENT_ID = "tankono_silent_v2"
    private const val NOTIF_ID_NEW = 1001
    private const val NOTIF_ID_PROGRESS = 1002

    /** Kanál pro nový ceník (normální notifikace). */
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

    /**
     * Kanál pro tichou notifikaci během update.
     * IMPORTANCE_DEFAULT = viditelná v horní liště (ikona).
     * setSound(null) = ticho.
     */
    private fun ensureSilentChannel(ctx: Context) {
        val mgr = ctx.getSystemService(NotificationManager::class.java)
        if (mgr.getNotificationChannel(CHANNEL_SILENT_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_SILENT_ID,
                "Aktualizace na pozadí",
                NotificationManager.IMPORTANCE_DEFAULT   // ← DEFAULT (ikona v liště)
            ).apply {
                description = "Tiché upozornění na probíhající aktualizaci"
                setSound(null, null)                     // ← TICHO
                enableVibration(false)
                enableLights(false)
                setShowBadge(false)
            }
            mgr.createNotificationChannel(channel)
        }
    }

    /** Notifikace o novém ceníku. */
    fun notifyNewPrices(ctx: Context) {
        ensureChannel(ctx)

        val notification = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notif_station)   // ← NOVÁ IKONA
            .setContentTitle("Tank ONO")
            .setContentText("Byl zveřejněn nový ceník.")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        val mgr = ctx.getSystemService(NotificationManager::class.java)
        mgr.notify(NOTIF_ID_NEW, notification)
    }

    /** Tichá notifikace "Aktualizuji data…" – zobrazí se, dokud update běží. */
    fun showUpdateInProgress(ctx: Context) {
        ensureSilentChannel(ctx)

        val notification = NotificationCompat.Builder(ctx, CHANNEL_SILENT_ID)
            .setSmallIcon(R.drawable.ic_notif_station)   // ← NOVÁ IKONA
            .setContentTitle("Tank ONO")
            .setContentText("Aktualizuji data…")
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .build()

        val mgr = ctx.getSystemService(NotificationManager::class.java)
        mgr.notify(NOTIF_ID_PROGRESS, notification)
    }

    /** Skryje tichou notifikaci (po dokončení update). */
    fun hideUpdateInProgress(ctx: Context) {
        val mgr = ctx.getSystemService(NotificationManager::class.java)
        mgr.cancel(NOTIF_ID_PROGRESS)
    }
}