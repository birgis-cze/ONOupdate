package cz.tankono.widget.data.remote

import cz.tankono.widget.data.db.PumpEntity
import cz.tankono.widget.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.net.HttpURLConnection
import java.net.URL

object PumpScraper {

    private const val URL_PUMPS = "https://www.tank-ono.cz/cz/index.php?page=pumpy"
    private const val BASE_URL = "https://www.tank-ono.cz/cz/"
    private const val USER_AGENT = "Mozilla/5.0 (Android) TankOnoWidget/1.0"
    private const val TIMEOUT_MS = 15_000
    private const val REDIRECT_TIMEOUT_MS = 10_000

    /**
     * Stáhne stránku pump a vrátí seznam všech čerpacích stanic.
     */
    suspend fun fetchPumpList(): List<PumpEntity> = withContext(Dispatchers.IO) {
        try {
            AppLogger.d("PumpScraper: stahuji seznam pump…")

            val doc = Jsoup.connect(URL_PUMPS)
                .userAgent(USER_AGENT)
                .timeout(TIMEOUT_MS)
                .get()

            val links = doc.select("a[href*=pumpcard]")
            val pumps = mutableMapOf<Int, PumpEntity>()

            for (link in links) {
                val href = link.attr("href")
                val name = link.text().trim()

                val id = Regex("""pump=(\d+)""")
                    .find(href)
                    ?.groupValues
                    ?.get(1)
                    ?.toIntOrNull()
                    ?: continue

                if (pumps.containsKey(id)) continue

                val detailUrl = if (href.startsWith("http")) href else BASE_URL + href

                pumps[id] = PumpEntity(
                    id = id,
                    name = name,
                    detailUrl = detailUrl,
                    lat = null,
                    lng = null,
                    lastUpdated = 0L
                )
            }

            AppLogger.i("PumpScraper: nalezeno ${pumps.size} pump")
            pumps.values.sortedBy { it.id }
        } catch (t: Throwable) {
            AppLogger.e("PumpScraper: chyba při stahování seznamu", t)
            emptyList()
        }
    }

    /**
     * Stáhne detail pumpy a extrahuje GPS souřadnice.
     *
     * Postup:
     * 1. Stáhne HTML detailu (např. index.php?page=pumpcard&pump=28)
     * 2. Najde odkaz na Google Maps (např. https://goo.gl/maps/zcNw1)
     * 3. Následuje přesměrování → získá plnou URL
     * 4. Extrahuje GPS z URL (regex @lat,lng)
     *
     * Vrací Pair(lat, lng) nebo null při chybě.
     */
    suspend fun fetchGpsForPump(pump: PumpEntity): Pair<Double, Double>? =
        withContext(Dispatchers.IO) {
            try {
                AppLogger.d("PumpScraper: GPS pro ${pump.name} (ID ${pump.id})…")

                // 1. Stáhnout detail pumpy
                val doc = Jsoup.connect(pump.detailUrl)
                    .userAgent(USER_AGENT)
                    .timeout(TIMEOUT_MS)
                    .get()

                // 2. Najít odkaz na Google Maps (goo.gl nebo maps.google)
                val mapsLink = doc.select("a[href*=goo.gl/maps], a[href*=maps.google], a[href*=google.com/maps]")
                    .firstOrNull()
                    ?.attr("href")
                    ?.trim()

                if (mapsLink.isNullOrBlank()) {
                    AppLogger.w("PumpScraper: odkaz na mapu nenalezen pro ${pump.name}")
                    return@withContext null
                }

                AppLogger.d("PumpScraper: maps link = $mapsLink")

                // 3. Následovat přesměrování
                val finalUrl = followRedirects(mapsLink)
                if (finalUrl == null) {
                    AppLogger.w("PumpScraper: nepodařilo se následovat přesměrování")
                    return@withContext null
                }

                AppLogger.d("PumpScraper: final URL = $finalUrl")

                // 4. Extrahovat GPS z URL
                val gps = extractGpsFromUrl(finalUrl)
                if (gps == null) {
                    AppLogger.w("PumpScraper: GPS v URL nenalezena")
                    return@withContext null
                }

                AppLogger.i("PumpScraper: ${pump.name} → ${gps.first}, ${gps.second}")
                gps
            } catch (t: Throwable) {
                AppLogger.e("PumpScraper: chyba GPS pro ${pump.name}", t)
                null
            }
        }

    /**
     * Následuje HTTP přesměrování (301, 302, 303, 307, 308).
     * Vrací finální URL nebo null.
     */
    private fun followRedirects(url: String, maxHops: Int = 10): String? {
        var current = url
        var hops = 0

        while (hops < maxHops) {
            try {
                val conn = URL(current).openConnection() as HttpURLConnection
                conn.instanceFollowRedirects = false
                conn.requestMethod = "HEAD"
                conn.setRequestProperty("User-Agent", USER_AGENT)
                conn.connectTimeout = REDIRECT_TIMEOUT_MS
                conn.readTimeout = REDIRECT_TIMEOUT_MS
                conn.connect()

                val code = conn.responseCode
                val location = conn.getHeaderField("Location")
                conn.disconnect()

                if (code in 300..399 && !location.isNullOrBlank()) {
                    current = if (location.startsWith("http")) {
                        location
                    } else {
                        // Relativní přesměrování
                        val base = URL(current)
                        URL(base, location).toString()
                    }
                    hops++
                    continue
                }

                // Konec – ne přesměrování
                return current
            } catch (t: Throwable) {
                AppLogger.w("PumpScraper: chyba při následování přesměrování: ${t.message}")
                return current
            }
        }
        return current
    }

    /**
     * Extrahuje GPS z Google Maps URL.
     *
     * Podporované formáty:
     *   .../@49.6789,18.1234,17z/...
     *   ...?q=49.6789,18.1234
     *   ...?ll=49.6789,18.1234
     *   ...?query=49.6789,18.1234
     */
    private fun extractGpsFromUrl(url: String): Pair<Double, Double>? {
        // Formát 1: @lat,lng
        val regex1 = Regex("""@(-?\d+\.\d+),(-?\d+\.\d+)""")
        regex1.find(url)?.let { m ->
            val lat = m.groupValues[1].toDoubleOrNull()
            val lng = m.groupValues[2].toDoubleOrNull()
            if (lat != null && lng != null) return lat to lng
        }

        // Formát 2: q= / ll= / query= / daddr=
        val regex2 = Regex("""[?&](?:q|ll|query|daddr)=(-?\d+\.\d+),(-?\d+\.\d+)""")
        regex2.find(url)?.let { m ->
            val lat = m.groupValues[1].toDoubleOrNull()
            val lng = m.groupValues[2].toDoubleOrNull()
            if (lat != null && lng != null) return lat to lng
        }

        return null
    }
}