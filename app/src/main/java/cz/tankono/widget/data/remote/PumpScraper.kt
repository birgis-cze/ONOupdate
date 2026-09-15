package cz.tankono.widget.data.remote

import cz.tankono.widget.data.db.PumpEntity
import cz.tankono.widget.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

object PumpScraper {

    private const val URL_PUMPS = "https://www.tank-ono.cz/cz/index.php?page=pumpy"
    private const val BASE_URL = "https://www.tank-ono.cz/cz/"
    private const val USER_AGENT = "Mozilla/5.0 (Android) TankOnoWidget/1.0"
    private const val TIMEOUT_MS = 15_000

    /**
     * Stáhne stránku pump a vrátí seznam všech čerpacích stanic.
     *
     * Struktura HTML:
     *   <a href="index.php?page=pumpcard&pump=1">ČS Plzeň, Domažlická</a>
     *   <a href="index.php?page=pumpcard&pump=2">ČS Nýřany</a>
     *   ...
     */
    suspend fun fetchPumpList(): List<PumpEntity> = withContext(Dispatchers.IO) {
        try {
            AppLogger.d("PumpScraper: stahuji seznam pump…")

            val doc = Jsoup.connect(URL_PUMPS)
                .userAgent(USER_AGENT)
                .timeout(TIMEOUT_MS)
                .get()

            // Najít všechny odkazy na pumpcard
            val links = doc.select("a[href*=pumpcard]")

            val pumps = mutableMapOf<Int, PumpEntity>()

            for (link in links) {
                val href = link.attr("href")
                val name = link.text().trim()

                // Extrahovat ID z href (např. "index.php?page=pumpcard&pump=1")
                val id = Regex("""pump=(\d+)""")
                    .find(href)
                    ?.groupValues
                    ?.get(1)
                    ?.toIntOrNull()
                    ?: continue

                // Přeskočit duplicitní (mapa má už toto ID)
                if (pumps.containsKey(id)) continue

                // Sestavit plnou URL
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
}