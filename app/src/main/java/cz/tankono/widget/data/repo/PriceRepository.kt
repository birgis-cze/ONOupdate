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

    suspend fun refresh(): Boolean {
        AppLogger.d("--- refresh() START ---")

        AppLogger.d("Stahuji datum z aktualit…")
        val news = TankOnoScraper.fetchLatestNewsDate()
        AppLogger.d("Datum z aktualit: $news")

        if (news == null) {
            AppLogger.w("Nepodařilo se získat datum z aktualit")
            return false
        }

        val stored = context.priceDataStore.data.first()[Keys.PUBLISHED_NEW]
        AppLogger.d("Uložené datum: $stored")

        if (stored != null && news == stored) {
            AppLogger.d("Datum je stejné – ceník se nezměnil")
            return false
        }

        AppLogger.i("Nový ceník! Stahuji…")
        val snapshot = TankOnoScraper.fetchPrices()
        if (snapshot == null) {
            AppLogger.w("Nepodařilo se stáhnout ceník")
            return false
        }
        AppLogger.d("Ceník stažen: ${snapshot.entries.size} položek")

        context.priceDataStore.edit { p ->
            val oldPublished = p[Keys.PUBLISHED_NEW]
            val oldFetched   = p[Keys.FETCHED_NEW]
            val oldData      = p[Keys.DATA_NEW]

            if (oldPublished != null) p[Keys.PUBLISHED_OLD] = oldPublished
            if (oldFetched   != null) p[Keys.FETCHED_OLD]   = oldFetched
            if (oldData      != null) p[Keys.DATA_OLD]      = oldData

            p[Keys.PUBLISHED_NEW] = snapshot.publishedAt ?: news
            p[Keys.FETCHED_NEW]   = snapshot.fetchedAt
            p[Keys.DATA_NEW]      = serialize(snapshot)
        }
        AppLogger.i("Ceník uložen do DataStore")
        return true
    }

    /**
     * Vždy zkusí stáhnout ceník. Pokud se datum nezměnilo, jen aktualizuje čas fetchedAt.
     */
    suspend fun forceRefresh(): Boolean {
        AppLogger.d("--- forceRefresh() START ---")

        val snapshot = TankOnoScraper.fetchPrices()
        if (snapshot == null) {
            AppLogger.w("forceRefresh: nepodařilo se stáhnout ceník")
            return false
        }

        val stored = context.priceDataStore.data.first()[Keys.PUBLISHED_NEW]

        // Stejné datum – jen aktualizuj čas fetchedAt
        if (stored != null && stored == snapshot.publishedAt) {
            AppLogger.d("forceRefresh: datum stejné, aktualizuji jen fetchedAt")
            context.priceDataStore.edit { p ->
                p[Keys.FETCHED_NEW] = snapshot.fetchedAt
            }
            return false
        }

        // Nové datum – posun old ← new
        AppLogger.i("forceRefresh: nová data, posouvám old ← new")
        context.priceDataStore.edit { p ->
            val oldPublished = p[Keys.PUBLISHED_NEW]
            val oldFetched   = p[Keys.FETCHED_NEW]
            val oldData      = p[Keys.DATA_NEW]

            if (oldPublished != null) p[Keys.PUBLISHED_OLD] = oldPublished
            if (oldFetched   != null) p[Keys.FETCHED_OLD]   = oldFetched
            if (oldData      != null) p[Keys.DATA_OLD]      = oldData

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

        val map = mutableMapOf<Product, PriceEntry>()
        for (part in main.split("|")) {
            val f = part.split(":")
            if (f.size != 3) continue
            val prod = Product.entries.find { it.id == f[0] } ?: continue
            val czk = f[1].toIntOrNull()?.takeIf { it >= 0 }
            val eur = f[2].toIntOrNull()?.takeIf { it >= 0 }
            map[prod] = PriceEntry(prod, czk, eur)
        }

        return PriceSnapshot(
            entries = map,
            publishedAt = pub.ifBlank { null },
            fetchedAt = fetched
        )
    }
}