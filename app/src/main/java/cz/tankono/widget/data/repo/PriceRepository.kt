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
import kotlinx.coroutines.flow.first

private val Context.priceDataStore by preferencesDataStore(name = "tankono_prices")

/**
 * Ukládá dva snapshoty – aktuální ("new") a předchozí ("old").
 * Při každém úspěšném stažení nového ceníku se "new" posune do "old".
 */
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
     * Zkontroluje, zda je na stránce aktualit novější záznam než ten uložený.
     * Pokud ano, stáhne ceník, posune old ← new, uloží nový snapshot.
     * Vrací true, když se data změnila (nový ceník).
     */
    suspend fun refresh(): Boolean {
        val news = TankOnoScraper.fetchLatestNewsDate() ?: return false
        val stored = context.priceDataStore.data.first()[Keys.PUBLISHED_NEW]

        // Pokud máme stejné datum, nic se nezměnilo
        if (stored != null && news == stored) return false

        val snapshot = TankOnoScraper.fetchPrices() ?: return false

        context.priceDataStore.edit { p ->
            // Posun new → old
            val oldPublished = p[Keys.PUBLISHED_NEW]
            val oldFetched   = p[Keys.FETCHED_NEW]
            val oldData      = p[Keys.DATA_NEW]

            if (oldPublished != null) p[Keys.PUBLISHED_OLD] = oldPublished
            if (oldFetched   != null) p[Keys.FETCHED_OLD]   = oldFetched
            if (oldData      != null) p[Keys.DATA_OLD]      = oldData

            // Zapsat nová data
            p[Keys.PUBLISHED_NEW] = snapshot.publishedAt ?: news
            p[Keys.FETCHED_NEW]   = snapshot.fetchedAt
            p[Keys.DATA_NEW]      = serialize(snapshot)
        }
        return true
    }

    /**
     * Vynutí stažení ceníku bez ohledu na datum.
     * Používá se pro tlačítko "Aktualizovat data" v nastavení.
     * Vrací true, když se něco stáhlo.
     */
    suspend fun forceRefresh(): Boolean {
        val snapshot = TankOnoScraper.fetchPrices() ?: return false
        val stored = context.priceDataStore.data.first()[Keys.PUBLISHED_NEW]

        // Pokud datum zůstává stejné, jen aktualizujeme "fetched_new" čas
        if (stored != null && stored == snapshot.publishedAt) {
            context.priceDataStore.edit { p ->
                p[Keys.FETCHED_NEW] = snapshot.fetchedAt
            }
            return false
        }

        // Jinak posun old ← new a ulož nová data
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

    /** Načte aktuální stav z DataStore. */
    suspend fun loadState(): PriceState {
        val p = context.priceDataStore.data.first()
        return PriceState(
            current  = deserialize(p[Keys.DATA_NEW], p[Keys.PUBLISHED_NEW], p[Keys.FETCHED_NEW] ?: 0L),
            previous = deserialize(p[Keys.DATA_OLD], p[Keys.PUBLISHED_OLD], p[Keys.FETCHED_OLD]   ?: 0L)
        )
    }

    // ---------- Serializace ----------

    /**
     * Formát: "id:czk:eur|id:czk:eur|...#published"
     * Prázdná hodnota = null.
     */
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