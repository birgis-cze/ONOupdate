package cz.tankono.widget.data

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import cz.tankono.widget.data.repo.PriceRepository
import cz.tankono.widget.util.AppLogger
import kotlinx.coroutines.runBlocking

/**
 * ContentProvider pro sdílení cen s jinými aplikacemi.
 * 
 * URI: content://cz.tankono.widget.provider/prices
 * 
 * Sloupce:
 * - product_id (String) - ID produktu (např. "natural_95")
 * - product_name (String) - Název produktu (např. "Natural 95")
 * - price_czk (Int) - Cena v Kč v haléřích (např. 3690 = 36,90 Kč)
 * - price_eur (Int) - Cena v EUR v tisícinách (např. 1763 = 1,763 €)
 * - published_at (String) - Datum zveřejnění ceníku (např. "23.11.2026 (15:28:00)")
 * - fetched_at (Long) - Čas stažení (System.currentTimeMillis())
 */
class PriceProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = "cz.tankono.widget.provider"
        const val PATH_PRICES = "prices"
        val CONTENT_URI: Uri = Uri.parse("content://$AUTHORITY/$PATH_PRICES")
    }

    override fun onCreate(): Boolean {
        AppLogger.i("PriceProvider: onCreate")
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        AppLogger.d("PriceProvider: query($uri)")
        
        val context = context ?: return null
        
        // Ověříme, že jde o náš URI
        if (uri != CONTENT_URI) {
            AppLogger.w("PriceProvider: neznámé URI $uri")
            return null
        }

        // Načteme data z DataStore (blokující volání v rámci ContentProvideru)
        val state = runBlocking {
            try {
                PriceRepository(context).loadState()
            } catch (t: Throwable) {
                AppLogger.e("PriceProvider: chyba při načítání dat", t)
                null
            }
        }

        val cursor = MatrixCursor(arrayOf(
            "product_id",
            "product_name",
            "price_czk",
            "price_eur",
            "published_at",
            "fetched_at"
        ))

        val current = state?.current ?: return cursor
        
        for ((product, entry) in current.entries) {
            cursor.addRow(arrayOf(
                product.id,
                product.displayName,
                entry.czk ?: -1,
                entry.eur ?: -1,
                current.publishedAt ?: "",
                current.fetchedAt
            ))
        }

        AppLogger.d("PriceProvider: vráceno ${cursor.count} řádků")
        return cursor
    }

    override fun getType(uri: Uri): String {
        return "vnd.android.cursor.dir/vnd.$AUTHORITY.$PATH_PRICES"
    }

    // Nepodporujeme zápis – jen čtení
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}