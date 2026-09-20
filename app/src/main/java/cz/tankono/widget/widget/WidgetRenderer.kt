package cz.tankono.widget.widget

import android.Manifest
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Typeface
import android.os.Build
import android.text.SpannableString
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.SuperscriptSpan
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import cz.tankono.widget.R
import cz.tankono.widget.data.db.PumpEntity
import cz.tankono.widget.data.model.Currency
import cz.tankono.widget.data.model.PriceFormatter
import cz.tankono.widget.data.model.PriceState
import cz.tankono.widget.data.model.Product
import cz.tankono.widget.data.prefs.NavigationApp
import cz.tankono.widget.data.prefs.SettingsStore
import cz.tankono.widget.data.prefs.WidgetSettings
import cz.tankono.widget.data.prefs.CachedNearestPump
import cz.tankono.widget.data.remote.TankOnoScraper
import cz.tankono.widget.data.repo.PriceRepository
import cz.tankono.widget.data.repo.PumpRepository
import cz.tankono.widget.util.AppLogger
import cz.tankono.widget.util.LocationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object WidgetRenderer {

    private const val COLOR_LIGHT_BG = 0xFFFFD600.toInt()
    private const val COLOR_LIGHT_FG = 0xFFC92200.toInt()
    private const val COLOR_DARK_BG  = 0xFFC92200.toInt()
    private const val COLOR_DARK_FG  = 0xFFFFD600.toInt()
    private const val HEADER_FG      = COLOR_LIGHT_FG

    private const val REQUEST_CODE_REFRESH = 0
    private const val REQUEST_CODE_NAV     = 1

    private val ROW_IDS = intArrayOf(
        R.id.row_0, R.id.row_1, R.id.row_2, R.id.row_3, R.id.row_4,
        R.id.row_5, R.id.row_6, R.id.row_7, R.id.row_8, R.id.row_9, R.id.row_10
    )

    private val NAME_IDS = intArrayOf(
        R.id.row_0_name, R.id.row_1_name, R.id.row_2_name, R.id.row_3_name, R.id.row_4_name,
        R.id.row_5_name, R.id.row_6_name, R.id.row_7_name, R.id.row_8_name, R.id.row_9_name, R.id.row_10_name
    )
    private val OLD_IDS = intArrayOf(
        R.id.row_0_old, R.id.row_1_old, R.id.row_2_old, R.id.row_3_old, R.id.row_4_old,
        R.id.row_5_old, R.id.row_6_old, R.id.row_7_old, R.id.row_8_old, R.id.row_9_old, R.id.row_10_old
    )
    private val PRICE_IDS = intArrayOf(
        R.id.row_0_price, R.id.row_1_price, R.id.row_2_price, R.id.row_3_price, R.id.row_4_price,
        R.id.row_5_price, R.id.row_6_price, R.id.row_7_price, R.id.row_8_price, R.id.row_9_price, R.id.row_10_price
    )
    private val TREND_IDS = intArrayOf(
        R.id.row_0_trend, R.id.row_1_trend, R.id.row_2_trend, R.id.row_3_trend, R.id.row_4_trend,
        R.id.row_5_trend, R.id.row_6_trend, R.id.row_7_trend, R.id.row_8_trend, R.id.row_9_trend, R.id.row_10_trend
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun render(context: Context, mgr: AppWidgetManager, widgetId: Int) {
        AppLogger.d("=== RENDER widgetId=$widgetId ===")
        scope.launch {
            try {
                val settingsStore = SettingsStore(context)
                val settings = settingsStore.settings.first()
                val state = PriceRepository(context).loadState()

                // Nejbližší pumpa – s cache fallbackem
                val nearestPump: Pair<PumpEntity, Double>? =
                    if (settings.showNearestPump) {
                        withContext(Dispatchers.IO) {
                            resolveNearestPumpWithCache(context, settingsStore)
                        }
                    } else null

                val views = buildViews(context, settings, state, nearestPump)
                withContext(Dispatchers.Main) {
                    mgr.updateAppWidget(widgetId, views)
                }
                AppLogger.i("Widget $widgetId úspěšně vykreslen")
            } catch (t: Throwable) {
                AppLogger.e("Chyba při renderu widgetu $widgetId", t)
            }
        }
    }

    /**
     * Získá nejbližší pumpu s cache fallbackem.
     *
     * DŮLEŽITÉ: Nikdy nevrací null, pokud existuje cache.
     * Tím se zajistí, že widget po probuzení displeje
     * neztratí informaci o nejbližší stanici.
     */
    private suspend fun resolveNearestPumpWithCache(
        context: Context,
        settingsStore: SettingsStore
    ): Pair<PumpEntity, Double>? {
        // 1. Zkus aktuální polohu
        val fresh = findNearestPumpForWidget(context)
        if (fresh != null) {
            val (pump, dist) = fresh
            if (pump.lat != null && pump.lng != null) {
                settingsStore.saveCachedNearestPump(
                    CachedNearestPump(
                        name = pump.name,
                        distanceKm = dist,
                        lat = pump.lat,
                        lng = pump.lng
                    )
                )
                AppLogger.i("Widget: FRESH → ${pump.name}, ${"%.1f".format(dist)} km (cache uložena)")
            }
            return fresh
        }

        // 2. Fallback na cache – VŽDY použij, když existuje
        val cached = settingsStore.getCachedNearestPump()
        if (cached != null) {
            AppLogger.i("Widget: CACHE → ${cached.name}, ${"%.1f".format(cached.distanceKm)} km (fresh selhal)")
            val pump = PumpEntity(
                id = -1,
                name = cached.name,
                detailUrl = "",
                lat = cached.lat,
                lng = cached.lng,
                lastUpdated = 0L
            )
            return pump to cached.distanceKm
        }

        AppLogger.w("Widget: ani fresh, ani cache → vracím null")
        return null
    }

    /**
     * Zjistí aktuální polohu uživatele a vrátí nejbližší pumpu + vzdálenost v km.
     * Vrací null při chybě / chybějícím oprávnění.
     */
    private suspend fun findNearestPumpForWidget(
        context: Context
    ): Pair<PumpEntity, Double>? {
        return try {
            // Kontrola oprávnění
            val fine = ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            val coarse = ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            if (!fine && !coarse) {
                AppLogger.w("Widget: chybí oprávnění k poloze")
                return null
            }

            // Získat polohu
            val loc = LocationProvider.getCurrentLocation(context)
            if (loc == null) {
                AppLogger.w("Widget: polohu nelze získat")
                return null
            }

            // Najít nejbližší pumpu
            val pumps = PumpRepository(context)
                .getAllSortedByDistance(loc.first, loc.second)

            pumps.firstOrNull()
        } catch (t: Throwable) {
            AppLogger.e("Widget: chyba při hledání nejbližší pumpy", t)
            null
        }
    }

    private fun buildViews(
        context: Context,
        settings: WidgetSettings,
        state: PriceState,
        nearestPump: Pair<PumpEntity, Double>?
    ): RemoteViews {
        val night = isNight(context)
        val bg = if (night) COLOR_DARK_BG else COLOR_LIGHT_BG
        val fg = if (night) COLOR_DARK_FG else COLOR_LIGHT_FG

        val views = RemoteViews(context.packageName, R.layout.widget_tankono)
        views.setInt(R.id.widget_root, "setBackgroundColor", bg)

        val pi = buildRefreshPendingIntent(context)
        views.setOnClickPendingIntent(R.id.widget_root, pi)

        // ---------- DYNAMICKÉ ŠÍŘKY SLOUPCŮ (API 31+) ----------
        applyDynamicColumnWidths(context, views, settings)

        // ---------- ROZHODNUTÍ: zobrazit trend? ----------
        val showTrend = TankOnoScraper.isPublishedToday(state.current?.publishedAt)
        AppLogger.d("WidgetRenderer: showTrend=$showTrend (publishedAt=${state.current?.publishedAt})")

        // ---------- HLAVIČKA ----------
        val publishedFormatted = TankOnoScraper.formatPublished(state.current?.publishedAt) ?: "--"
        val fetchedTime = state.current?.fetchedAt?.let { ts ->
            if (ts > 0L) SimpleDateFormat("H:mm", Locale("cs", "CZ")).format(Date(ts))
            else "--"
        } ?: "--"

        val headerFontSize = (settings.fontSizeSp - 4).coerceAtLeast(8).toFloat()

        views.setTextViewText(R.id.header_date_web, publishedFormatted)
        views.setTextColor(R.id.header_date_web, HEADER_FG)
        views.setFloat(R.id.header_date_web, "setTextSize", headerFontSize)

        views.setTextViewText(R.id.header_date_update, "($fetchedTime)")
        views.setTextColor(R.id.header_date_update, HEADER_FG)
        views.setFloat(R.id.header_date_update, "setTextSize", headerFontSize)

        // ---------- PRODUKTY ----------
        val visible = Product.entries.filter { it in settings.visibleProducts }
        val groups = listOf(
            Product.Kind.FUEL,
            Product.Kind.OTHER,
            Product.Kind.EXCHANGE
        ).map { kind -> visible.filter { it.kind == kind } }
            .filter { it.isNotEmpty() }

        val flat = mutableListOf<Product>()
        for (g in groups) flat.addAll(g)

        ROW_IDS.forEachIndexed { index, rowId ->
            if (index < flat.size) {
                val product = flat[index]
                views.setViewVisibility(rowId, View.VISIBLE)

                val cur = state.current?.entries?.get(product)
                val old = state.previous?.entries?.get(product)

                val name = if (settings.useShortNames) product.shortName else product.displayName
                views.setViewVisibility(NAME_IDS[index], View.VISIBLE)
                views.setTextViewText(NAME_IDS[index], name)
                views.setTextColor(NAME_IDS[index], fg)
                views.setFloat(NAME_IDS[index], "setTextSize", settings.fontSizeSp.toFloat())

                views.setViewVisibility(OLD_IDS[index], View.VISIBLE)
                val oldText = if (old != null)
                    "(${PriceFormatter.format(old, settings.currency)})"
                else
                    "( --,-- )"
                views.setTextViewText(OLD_IDS[index], oldText)
                views.setTextColor(OLD_IDS[index], fg)
                views.setFloat(OLD_IDS[index], "setTextSize", (settings.fontSizeSp - 2).toFloat())

                views.setViewVisibility(PRICE_IDS[index], View.VISIBLE)
                val priceSpannable = buildPriceSpannable(cur, product, settings)
                views.setTextViewText(PRICE_IDS[index], priceSpannable)
                views.setTextColor(PRICE_IDS[index], fg)
                views.setFloat(PRICE_IDS[index], "setTextSize", settings.fontSizeSp.toFloat())

                views.setViewVisibility(TREND_IDS[index], View.VISIBLE)
                val arrow = if (showTrend && old != null && cur != null) {
                    val oldVal = PriceFormatter.valueFor(old, settings.currency)
                    val curVal = PriceFormatter.valueFor(cur, settings.currency)
                    when {
                        oldVal == null || curVal == null -> ""
                        curVal > oldVal -> "▲"
                        curVal < oldVal -> "▼"
                        else -> "="
                    }
                } else ""
                views.setTextViewText(TREND_IDS[index], arrow)
                views.setTextColor(TREND_IDS[index], fg)
                views.setFloat(TREND_IDS[index], "setTextSize", settings.fontSizeSp.toFloat())
            } else {
                views.setViewVisibility(rowId, View.GONE)
            }
        }

        // ---------- NEJBLIŽŠÍ STANICE ----------
        renderNearestPump(context, views, settings, nearestPump)

        return views
    }

    /**
     * Vykreslí spodní řádek s nejbližší stanicí.
     *
     * Pokud je přepínač zapnutý, blok je VŽDY VISIBLE (nikdy GONE),
     * aby po probuzení displeje zůstal viditelný.
     * Když nejsou data, zobrazí "Zjišťuji polohu…".
     */
    private fun renderNearestPump(
        context: Context,
        views: RemoteViews,
        settings: WidgetSettings,
        nearestPump: Pair<PumpEntity, Double>?
    ) {
        // Přepínač vypnutý → schovat (správně)
        if (!settings.showNearestPump) {
            views.setViewVisibility(R.id.nearest_container, View.GONE)
            return
        }

        // Přepínač zapnutý → VŽDY zobrazit
        views.setViewVisibility(R.id.nearest_container, View.VISIBLE)

        val night = isNight(context)
        val fg = if (night) COLOR_DARK_FG else COLOR_LIGHT_FG

        // Když opravdu nic neznáme (první render, cache prázdná)
        if (nearestPump == null) {
            views.setImageViewResource(R.id.nearest_icon, R.drawable.ic_nav_system)
            views.setInt(R.id.nearest_icon, "setColorFilter", fg)

            views.setTextViewText(R.id.nearest_distance, "")
            views.setTextColor(R.id.nearest_distance, fg)
            views.setFloat(R.id.nearest_distance, "setTextSize", settings.fontSizeSp.toFloat())

            views.setTextViewText(R.id.nearest_text, "Zjišťuji polohu…")
            views.setTextColor(R.id.nearest_text, fg)
            views.setFloat(R.id.nearest_text, "setTextSize", settings.fontSizeSp.toFloat())

            return
        }

        // Normální render s daty
        val (pump, distanceKm) = nearestPump

        // Vzdálenost – tučně
        val distanceText = formatDistance(distanceKm)
        views.setTextViewText(R.id.nearest_distance, distanceText)
        views.setTextColor(R.id.nearest_distance, fg)
        views.setFloat(R.id.nearest_distance, "setTextSize", settings.fontSizeSp.toFloat())

        // Adresa – normálně
        val name = pump.name.removePrefix("ČS ").trim()
        views.setTextViewText(R.id.nearest_text, name)
        views.setTextColor(R.id.nearest_text, fg)
        views.setFloat(R.id.nearest_text, "setTextSize", settings.fontSizeSp.toFloat())

        // Ikonka vybrané navigace
        val iconRes = settings.preferredNavigation.iconRes
        views.setImageViewResource(R.id.nearest_icon, iconRes)
        views.setInt(R.id.nearest_icon, "setColorFilter", fg)

        // Klik → navigace
        val pi = buildNavigationPendingIntent(context, pump, settings.preferredNavigation)
        views.setOnClickPendingIntent(R.id.nearest_container, pi)
        views.setOnClickPendingIntent(R.id.nearest_icon, pi)
        views.setOnClickPendingIntent(R.id.nearest_distance, pi)
        views.setOnClickPendingIntent(R.id.nearest_text, pi)
    }

    /**
     * "33km" pro >= 10 km, "3,5km" pro < 10 km.
     */
    private fun formatDistance(km: Double): String {
        return if (km < 10.0) {
            "%.1fkm".format(km).replace('.', ',')
        } else {
            "%.0fkm".format(km)
        }
    }

    /**
     * PendingIntent pro klik na nejbližší stanici.
     */
    private fun buildNavigationPendingIntent(
        context: Context,
        pump: PumpEntity,
        navApp: NavigationApp
    ): PendingIntent {
        val intent = Intent(context, TankOnoWidget::class.java).apply {
            action = TankOnoWidget.ACTION_NAVIGATE
            putExtra(TankOnoWidget.EXTRA_LAT, pump.lat ?: 0.0)
            putExtra(TankOnoWidget.EXTRA_LNG, pump.lng ?: 0.0)
            putExtra(TankOnoWidget.EXTRA_LABEL, pump.name)
            putExtra(TankOnoWidget.EXTRA_NAV_APP, navApp.id)
        }

        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_NAV,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * Dynamicky nastaví šířky sloupců podle fontSizeSp.
     * Funguje pouze na API 31+ (setViewLayoutWidth).
     */
    private fun applyDynamicColumnWidths(
        context: Context,
        views: RemoteViews,
        settings: WidgetSettings
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return
        }

        val density = context.resources.displayMetrics.density
        val fontSize = settings.fontSizeSp.toFloat()

        val trendWidthDp = (fontSize * 1.5f).coerceIn(22f, 40f)
        val trendWidthPx = trendWidthDp * density

        val oldFontSize = (fontSize - 2).coerceAtLeast(8f)
        val oldWidthDp = (oldFontSize * 3.5f).coerceIn(55f, 90f)
        val oldWidthPx = oldWidthDp * density

        val priceWidthDp = (fontSize * 3.2f).coerceIn(50f, 85f)
        val priceWidthPx = priceWidthDp * density

        TREND_IDS.forEach { id ->
            views.setViewLayoutWidth(id, trendWidthPx, TypedValue.COMPLEX_UNIT_PX)
        }
        OLD_IDS.forEach { id ->
            views.setViewLayoutWidth(id, oldWidthPx, TypedValue.COMPLEX_UNIT_PX)
        }
        PRICE_IDS.forEach { id ->
            views.setViewLayoutWidth(id, priceWidthPx, TypedValue.COMPLEX_UNIT_PX)
        }
    }

    private fun buildPriceSpannable(
        entry: cz.tankono.widget.data.model.PriceEntry?,
        product: Product,
        settings: WidgetSettings
    ): CharSequence {
        if (entry == null) return "--"
        val value = PriceFormatter.valueFor(entry, settings.currency) ?: return "--"

        val isCzk = (product.kind == Product.Kind.EXCHANGE || settings.currency == Currency.CZK)
        val whole: String
        val frac: String
        if (isCzk) {
            whole = (value / 100).toString()
            frac = "%02d".format(value % 100)
        } else {
            whole = (value / 1000).toString()
            frac = "%03d".format(value % 1000)
        }

        val full = whole + frac
        val sp = SpannableString(full)
        val start = whole.length
        val end = full.length

        sp.setSpan(StyleSpan(Typeface.BOLD), 0, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        sp.setSpan(SuperscriptSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        sp.setSpan(RelativeSizeSpan(0.7f), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        return sp
    }

    private fun buildRefreshPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, TankOnoWidget::class.java).apply {
            action = TankOnoWidget.ACTION_REFRESH
        }
        return PendingIntent.getBroadcast(
            context, REQUEST_CODE_REFRESH, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun isNight(context: Context): Boolean {
        return (context.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
    }
}