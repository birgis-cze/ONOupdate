package cz.tankono.widget.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import cz.tankono.widget.data.model.Currency
import cz.tankono.widget.data.model.Product
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "tankono_settings")

/** Uživatelské nastavení widgetu. */
data class WidgetSettings(
    val visibleProducts: Set<Product> = Product.entries.toSet(),
    val currency: Currency = Currency.CZK,
    val peakStartMinutes: Int = 14 * 60 + 30,  // 14:30
    val peakEndMinutes: Int = 16 * 60,          // 16:00
    val intervalPeakMin: Int = 10,
    val intervalOffPeakMin: Int = 60,
    val fontSizeSp: Int = 15
) {
    /** Efektivní interval – Android minimum je 15 minut. */
    val effectiveIntervalMin: Int
        get() = minOf(intervalPeakMin, intervalOffPeakMin).coerceAtLeast(15)
}

class SettingsStore(private val context: Context) {

    private object Keys {
        val VISIBLE_PRODUCTS = stringSetPreferencesKey("visible_products")
        val CURRENCY         = stringPreferencesKey("currency")
        val PEAK_START       = intPreferencesKey("peak_start")
        val PEAK_END         = intPreferencesKey("peak_end")
        val INTERVAL_PEAK    = intPreferencesKey("interval_peak")
        val INTERVAL_OFF     = intPreferencesKey("interval_off")
        val FONT_SIZE        = intPreferencesKey("font_size")
    }

    val settings: Flow<WidgetSettings> = context.settingsDataStore.data.map { p ->
        WidgetSettings(
            visibleProducts = p[Keys.VISIBLE_PRODUCTS]
                ?.mapNotNull { id -> Product.entries.find { it.id == id } }
                ?.toSet()
                ?: Product.entries.toSet(),
            currency = p[Keys.CURRENCY]?.let { name ->
                runCatching { Currency.valueOf(name) }.getOrDefault(Currency.CZK)
            } ?: Currency.CZK,
            peakStartMinutes  = p[Keys.PEAK_START]    ?: (14 * 60 + 30),
            peakEndMinutes    = p[Keys.PEAK_END]      ?: (16 * 60),
            intervalPeakMin   = p[Keys.INTERVAL_PEAK] ?: 10,
            intervalOffPeakMin= p[Keys.INTERVAL_OFF]  ?: 60,
            fontSizeSp        = p[Keys.FONT_SIZE]     ?: 15
        )
    }

    suspend fun save(s: WidgetSettings) {
        context.settingsDataStore.edit { p ->
            p[Keys.VISIBLE_PRODUCTS] = s.visibleProducts.map { it.id }.toSet()
            p[Keys.CURRENCY]         = s.currency.name
            p[Keys.PEAK_START]       = s.peakStartMinutes
            p[Keys.PEAK_END]         = s.peakEndMinutes
            p[Keys.INTERVAL_PEAK]    = s.intervalPeakMin
            p[Keys.INTERVAL_OFF]     = s.intervalOffPeakMin
            p[Keys.FONT_SIZE]        = s.fontSizeSp
        }
    }
}