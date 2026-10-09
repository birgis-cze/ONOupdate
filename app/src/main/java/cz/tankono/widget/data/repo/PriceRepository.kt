package cz.tankono.widget.data.repo

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import cz.tankono.widget.data.model.PriceEntry
import cz.tankono.widget.data.model.PriceSnapshot
import cz.tankono.widget.data.model.PriceState
import cz.tankono.widget.data.model.Product
import cz.tankono.widget.data.remote.TankOnoScraper
import cz.tankono.widget.util.AppLogger
import kotlinx.coroutines.flow.first

private val Context.priceDataStore by preferencesDataStore(name = "tankono_prices")

class PriceRepository(private val context: Context) {

    private object Keys {
        val PUBLISHED_OLD = stringPreferencesKey("published_old")
        val FETCHED_OLD   = longPreferencesKey("fetched_old")
        val DATA_OLD      = stringPreferencesKey("data_old")

        val PUBLISHED_NEW = stringPreferencesKey("published_new")
        val FETCHED_NEW   = longPreferencesKey("fetched_new")
        val DATA_NEW      = stringPreferencesKey("data_new")
    }

    /**
     * Refresh ceníku. Chová se takto: 
     *
     * 1. Stáhne datum z aktualit.
     * 2. Když je datum stejné a máme v DataStore ceny → jen aktualizuje fetchedAt.
     * 3. Když je datum nové NEBO chybí ceny → stáhne ceník a ATOMICKY uloží datum + ceny.
     * 4. Když stahování selže nebo vrátí prázdná data → NEUKLÁDÁ NIC
     *    (datum zůstane staré, aby další pokus zkusil znovu).
     */
    suspend fun refresh(): Boolean {
        AppLogger.d("--- refresh() START ---")

        // 1. Získat datum z aktualit
        AppLogger.d("Stahuji datum z aktualit…")
        val news = TankOnoScraper.fetchLatestNewsDate()
        AppLogger.d("Datum z aktualit: $news")

        if (news == null) {
            AppLogger.w("Nepodařilo se získat datum z aktualit → nic neměním")
            return false
        }

        // 2. Přečíst aktuální stav DataStore
        val prefs = context.priceDataStore.data.first()
        val storedPublished = prefs[Keys.PUBLISHED_NEW]
        val storedData      = prefs[Keys.DATA_NEW]

        // Máme v DataStore reálná data? (ne prázdný string)
        val hasData = !storedData.isNullOrBlank() && storedData.contains(":")

        AppLogger.d("Uložené datum: $storedPublished, data: ${if (hasData) "OK" else "CHYBÍ"}")

        // 3. Datum stejné + máme data → jen aktualizovat fetchedAt
        if (storedPublished != null && news == storedPublished && hasData) {
            AppLogger.d("Datum stejné + máme ceny → jen aktualizuji fetchedAt")
            context.priceDataStore.edit { p ->
                p[Keys.FETCHED_NEW] = System.currentTimeMillis()
            }
            return false
        }

        // 4. Potřebujeme stáhnout ceník (nové datum, nebo chybí data)
        val reason = when {
            storedPublished == null -> "žádné datum"
            !hasData                -> "chybí data"
            else                    -> "nové datum"
        }
        AppLogger.i("Stahuji ceník (důvod: $reason)")

        val snapshot = TankOnoScraper.fetchPrices()

        // 5. KLÍČOVÉ: Pokud stahování selhalo nebo vrátilo prázdná data,
        //    NEUKLÁDÁME nové datum. Datum a ceny musí být vždy konzistentní!
        if (snapshot == null) {
            AppLogger.w("Ceník se nepodařilo stáhnout → NEUKLÁDÁM datum")
            return false
        }
        if (snapshot.entries.isEmpty()) {
            AppLogger.w("Ceník je prázdný (0 položek) → NEUKLÁDÁM datum")
            return false
        }

        AppLogger.d("Ceník stažen: ${snapshot.entries.size} položek")

        // 6. ATOMICKÝ zápis: datum + ceny společně v jednom edit bloku
        context.priceDataStore.edit { p ->
            val oldPublished = p[Keys.PUBLISHED_NEW]
            val oldFetched   = p[Keys.FETCHED_NEW]
            val oldData      = p[Keys.DATA_NEW]

            // Posunout old ← new jen pokud máme skutečná stará data
            if (oldPublished != null && oldData != null && oldData.contains(":")) {
                p[Keys.PUBLISHED_OLD] = oldPublished
                p[Keys.FETCHED_OLD]   = oldFetched ?: 0L
                p[Keys.DATA_OLD]      = oldData
            }

            // Napsat nové datum + ceny SPOLEČNĚ
            p[Keys.PUBLISHED_NEW] = snapshot.publishedAt ?: news
            p[Keys.FETCHED_NEW]   = snapshot.fetchedAt
            p[Keys.DATA_NEW]      = serialize(snapshot)
        }

        AppLogger.i("Ceník uložen do DataStore (${snapshot.entries.size} položek)")
        return true
    }

