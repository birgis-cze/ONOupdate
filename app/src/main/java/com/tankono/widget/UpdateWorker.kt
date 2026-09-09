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
import org.jsoup.nodes.Element
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
        return try {
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

                // AKTUALIZACE WIDGETU PO STAŽENÍ DAT
                TankONOWidget.updateAllWidgets(applicationContext)
                Result.success()
            } else {
                Result.failure()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure()
        }
    }

    private fun parsePriceWithSup(element: Element): Double {
        return try {
            val wholePart = element.ownText().trim()
            val supElement = element.select("sup").first()
            val decimalPart = supElement?.text()?.trim() ?: "00"
            val priceStr = "$wholePart.$decimalPart".replace(",", ".")
            priceStr.toDoubleOrNull() ?: 0.0
        } catch (e: Exception) {
            0.0
        }
    }

    private fun parseHtml(html: String): PriceData? {
        return try {
            val doc = Jsoup.parse(html)
            val table = doc.select("table").first() ?: return null
            val rows = table.select("tr")
            
            var n95 = 0.0
            var n95p = 0.0
            var n98 = 0.0
            var diesel = 0.0
            var dieselPlus = 0.0
            var lpg = 0.0
            var adBlue = 0.0
            var om = 0.0
            var nm = 0.0

            for (row in rows) {
                val cells = row.select("td")
                if (cells.size < 3) continue
                
                val name = cells[0].text().trim()
                val czkPrice = parsePriceWithSup(cells[1])
                
                when {
                    name.contains("NATURAL 95", ignoreCase = true) && !name.contains("+", ignoreCase = true) && !name.contains("98", ignoreCase = true) -> n95 = czkPrice
                    name.contains("NATURAL 95+", ignoreCase = true) -> n95p = czkPrice
                    name.contains("NATURAL 98", ignoreCase = true) -> n98 = czkPrice
                    name.equals("DIESEL", ignoreCase = true) -> diesel = czkPrice
                    name.contains("DIESEL+", ignoreCase = true) -> dieselPlus = czkPrice
                    name.contains("LPG", ignoreCase = true) -> lpg = czkPrice
                    name.contains("AD BLUE", ignoreCase = true) -> adBlue = czkPrice
                    name.equals("OM", ignoreCase = true) -> om = czkPrice
                    name.equals("NM", ignoreCase = true) -> nm = czkPrice
                }
            }
            
            var euro = 0.0
            val euroTable = doc.select("table").getOrNull(1)
            if (euroTable != null) {
                val euroRows = euroTable.select("tr")
                for (row in euroRows) {
                    val cells = row.select("td")
                    if (cells.size >= 3) {
                        val label = cells[0].text().trim()
                        if (label.contains("EURO", ignoreCase = true)) {
                            euro = parsePriceWithSup(cells[1])
                            break
                        }
                    }
                }
            }

            PriceData(
                n95 = n95,
                n95p = n95p,
                n98 = n98,
                diesel = diesel,
                dieselPlus = dieselPlus,
                lpg = lpg,
                adBlue = adBlue,
                om = om,
                nm = nm,
                euro = euro,
                lastUpdate = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun checkAndNotify(context: Context, previous: PriceData, current: PriceData) {
        val changes = mutableListOf<String>()
        val diff = 0.5

        checkChange(previous.n95, current.n95, "N95", diff)?.let { changes.add(it) }
        checkChange(previous.n95p, current.n95p, "N95+", diff)?.let { changes.add(it) }
        checkChange(previous.n98, current.n98, "NATURAL 98", diff)?.let { changes.add(it) }
        checkChange(previous.diesel, current.diesel, "DIESEL", diff)?.let { changes.add(it) }
        checkChange(previous.dieselPlus, current.dieselPlus, "DIESEL+", diff)?.let { changes.add(it) }
        checkChange(previous.lpg, current.lpg, "LPG", diff)?.let { changes.add(it) }
        checkChange(previous.adBlue, current.adBlue, "AD BLUE", diff)?.let { changes.add(it) }
        checkChange(previous.om, current.om, "OM (osobní)", diff)?.let { changes.add(it) }
        checkChange(previous.nm, current.nm, "NM (nákladní)", diff)?.let { changes.add(it) }
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