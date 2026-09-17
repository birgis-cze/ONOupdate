package cz.tankono.widget.data.remote

import cz.tankono.widget.data.model.PriceEntry
import cz.tankono.widget.data.model.PriceSnapshot
import cz.tankono.widget.data.model.Product
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Calendar
import java.util.Locale

object TankOnoScraper {

    private const val URL_NEWS  = "https://m.tank-ono.cz/cz/index.php?page=aktuality"
    private const val URL_PRICE = "https://m.tank-ono.cz/cz/index.php?page=cenik"
    private const val USER_AGENT = "Mozilla/5.0 (Android) TankOnoWidget/1.0"
    private const val TIMEOUT_MS = 15_000

    /**
     * Vrátí datum+čas poslední aktuality obsahující "nový ceník",
     * ve formátu ze stránky, např. "10.9.2026 (14:42:44)".
     */
    suspend fun fetchLatestNewsDate(): String? = withContext(Dispatchers.IO) {
        runCatching {
            val doc = Jsoup.connect(URL_NEWS)
                .userAgent(USER_AGENT)
                .timeout(TIMEOUT_MS)
                .get()

            // Najdi div.divnews, který obsahuje "nový ceník"
            val news = doc.select("div.divnews")
            val target = news.firstOrNull {
                it.text().contains("nový ceník", ignoreCase = true)
            } ?: news.firstOrNull()

            // Text má formát "10.9.2026 (14:42:44)Zveřejněn nový ceník."
            // Datum je v prvním textovém uzlu (před <br />)
            target?.let {
                val text = it.ownText().trim()
                if (text.contains("(")) text else {
                    // Fallback – vytáhneme regexem z celého textu
                    Regex("""\d{1,2}\.\d{1,2}\.\d{4}\s*\(\d{1,2}:\d{2}:\d{2}\)""")
                        .find(it.text())?.value
                }
            }
        }.getOrNull()
    }

    /** Stáhne a naparsuje ceník. Vrátí null při chybě. */
    suspend fun fetchPrices(): PriceSnapshot? = withContext(Dispatchers.IO) {
        runCatching {
            val doc = Jsoup.connect(URL_PRICE)
                .userAgent(USER_AGENT)
                .timeout(TIMEOUT_MS)
                .get()

            val map = mutableMapOf<Product, PriceEntry>()

            // Každý produkt je v <div class="divrow2"> s:
            //   <div class="divprgw|divprbw|divpryb">NÁZEV</div>
            //   <div class="divprice"> 42<sup>50</sup> </div>
            //   <div class="divpriceeu"> 1<sup>763</sup></div>
            for (row in doc.select("div.divrow2")) {
                val nameEl = row.selectFirst("div.divprgw, div.divprbw, div.divpryb")
                    ?: continue
                val product = matchProduct(nameEl.text().trim()) ?: continue

                val czk = parsePrice(row.selectFirst("div.divprice"))
                val eur = parsePrice(row.selectFirst("div.divpriceeu"))

                map[product] = PriceEntry(product = product, czk = czk, eur = eur)
            }

            // Směnárna – EURO nákup / prodej
            val euroRow = doc.selectFirst("div.divrow2:has(div.divexbw)")
            if (euroRow != null) {
                val nak = parsePrice(euroRow.selectFirst("div.divexnak"))
                val pro = parsePrice(euroRow.selectFirst("div.divexpro"))
                map[Product.EUR_BUY]  = PriceEntry(Product.EUR_BUY,  czk = nak, eur = null)
                map[Product.EUR_SELL] = PriceEntry(Product.EUR_SELL, czk = pro, eur = null)
            }

            if (map.isEmpty()) return@runCatching null

            val published = fetchLatestNewsDate()

            PriceSnapshot(
                entries = map,
                publishedAt = published,
                fetchedAt = System.currentTimeMillis()
            )
        }.getOrNull()
    }

    /** Přiřadí název z HTML k našemu Product. */
    private fun matchProduct(raw: String): Product? {
        val n = raw.uppercase(Locale.ROOT).replace(Regex("\\s+"), "")
        return when (n) {
            "NATURAL95+" -> Product.NATURAL_95_PLUS
            "NATURAL95"  -> Product.NATURAL_95
            "NATURAL98"  -> Product.NATURAL_98
            "DIESEL+"    -> Product.DIESEL_PLUS
            "DIESEL"     -> Product.DIESEL
            "LPG"        -> Product.LPG
            "ADBLUE"     -> Product.AD_BLUE
            "OM"         -> Product.OM
            "NM"         -> Product.NM
            else -> null
        }
    }

    /**
     * Parsuje cenu z elementu. Obsah <sup> je VŽDY desetinná část.
     *   <div class="divprice"> 42<sup>50</sup> </div>      → 4250 (42,50 Kč)
     *   <div class="divprice">649<sup>00</sup> </div>      → 64900 (649,00 Kč)
     *   <div class="divpriceeu"> 1<sup>763</sup></div>     → 1763 (1,763 €)
     *   <div class="divpriceeu">26<sup>929</sup></div>     → 26929 (26,929 €)
     */
    private fun parsePrice(el: Element?): Int? {
        if (el == null) return null

        val sup = el.selectFirst("sup")?.text()?.trim().orEmpty()
        val whole = el.clone()
            .apply { select("sup").remove() }
            .text()
            .replace(Regex("[^0-9]"), "")
            .trim()

        return if (whole.isNotEmpty() && sup.isNotEmpty()) {
            (whole + sup).toIntOrNull()
        } else {
            // Fallback – když <sup> chybí
            val digits = el.text().replace(Regex("[^0-9]"), "")
            digits.toIntOrNull()
        }
    }

    /**
     * "10.9.2026 (14:42:44)" → "čt 10.9.26 14:42"
     * Vrací null, když vstup je prázdný.
     */
    fun formatPublished(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        return runCatching {
            val m = Regex("""(\d{1,2})\.(\d{1,2})\.(\d{4})\s*\((\d{1,2}):(\d{2}):(\d{2})\)""")
                .find(raw) ?: return@runCatching raw
            val (d, mo, y, h, mi, _) = m.destructured

            val cal = Calendar.getInstance().apply {
                set(y.toInt(), mo.toInt() - 1, d.toInt(), h.toInt(), mi.toInt(), 0)
            }
            val loc = Locale("cs", "CZ")
            val dayFmt  = SimpleDateFormat("EEE", loc)
            val dateFmt = SimpleDateFormat("d.M.yy", loc)
            val timeFmt = SimpleDateFormat("H:mm", loc)

            "${dayFmt.format(cal.time)} ${dateFmt.format(cal.time)} ${timeFmt.format(cal.time)}"
        }.getOrDefault(raw)
    }

    /**
     * Parsuje "15.9.2026 (14:53:47)" na LocalDate.
     * Vrací null při chybě.
     */
    fun parseDateFromPublished(publishedAt: String?): LocalDate? {
        if (publishedAt.isNullOrBlank()) return null
        return try {
            val m = Regex("""(\d{1,2})\.(\d{1,2})\.(\d{4})""").find(publishedAt) ?: return null
            val (d, mo, y) = m.destructured
            LocalDate.of(y.toInt(), mo.toInt(), d.toInt())
        } catch (t: Throwable) {
            null
        }
    }

    /**
     * Vrátí true, pokud je datum zveřejnění DNEŠNÍ.
     * Používá se pro rozhodnutí, zda zobrazit trend (▲/▼).
     */
    fun isPublishedToday(publishedAt: String?): Boolean {
        val pubDate = parseDateFromPublished(publishedAt) ?: return false
        return pubDate == LocalDate.now()
    }
}