package cz.tankono.widget.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import cz.tankono.widget.R
import cz.tankono.widget.util.AppLogger
import cz.tankono.widget.util.SoundHelper

object Notifier {

    private const val CHANNEL_ID = "tankono_prices"
    private const val CHANNEL_SILENT_ID = "tankono_silent_v2"
    private const val CHANNEL_UPDATE_ID = "tankono_update_available"
    
    private const val NOTIF_ID_NEW = 1001
    private const val NOTIF_ID_PROGRESS = 1002
    private const val NOTIF_ID_UPDATE = 1003

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
     */
    private fun ensureSilentChannel(ctx: Context) {
        val mgr = ctx.getSystemService(NotificationManager::class.java)
        if (mgr.getNotificationChannel(CHANNEL_SILENT_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_SILENT_ID,
                "Aktualizace na pozadí",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Tiché upozornění na probíhající aktualizaci"
                setSound(null, null)
                enableVibration(false)
                enableLights(false)
                setShowBadge(false)
            }
            mgr.createNotificationChannel(channel)
        }
    }

    /**
     * Kanál pro upozornění na novou verzi aplikace.
     */
    private fun ensureUpdateChannel(ctx: Context) {
        val mgr = ctx.getSystemService(NotificationManager::class.java)
        if (mgr.getNotificationChannel(CHANNEL_UPDATE_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_UPDATE_ID,
                "Nová verze aplikace",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Tiché upozornění na dostupnou novou verzi aplikace"
                setSound(null, null)
                enableVibration(false)
                enableLights(false)
                setShowBadge(true)
            }
            mgr.createNotificationChannel(channel)
        }
    }

    /**
     * Notifikace o novém ceníku s vybraným zvukem.
     * Pokud je preferredSound prázdný, dynamicky vybere první dostupný zvuk.
     */
    fun notifyNewPrices(ctx: Context, preferredSound: String) {
        // Zjistíme, jaký zvuk se má reálně přehrát
        val soundToPlay = if (preferredSound.isBlank() || ctx.resources.getIdentifier(preferredSound, "raw", ctx.packageName) == 0) {
            SoundHelper.getAvailableSounds(ctx).firstOrNull()?.fileName ?: ""
        } else {
            preferredSound
        }

        if (soundToPlay.isEmpty()) {
            AppLogger.w("Notifier: V res/raw nejsou žádné zvuky, notifikace bude bez zvuku.")
        }

        // Vytvoříme unikátní ID kanálu pro každý zvuk (Android nedovolí měnit zvuk u existujícího)
        val safeSoundName = soundToPlay.replace(" ", "_").lowercase()
        val dynamicChannelId = "tankono_prices_$safeSoundName"
        
        val mgr = ctx.getSystemService(NotificationManager::class.java)
        
        if (mgr.getNotificationChannel(dynamicChannelId) == null) {
            val channel = NotificationChannel(
                dynamicChannelId,
                "Změna ceníku",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Upozornění na nový ceník Tank ONO"
                
                // Najdeme resource ID zvuku podle jeho názvu
                val resId = ctx.resources.getIdentifier(soundToPlay, "raw", ctx.packageName)
                if (resId != 0) {
                    val soundUri = android.net.Uri.parse("android.resource://${ctx.packageName}/$resId")
                    val audioAttributes = android.media.AudioAttributes.Builder()
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                        .build()
                    setSound(soundUri, audioAttributes)
                }
            }
            mgr.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(ctx, dynamicChannelId)
            .setSmallIcon(R.drawable.ic_notif_station)
            .setContentTitle("Tank ONO")
            .setContentText("Byl zveřejněn nový ceník.")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        mgr.notify(NOTIF_ID_NEW, notification)
    }

    /** Tichá notifikace "Aktualizuji data…" */
    fun showUpdateInProgress(ctx: Context) {
        ensureSilentChannel(ctx)

        val notification = NotificationCompat.Builder(ctx, CHANNEL_SILENT_ID)
            .setSmallIcon(R.drawable.ic_notif_station)
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

    /** Skryje tichou notifikaci */
    fun hideUpdateInProgress(ctx: Context) {
        val mgr = ctx.getSystemService(NotificationManager::class.java)
        mgr.cancel(NOTIF_ID_PROGRESS)
    }

    /** Tiché upozornění na novou verzi aplikace */
    fun notifyUpdateAvailable(ctx: Context, version: String) {
        ensureUpdateChannel(ctx)

        val intent = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pendingIntent = if (intent != null) {
            PendingIntent.getActivity(
                ctx, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else null

        val notification = NotificationCompat.Builder(ctx, CHANNEL_UPDATE_ID)
            .setSmallIcon(R.drawable.ic_notif_station)
            .setContentTitle("Nová verze Tank ONO")
            .setContentText("K dispozici je verze $version. Klepněte pro aktualizaci.")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("K dispozici je nová verze $version. Klepněte pro otevření aplikace a stažení."))
            .setAutoCancel(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .apply { if (pendingIntent != null) setContentIntent(pendingIntent) }
            .build()

        val mgr = ctx.getSystemService(NotificationManager::class.java)
        mgr.notify(NOTIF_ID_UPDATE, notification)
    }

    /**
     * Smaže staré notifikační kanály pro změnu ceníku, které uživatel už nepoužívá.
     * Volá se POUZE tehdy, když uživatel v nastavení změní zvuk.
     */
    fun cleanupUnusedPriceChannels(ctx: Context, currentSound: String) {
        // API 26+ (Android 8.0+), na starších systémech kanály neexistují
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) return
        
        val mgr = ctx.getSystemService(NotificationManager::class.java)
        val allChannels = mgr.notificationChannels ?: return
        
        // Zjistíme, jaký zvuk se reálně používá (pokud je prázdný, vezmeme první dostupný)
        val soundToPlay = if (currentSound.isBlank() || ctx.resources.getIdentifier(currentSound, "raw", ctx.packageName) == 0) {
            cz.tankono.widget.util.SoundHelper.getAvailableSounds(ctx).firstOrNull()?.fileName ?: ""
        } else {
            currentSound
        }

        val safeSoundName = soundToPlay.replace(" ", "_").lowercase()
        val currentChannelId = "tankono_prices_$safeSoundName"

        // Projdeme všechny kanály a smažeme ty pro ceník, které nejsou ten aktuální
        allChannels.forEach { channel ->
            if (channel.id.startsWith("tankono_prices_") && channel.id != currentChannelId) {
                AppLogger.d("Notifier: Mažu nepoužívaný notifikační kanál ${channel.id}")
                mgr.deleteNotificationChannel(channel.id)
            }
        }
    }
}