    /**
     * Vynucený refresh – vždy stáhne ceník ze serveru.
     * Používá se pro ruční "Aktualizovat data" a klik na widget.
     */
    suspend fun forceRefresh(): Boolean {
        AppLogger.d("--- forceRefresh() START ---")

        val snapshot = TankOnoScraper.fetchPrices()
        if (snapshot == null) {
            AppLogger.w("forceRefresh: nepodařilo se stáhnout ceník → nic neměním")
            return false
        }
        if (snapshot.entries.isEmpty()) {
            AppLogger.w("forceRefresh: ceník je prázdný → nic neměním")
            return false
        }

        val stored = context.priceDataStore.data.first()[Keys.PUBLISHED_NEW]

        // Stejné datum → jen aktualizovat fetchedAt (ceny jsou stejné)
        if (stored != null && stored == snapshot.publishedAt) {
            AppLogger.d("forceRefresh: datum stejné, aktualizuji jen fetchedAt")
            context.priceDataStore.edit { p ->
                p[Keys.FETCHED_NEW] = snapshot.fetchedAt
            }
            return false
        }

        // Nové datum → posunout old ← new a uložit nové
        AppLogger.i("forceRefresh: nová data, posouvám old ← new")
        context.priceDataStore.edit { p ->
            val oldPublished = p[Keys.PUBLISHED_NEW]
            val oldFetched   = p[Keys.FETCHED_NEW]
            val oldData      = p[Keys.DATA_NEW]

            if (oldPublished != null && oldData != null && oldData.contains(":")) {
                p[Keys.PUBLISHED_OLD] = oldPublished
                p[Keys.FETCHED_OLD]   = oldFetched ?: 0L
                p[Keys.DATA_OLD]      = oldData
            }

            p[Keys.PUBLISHED_NEW] = snapshot.publishedAt ?: ""
            p[Keys.FETCHED_NEW]   = snapshot.fetchedAt
            p[Keys.DATA_NEW]      = serialize(snapshot)
        }
        return true
    }

    suspend fun loadState(): PriceState {
        val p = context.priceDataStore.data.first()
        val current = deserialize(p[Keys.DATA_NEW], p[Keys.PUBLISHED_NEW], p[Keys.FETCHED_NEW] ?: 0L)
        val previous = deserialize(p[Keys.DATA_OLD], p[Keys.PUBLISHED_OLD], p[Keys.FETCHED_OLD] ?: 0L)
        AppLogger.d("loadState: current=${current?.entries?.size ?: 0}, previous=${previous?.entries?.size ?: 0}")
        return PriceState(current = current, previous = previous)
    }

    private fun serialize(s: PriceSnapshot): String {
        val body = s.entries.values.joinToString("|") { e ->
            "${e.product.id}:${e.czk ?: -1}:${e.eur ?: -1}"
        }
        return body + "#" + (s.publishedAt ?: "")
    }

    private fun deserialize(data: String?, published: String?, fetched: Long): PriceSnapshot? {
        if (data.isNullOrBlank()) return null

        val main = data.substringBefore("#")
        val pub  = data.substringAfter("#", "").ifBlank { published ?: "" }

        // Pokud nemáme žádné položky, vrať null (např. poškozená data)
        if (main.isBlank()) return null

        val map = mutableMapOf<Product, PriceEntry>()
        for (part in main.split("|")) {
            val f = part.split(":")
            if (f.size != 3) continue
            val prod = Product.entries.find { it.id == f[0] } ?: continue
            val czk = f[1].toIntOrNull()?.takeIf { it >= 0 }
            val eur = f[2].toIntOrNull()?.takeIf { it >= 0 }
            map[prod] = PriceEntry(prod, czk, eur)
        }

        if (map.isEmpty()) return null

        return PriceSnapshot(
            entries = map,
            publishedAt = pub.ifBlank { null },
            fetchedAt = fetched
        )
    }
}