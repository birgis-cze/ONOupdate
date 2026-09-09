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
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

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
        DebugHelper.log(ctx, TAG, "=== AKTUALIZACE SPUŠTĚNA ===")
        
        return try {
            DebugHelper.log(ctx, TAG, "Krok 1: Kontrola internetu...")
            if (!isNetworkAvailable(ctx)) {
                DebugHelper.log(ctx, TAG, "❌ Není dostupné internetové připojení")
                return Result.failure()
            }
            DebugHelper.log(ctx, TAG, "✅ Internet dostupný")
            
            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()

            DebugHelper.log(ctx, TAG, "Krok 2: Stahování cen...")
            val cenikRequest = Request.Builder()
                .url("https://m.tank-ono.cz/cz/index.php?page=cenik")
                .header("User-Agent", "Mozilla/5.0 (Android) Tasker/1.0")
                .build()

            val cenikResponse = client.newCall(cenikRequest).execute()
            DebugHelper.log(ctx, TAG, "HTTP cenik: ${cenikResponse.code}")
            
            if (!cenikResponse.isSuccessful) {
                DebugHelper.log(ctx, TAG, "❌ HTTP chyba cenik: ${cenikResponse.code}")
                return Result.failure()
            }

            val cenikHtml = cenikResponse.body?.string() ?: run {
                DebugHelper.log(ctx, TAG, "❌ Prázdná odpověď cenik")
                return Result.failure()
            }
            DebugHelper.log(ctx, TAG, "✅ Cenik načten, délka: ${cenikHtml.length} znaků")

            DebugHelper.log(ctx, TAG, "Krok 3: Stahování aktualit...")
            val aktualityRequest = Request.Builder()
                .url("https://m.tank-ono.cz/cz/index.php?page=aktuality")
                .header("User-Agent", "Mozilla/5.0 (Android) Tasker/1.0")
                .build()

            val aktualityResponse = client.newCall(aktualityRequest).execute()
            DebugHelper.log(ctx, TAG, "HTTP aktuality: ${aktualityResponse.code}")
            
            val aktualityHtml = if (aktualityResponse.isSuccessful) {
                aktualityResponse.body?.string() ?: ""
            } else {
                DebugHelper.log(ctx, TAG, "⚠️ HTTP chyba aktuality: ${aktualityResponse.code}")
                ""
            }

            DebugHelper.log(ctx, TAG, "Krok 4: Parsování cen...")
            val priceData = parseCenik(ctx, cenikHtml)
            
            if (priceData == null) {
                DebugHelper.log(ctx, TAG, "❌ Parsování cen selhalo")
                return Result.failure()
            }
            DebugHelper.log(ctx, TAG, "✅ Ceny parsovány: N95=${priceData.n95}, Diesel=${priceData.diesel}")

            DebugHelper.log(ctx, TAG, "Krok 5: Parsování data poslední změny...")
            val lastChangeDate = parseLastChangeDate(ctx, aktualityHtml)
            DebugHelper.log(ctx, TAG, "Datum poslední změny: ${if (lastChangeDate != null) java.util.Date(lastChangeDate) else "Nenalezeno"}")

            DebugHelper.log(ctx, TAG, "Krok 6: Ukládání dat...")
            val previous = DataManager.getPrices(ctx)
            DataManager.savePrices(ctx, priceData)
            DataManager.saveLastUpdate(ctx, System.currentTimeMillis())
            
            if (lastChangeDate != null) {
                DataManager.saveLastChangeDate(ctx, lastChangeDate)
                DebugHelper.log(ctx, TAG, "✅ Datum poslední změny uloženo")
            }

            if (previous != null) {
                checkAndNotify(ctx, previous, priceData)
            }

            DebugHelper.log(ctx, TAG, "Krok 7: Aktualizace widgetu...")
            TankONOWidget.updateAllWidgets(ctx)
            
            DebugHelper.log(ctx, TAG, "=== ✅ AKTUALIZACE ÚSPĚŠNÁ ===")
            Result.success()
            
        } catch (e: Exception) {
            DebugHelper.log(ctx, TAG, "❌ CHYBA: ${e.message}")
            e.printStackTrace()
            Result.failure()
        }
    }

    private fun isNetworkAvailable(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            val activeNetwork = cm.activeNetworkInfo
            activeNetwork != null && activeNetwork.isConnected
        } catch (e: Exception) {
            false
        }
    }

    private fun parseCenik(context: Context, html: String): PriceData? {
        return try {
            val doc = Jsoup.parse(html)
            val priceRows = doc.select("div.divrow2")
            
            var n95 = 0.0
            var n95p = 0.0
            var n98 = 0.0
            var diesel = 0.0
            var dieselPlus = 0.0
            var lpg = 0.0
            var adBlue = 0.0
            var om = 0.0
            var nm = 0.0
            var euroNakup = 0.0

            for (row in priceRows) {
                val labelElement = row.select("div.divprgw, div.divprbw, div.divpryb").first()
                val label = labelElement?.text()?.trim() ?: continue
                
                val priceElement = row.select("div.divprice").first()
                val priceCzk = parsePriceFromElement(priceElement)
                
                when {
                    label.contains("NATURAL 95", ignoreCase = true) && !label.contains("+", ignoreCase = true) && !label.contains("98", ignoreCase = true) -> n95 = priceCzk
                    label.contains("NATURAL 95+", ignoreCase = true) -> n95p = priceCzk
                    label.contains("NATURAL 98", ignoreCase = true) -> n98 = priceCzk
                    label.equals("DIESEL", ignoreCase = true) -> diesel = priceCzk
                    label.contains("DIESEL+", ignoreCase = true) -> dieselPlus = priceCzk
                    label.equals("LPG", ignoreCase = true) -> lpg = priceCzk
                    label.equals("AD BLUE", ignoreCase = true) -> adBlue = priceCzk
                    label.equals("OM", ignoreCase = true) -> om = priceCzk
                    label.equals("NM", ignoreCase = true) -> nm = priceCzk
                }
            }
            
            val euroRows = doc.select("div.divrow2")
            for (row in euroRows) {
                val labelElement = row.select("div.divexbw").first()
                val label = labelElement?.text()?.trim() ?: continue
                
                if (label.equals("EURO", ignoreCase = true)) {
                    val nakupElement = row.select("div.divexnak").first()
                    euroNakup = parsePriceFromElement(nakupElement)
                    break
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
                euro = euroNakup,
                lastUpdate = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            DebugHelper.log(context, TAG, "Chyba parsování cen: ${e.message}")
            null
        }
    }

    private fun parsePriceFromElement(element: org.jsoup.nodes.Element?): Double {
        if (element == null) return 0.0
        return try {
            val wholePart = element.ownText().trim()
            val supElement = element.select("sup").first()
            val decimalPart = supElement?.text()?.trim() ?: "00"
            val cleanWhole = wholePart.replace(" ", "")
            val cleanDecimal = decimalPart.replace(" ", "").padEnd(2, '0')
            val priceStr = "$cleanWhole.$cleanDecimal".replace(",", ".")
            priceStr.toDoubleOrNull() ?: 0.0
        } catch (e: Exception) {
            0.0
        }
    }

    private fun parseLastChangeDate(context: Context, html: String): Long? {
        return try {
            val doc = Jsoup.parse(html)
            val newsElements = doc.select("div.divnews")
            if (newsElements.isEmpty()) return null
            
            val firstNews = newsElements.first()
            val text = firstNews?.text() ?: return null
            
            val pattern = Pattern.compile("(\\d{1,2})\\.(\\d{1,2})\\.(\\d{4})\\s*\\((\\d{2}):(\\d{2}):(\\d{2})\\)")
            val matcher = pattern.matcher(text)
            
            if (matcher.find()) {
                val day = matcher.group(1).toInt()
                val month = matcher.group(2).toInt()
                val year = matcher.group(3).toInt()
                val hour = matcher.group(4).toInt()
                val minute = matcher.group(5).toInt()
                val second = matcher.group(6).toInt()
                
                val calendar = java.util.Calendar.getInstance()
                calendar.set(year, month - 1, day, hour, minute, second)
                calendar.set(java.util.Calendar.MILLISECOND, 0)
                
                calendar.timeInMillis
            } else {
                null
            }
        } catch (e: Exception) {
            DebugHelper.log(context, TAG, "Chyba parsování data: ${e.message}")
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