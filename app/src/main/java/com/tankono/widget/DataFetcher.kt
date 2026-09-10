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

            // 1. Stažení ceníku
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

            val cenikHtml = cenikResponse.body?.string() ?: ""
            if (cenikHtml.isEmpty()) {
                DebugHelper.log(context, TAG, "❌ Prázdný ceník")
                return false
            }

            // 2. Stažení aktualit
            DebugHelper.log(context, TAG, "Stahuji aktuality...")
            val aktualityRequest = Request.Builder()
                .url("https://m.tank-ono.cz/cz/index.php?page=aktuality")
                .header("User-Agent", "Mozilla/5.0 (Android) Tasker/1.0")
                .build()

            val aktualityResponse = client.newCall(aktualityRequest).execute()
            val aktualityHtml = if (aktualityResponse.isSuccessful) aktualityResponse.body?.string() else null

            // 3. Parsování dat ceníku
            val currentPrices = DataManager.getPrices(context)
            val parsedPrices = parseCenik(cenikHtml, currentPrices)

            DebugHelper.log(context, TAG, "✅ Ceny parsovány: N95=${parsedPrices.n95}, Diesel=${parsedPrices.diesel}")

            // 4. Parsování datumu změny
            if (!aktualityHtml.isNullOrEmpty()) {
                val changeTimestamp = parseLastChangeDate(aktualityHtml)
                if (changeTimestamp != null && changeTimestamp > 0) {
                    val sdf = java.text.SimpleDateFormat("dd.MM.", java.util.Locale.getDefault())
                    val lastChangeFormatted = sdf.format(java.util.Date(changeTimestamp))
                    DataManager.saveLastChangeDate(context, lastChangeFormatted)
                    DebugHelper.log(context, TAG, "✅ Datum změny uloženo: $lastChangeFormatted")
                }
            }

            // 5. Uložení stažených cen a času
            DataManager.savePrices(context, parsedPrices)
            DataManager.saveLastUpdate(context, System.currentTimeMillis())

            DebugHelper.log(context, TAG, "=== ✅ STAHOVÁNÍ ÚSPĚŠNÉ ===")
            true
        } catch (e: Exception) {
            DebugHelper.log(context, TAG, "❌ Výjimka při stahování: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    private fun parseCenik(html: String, existingPrices: DataManager.PricesData?): DataManager.PricesData {
        return try {
            val doc = Jsoup.parse(html)
            val rows = doc.select("div.divrow2")

            var n95 = existingPrices?.n95 ?: 0.0
            var n95p = existingPrices?.n95p ?: 0.0
            var n98 = existingPrices?.n98 ?: 0.0
            var diesel = existingPrices?.diesel ?: 0.0
            var dieselPlus = existingPrices?.dieselPlus ?: 0.0
            var lpg = existingPrices?.lpg ?: 0.0
            var adBlue = existingPrices?.adBlue ?: 0.0
            var om = existingPrices?.om ?: 0.0
            var nm = existingPrices?.nm ?: 0.0
            var euro = existingPrices?.euro ?: 0.0

            for (row in rows) {
                val labelElement = row.selectFirst("div[class^=divpr], div[class^=divex]") ?: continue
                val label = labelElement.text().trim().uppercase()

                val price = if (label.contains("EURO")) {
                    val exPro = row.selectFirst("div.divexpro")
                    parsePriceFromElement(exPro)
                } else {
                    val priceElem = row.selectFirst("div.divprice")
                    parsePriceFromElement(priceElem)
                }

                if (price > 0.0) {
                    when {
                        label.contains("NATURAL 95+") -> n95p = price
                        label.contains("NATURAL 95") -> n95 = price
                        label.contains("NATURAL 98") -> n98 = price
                        label.contains("DIESEL+") -> dieselPlus = price
                        label.contains("DIESEL") -> diesel = price
                        label.contains("LPG") -> lpg = price
                        label.contains("AD BLUE") || label.contains("ADBLUE") -> adBlue = price
                        label.contains("OM") -> om = price
                        label.contains("NM") -> nm = price
                        label.contains("EURO") -> euro = price
                    }
                }
            }

            DataManager.PricesData(
                n95 = n95,
                n95p = n95p,
                n98 = n98,
                diesel = diesel,
                dieselPlus = dieselPlus,
                lpg = lpg,
                adBlue = adBlue,
                om = om,
                nm = nm,
                euro = euro
            )
        } catch (e: Exception) {
            e.printStackTrace()
            existingPrices ?: DataManager.PricesData()
        }
    }

    private fun parsePriceFromElement(element: org.jsoup.nodes.Element?): Double {
        if (element == null) return 0.0
        return try {
            val sup = element.selectFirst("sup")?.text()?.trim() ?: "00"
            val mainPart = element.ownText().replace("[^0-9]".toRegex(), "").trim()
            
            if (mainPart.isEmpty()) return 0.0

            "$mainPart.$sup".toDoubleOrNull() ?: 0.0
        } catch (e: Exception) {
            0.0
        }
    }

    private fun parseLastChangeDate(html: String): Long? {
        return try {
            val doc = Jsoup.parse(html)
            val firstNews = doc.selectFirst("div.divnews") ?: return null
            val text = firstNews.text()

            val pattern = Pattern.compile("(\\d{1,2})\\.(\\d{1,2})\\.(\\d{4})\\s*\\((\\d{2}):(\\d{2}):(\\d{2})\\)")
            val matcher = pattern.matcher(text)

            if (matcher.find()) {
                val day = matcher.group(1)?.toIntOrNull() ?: return null
                val month = matcher.group(2)?.toIntOrNull() ?: return null
                val year = matcher.group(3)?.toIntOrNull() ?: return null
                val hour = matcher.group(4)?.toIntOrNull() ?: 0
                val minute = matcher.group(5)?.toIntOrNull() ?: 0
                val second = matcher.group(6)?.toIntOrNull() ?: 0

                val calendar = java.util.Calendar.getInstance()
                calendar.set(year, month - 1, day, hour, minute, second)
                calendar.set(java.util.Calendar.MILLISECOND, 0)
                calendar.timeInMillis
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}