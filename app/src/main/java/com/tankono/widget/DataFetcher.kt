package com.tankono.widget

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object DataFetcher {

    private const val TAG = "DataFetcher"

    suspend fun fetchAndSave(context: Context): Boolean {
        DebugHelper.log(context, TAG, "=== STAHOVÁNÍ DAT ===")

        return try {
            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()

            // Stáhneme ceník
            DebugHelper.log(context, TAG, "Stahuji ceník...")
            val cenikRequest = Request.Builder()
                .url("https://m.tank-ono.cz/cz/index.php?page=cenik")
                .header("User-Agent", "Mozilla/5.0 (Android) Tasker/1.0")
                .build()

            val cenikResponse = client.newCall(cenikRequest).execute()
            if (!cenikResponse.isSuccessful) {
                DebugHelper.log(context, TAG, "❌ HTTP chyba cenik: ${cenikResponse.code}")
                return false
            }

            val cenikHtml = cenikResponse.body?.string() ?: run {
                DebugHelper.log(context, TAG, "❌ Prázdná odpověď cenik")
                return false
            }
            DebugHelper.log(context, TAG, "✅ Cenik načten, délka: ${cenikHtml.length}")

            // Stáhneme aktuality
            DebugHelper.log(context, TAG, "Stahuji aktuality...")
            val aktualityRequest = Request.Builder()
                .url("https://m.tank-ono.cz/cz/index.php?page=aktuality")
                .header("User-Agent", "Mozilla/5.0 (Android) Tasker/1.0")
                .build()

            val aktualityResponse = client.newCall(aktualityRequest).execute()
            val aktualityHtml = if (aktualityResponse.isSuccessful) {
                aktualityResponse.body?.string() ?: ""
            } else {
                DebugHelper.log(context, TAG, "⚠️ HTTP chyba aktuality: ${aktualityResponse.code}")
                ""
            }

            // Parsujeme ceny
            val priceData = parseCenik(context, cenikHtml) ?: run {
                DebugHelper.log(context, TAG, "❌ Parsování cen selhalo")
                return false
            }
            DebugHelper.log(context, TAG, "✅ Ceny parsovány: N95=${priceData.n95}, Diesel=${priceData.diesel}")

            // Parsujeme datum změny
            val lastChangeDate = parseLastChangeDate(context, aktualityHtml)
            DebugHelper.log(context, TAG, "Datum poslední změny: ${if (lastChangeDate != null) java.util.Date(lastChangeDate) else "Nenalezeno"}")

            // Uložíme data
            DataManager.savePrices(context, priceData)
            DataManager.saveLastUpdate(context, System.currentTimeMillis())

            if (lastChangeDate != null) {
                DataManager.saveLastChangeDate(context, lastChangeDate)
                DebugHelper.log(context, TAG, "✅ Datum změny uloženo")
            }

            DebugHelper.log(context, TAG, "=== ✅ STAHOVÁNÍ ÚSPĚŠNÉ ===")
            true

        } catch (e: Exception) {
            DebugHelper.log(context, TAG, "❌ CHYBA: ${e.message}")
            e.printStackTrace()
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
                    label.equals("DIESEL", ignoreCase = true) && !label.contains("+", ignoreCase = true) -> diesel = priceCzk
                    label.contains("DIESEL+", ignoreCase = true) -> dieselPlus = priceCzk
                    label.equals("LPG", ignoreCase = true) -> lpg = priceCzk
                    label.equals("AD BLUE", ignoreCase = true) -> adBlue = priceCzk
                    label.equals("OM", ignoreCase = true) -> om = priceCzk
                    label.equals("NM", ignoreCase = true) -> nm = priceCzk
                }
            }

            // EUR
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
            DebugHelper.log(context, TAG, "Chyba parsování: ${e.message}")
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
                // ✅ BEZPEČNÉ PARSOVÁNÍ (odstraní warning "Unsafe use of nullable receiver")
                val day = matcher.group(1)?.toIntOrNull() ?: 0
                val month = matcher.group(2)?.toIntOrNull() ?: 1
                val year = matcher.group(3)?.toIntOrNull() ?: 2026
                val hour = matcher.group(4)?.toIntOrNull() ?: 0
                val minute = matcher.group(5)?.toIntOrNull() ?: 0
                val second = matcher.group(6)?.toIntOrNull() ?: 0

                val calendar = java.util.Calendar.getInstance()
                calendar.set(year, month - 1, day, hour, minute, second)
                calendar.set(java.util.Calendar.MILLISECOND, 0)
                calendar.timeInMillis
            } else null
        } catch (e: Exception) {
            DebugHelper.log(context, TAG, "Chyba parsování data: ${e.message}")
            null
        }
    }
}