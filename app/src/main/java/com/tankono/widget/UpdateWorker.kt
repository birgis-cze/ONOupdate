package com.tankono.widget

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class UpdateWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "tankono_channel"
    }

    override suspend fun doWork(): Result {
        try {
            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()

            val request = Request.Builder()
                .url("https://m.tank-ono.cz/cz/index.php?page=cenik")
                .header("User-Agent", "Mozilla/5.0 (Android) Tasker/1.0")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return Result.failure()
            }

            val html = response.body?.string() ?: return Result.failure()
            val data = parseHtml(html)

            if (data != null) {
                val previous = DataManager.getPrices(applicationContext)
                DataManager.savePrices(applicationContext, data)
                DataManager.saveLastUpdate(applicationContext, System.currentTimeMillis())

                if (previous != null) {
                    checkAndNotify(applicationContext, previous, data)
                }

                TankONOWidget.updateAllWidgets(applicationContext)
                return Result.success()
            }

            return Result.failure()

        } catch (e: Exception) {
            e.printStackTrace()
            return Result.failure()
        }
    }

    private fun parseHtml(html: String): PriceData? {
        try {
            val doc = Jsoup.parse(html)
            val divRows = doc.select("div.divrow2, div.divrow1")

            var n95 = 0.0
            var n95p = 0.0
            var nafta = 0.0
            var lpg = 0.0

            for (row in divRows) {
                val label = row.select("div.divlabel").firstOrNull()?.text() ?: continue
                val priceText = row.select("div.divprice").firstOrNull()?.text()?.replace(",", ".") ?: continue
                val price = priceText.replace(Regex("[^0-9.]"), "").toDoubleOrNull() ?: continue

                when {
                    label.contains("NATURAL 95", ignoreCase = true) -> n95 = price
                    label.contains("NATURAL 95+", ignoreCase = true) -> n95p = price
                    label.contains("Nafta", ignoreCase = true) -> nafta = price
                    label.contains("LPG", ignoreCase = true) -> lpg = price
                }
            }

            return PriceData(n95, n95p, nafta, lpg)

        } catch (e: Exception) {
            return null
        }
    }

    private fun checkAndNotify(context: Context, previous: PriceData, current: PriceData) {
        val changes = mutableListOf<String>()
        val diff = 0.5

        checkChange(previous.n95, current.n95, "N95", diff)?.let { changes.add(it) }
        checkChange(previous.n95p, current.n95p, "N95+", diff)?.let { changes.add(it) }
        checkChange(previous.nafta, current.nafta, "Nafta", diff)?.let { changes.add(it) }
        checkChange(previous.lpg, current.lpg, "LPG", diff)?.let { changes.add(it) }

        if (changes.isNotEmpty()) {
            DataManager.saveChangeNotified(context, true)
            showNotification(context, changes)
        }
    }

    private fun checkChange(old: Double, new: Double, name: String, diff: Double): String? {
        return if (kotlin.math.abs(old - new) >= diff) {
            val direction = if (new > old) "↑" else "↓"
            "$name $direction ${String.format("%.2f", new)} Kč"
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