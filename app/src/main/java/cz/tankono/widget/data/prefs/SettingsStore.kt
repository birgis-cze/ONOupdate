package cz.tankono.widget.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import cz.tankono.widget.data.model.Currency
import cz.tankono.widget.data.model.Product
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "tankono_settings")

data class WidgetSettings(
    val visibleProducts: Set<Product> = Product.entries.toSet(),
    val currency: Currency = Currency.CZK,
    val useShortNames: Boolean = false,
    val peakStartMinutes: Int = 14 * 60 + 30,
    val peakEndMinutes: Int = 16 * 60,
    val intervalPeakMin: Int = 15,
    val intervalOffPeakMin: Int = 60,
    val fontSizeSp: Int = 15,
    val preferredNavigation: NavigationApp = NavigationApp.SYSTEM,
    val showNearestPump: Boolean = false
) {
    val effectiveIntervalMin: Int
        get() = minOf(intervalPeakMin, intervalOffPeakMin).coerceAtLeast(15)
}

class SettingsStore(private val context: Context) {

    private object Keys {
        val VISIBLE_PRODUCTS  = stringSetPreferencesKey("visible_products")
        val CURRENCY          = stringPreferencesKey("currency")
        val USE_SHORT_NAMES   = booleanPreferencesKey("use_short_names")
        val PEAK_START        = intPreferencesKey("peak_start")
        val PEAK_END          = intPreferencesKey("peak_end")
        val INTERVAL_PEAK     = intPreferencesKey("interval_peak")
        val INTERVAL_OFF      = intPreferencesKey("interval_off")
        val FONT_SIZE         = intPreferencesKey("font_size")
        val LAST_PUMP_SYNC    = longPreferencesKey("last_pump_sync")
        val PREFERRED_NAV     = stringPreferencesKey("preferred_navigation")
        val SHOW_NEAREST_PUMP = booleanPreferencesKey("show_nearest_pump")
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
            useShortNames     = p[Keys.USE_SHORT_NAMES] ?: false,
            peakStartMinutes  = p[Keys.PEAK_START]      ?: (14 * 60 + 30),
            peakEndMinutes    = p[Keys.PEAK_END]        ?: (16 * 60),
            intervalPeakMin   = p[Keys.INTERVAL_PEAK]   ?: 15,
            intervalOffPeakMin= p[Keys.INTERVAL_OFF]    ?: 60,
            fontSizeSp        = p[Keys.FONT_SIZE]       ?: 15,
            preferredNavigation = NavigationApp.fromId(p[Keys.PREFERRED_NAV]),
            showNearestPump   = p[Keys.SHOW_NEAREST_PUMP] ?: false
        )
    }

    suspend fun save(s: WidgetSettings) {
        context.settingsDataStore.edit { p ->
            p[Keys.VISIBLE_PRODUCTS] = s.visibleProducts.map { it.id }.toSet()
            p[Keys.CURRENCY]         = s.currency.name
            p[Keys.USE_SHORT_NAMES]  = s.useShortNames
            p[Keys.PEAK_START]       = s.peakStartMinutes
            p[Keys.PEAK_END]         = s.peakEndMinutes
            p[Keys.INTERVAL_PEAK]    = s.intervalPeakMin
            p[Keys.INTERVAL_OFF]     = s.intervalOffPeakMin
            p[Keys.FONT_SIZE]        = s.fontSizeSp
            p[Keys.PREFERRED_NAV]    = s.preferredNavigation.id
            p[Keys.SHOW_NEAREST_PUMP]= s.showNearestPump
        }
    }

    suspend fun saveLastPumpSync(time: Long) {
        context.settingsDataStore.edit { it[Keys.LAST_PUMP_SYNC] = time }
    }

    suspend fun getLastPumpSync(): Long {
        return context.settingsDataStore.data.map { it[Keys.LAST_PUMP_SYNC] ?: 0L }.first()
    }
}