package cz.tankono.widget.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import androidx.core.app.NotificationCompat
import cz.tankono.widget.R
import cz.tankono.widget.util.AppLogger

object Notifier {

    // Nový ID kanálu pro ceník se zvukem. 
    // Změna ID je nutná, protože Android nedovolí změnit zvuk u existujícího kanálu.
    private const val CHANNEL_ID_NEW_PRICES = "tankono_prices_sound_v3"
    
    private const val CHANNEL_SILENT_ID = "tankono_silent_v2"
    private const val CHANNEL_UPDATE_ID = "tankono_update_available"
    
    private const val NOTIF_ID_NEW = 1001
    private const val NOTIF_ID_PROGRESS = 1002
    private const val NOTIF_ID_UPDATE = 1003

    /** 
     * Kanál pro nový ceník – SE ZVUKEM. 
     */
    fun ensureChannel(ctx: Context) {
        val mgr = ctx.getSystemService(NotificationManager::class.java)
        
        // Pokud kanál ještě neexistuje, vytvoříme ho
        if (mgr.getNotificationChannel(CHANNEL_ID_NEW_PRICES) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID_NEW_PRICES,
                "Změna ceníku (se zvukem)",
                NotificationManager.IMPORTANCE_HIGH // HIGH zaručí zobrazení jako heads-up (vyskočí na obrazovce)
            ).apply {
                description = "Upozornění na nový ceník Tank ONO s vlastním zvukem"
                
                // Nastavení vlastního zvuku z res/raw/tank_ono_notify.mp3
                try {
                    val soundUri = Uri.parse(
                        ContentResolver.SCHEME_ANDROID_RESOURCE + "://" +
                        ctx.packageName + "/" + R.raw.tank_ono_notify
                    )
                    val audioAttributes = AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .build()
                    
                    setSound(soundUri, audioAttributes)
                    enableVibration(true) // Necháme vibrace, ať to uživatel cítí
                } catch (t: Throwable) {
                    AppLogger.e("Notifier: Nepodařilo se nastavit vlastní zvuk", t)
                }
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
     * Kanál pro upozornění na novou verzi aplikace (tichý, ale viditelný).
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

    /** Notifikace o novém ceníku (používá nový kanál se zvukem). */
    fun notifyNewPrices(ctx: Context) {
        ensureChannel(ctx) // Vytvoří kanál, pokud ještě není

        val notification = NotificationCompat.Builder(ctx, CHANNEL_ID_NEW_PRICES)
            .setSmallIcon(R.drawable.ic_notif_station)
            .setContentTitle("Tank ONO")
            .setContentText("Byl zveřejněn nový ceník.")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        val mgr = ctx.getSystemService(NotificationManager::class.java)
        mgr.notify(NOTIF_ID_NEW, notification)
    }

    /** Tichá notifikace "Aktualizuji data…" – zobrazí se, dokud update běží. */
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

    /** Skryje tichou notifikaci (po dokončení update). */
    fun hideUpdateInProgress(ctx: Context) {
        val mgr = ctx.getSystemService(NotificationManager::class.java)
        mgr.cancel(NOTIF_ID_PROGRESS)
    }

    /**
     * Tiché upozornění, že je dostupná nová verze aplikace.
     * Kliknutím se otevře aplikace, kde je připravené tlačítko "Stáhnout verzi".
     */
    fun notifyUpdateAvailable(ctx: Context, version: String) {
        ensureUpdateChannel(ctx)

        // Klik otevře aplikaci
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
}