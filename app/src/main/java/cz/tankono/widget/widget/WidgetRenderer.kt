package cz.tankono.widget.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.SuperscriptSpan
import android.view.View
import android.widget.RemoteViews
import cz.tankono.widget.R
import cz.tankono.widget.data.model.Currency
import cz.tankono.widget.data.model.PriceFormatter
import cz.tankono.widget.data.model.PriceState
import cz.tankono.widget.data.model.Product
import cz.tankono.widget.data.prefs.SettingsStore
import cz.tankono.widget.data.prefs.WidgetSettings
import cz.tankono.widget.data.remote.TankOnoScraper
import cz.tankono.widget.data.repo.PriceRepository
import cz.tankono.widget.util.AppLogger
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

    // Prahy šířky (v dp) pro skrytí sloupců
    // Priorita: Název > Aktuální cena > Stará cena > Trend
    private const val THRESHOLD_HIDE_TREND = 250   // skryje trend (nejnižší priorita)
    private const val THRESHOLD_HIDE_OLD   = 200   // skryje starou cenu
    private const val THRESHOLD_HIDE_PRICE = 100   // skryje aktuální cenu (extrém)

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
                val settings = SettingsStore(context).settings.first()
                val state = PriceRepository(context).loadState()
                val views = buildViews(context, mgr, widgetId, settings, state)
                withContext(Dispatchers.Main) {
                    mgr.updateAppWidget(widgetId, views)
                }
                AppLogger.i("Widget $widgetId úspěšně vykreslen")
            } catch (t: Throwable) {
                AppLogger.e("Chyba při renderu widgetu $widgetId", t)
            }
        }
    }

    private fun buildViews(
        context: Context,
        mgr: AppWidgetManager,
        widgetId: Int,
        settings: WidgetSettings,
        state: PriceState
    ): RemoteViews {
        // Zjistit šířku widgetu (v dp)
        val options: Bundle = mgr.getAppWidgetOptions(widgetId)
        val minWidthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        val maxWidthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)
        val widthDp = if (maxWidthDp > 0) maxWidthDp else minWidthDp

        // Podle šířky rozhodnout, které sloupce skrýt
        val hideTrend = widthDp < THRESHOLD_HIDE_TREND
        val hideOld   = widthDp < THRESHOLD_HIDE_OLD
        val hidePrice = widthDp < THRESHOLD_HIDE_PRICE
        // Název se NIKDY neskrývá

        AppLogger.d("Šířka widgetu: $widthDp dp (hideTrend=$hideTrend, hideOld=$hideOld, hidePrice=$hidePrice)")

        val night = isNight(context)
        val bg = if (night) COLOR_DARK_BG else COLOR_LIGHT_BG
        val fg = if (night) COLOR_DARK_FG else COLOR_LIGHT_FG

        val views = RemoteViews(context.packageName, R.layout.widget_tankono)

        views.setInt(R.id.widget_root, "setBackgroundColor", bg)

        val pi = buildRefreshPendingIntent(context)
        views.setOnClickPendingIntent(R.id.widget_root, pi)

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

                // Název – VŽDY viditelný (priorita 1)
                val name = if (settings.useShortNames) product.shortName else product.displayName
                views.setTextViewText(NAME_IDS[index], name)
                views.setTextColor(NAME_IDS[index], fg)
                views.setFloat(NAME_IDS[index], "setTextSize", settings.fontSizeSp.toFloat())

                // Stará cena – skrýt, pokud je widget úzký (priorita 3)
                if (hideOld) {
                    views.setViewVisibility(OLD_IDS[index], View.GONE)
                } else {
                    views.setViewVisibility(OLD_IDS[index], View.VISIBLE)
                    val oldText = if (old != null)
                        "(${PriceFormatter.format(old, settings.currency)})"
                    else
                        "( --,-- )"
                    views.setTextViewText(OLD_IDS[index], oldText)
                    views.setTextColor(OLD_IDS[index], fg)
                    views.setFloat(OLD_IDS[index], "setTextSize", (settings.fontSizeSp - 2).toFloat())
                }

                // Aktuální cena – skrýt jen v extrému (priorita 2)
                if (hidePrice) {
                    views.setViewVisibility(PRICE_IDS[index], View.GONE)
                } else {
                    views.setViewVisibility(PRICE_IDS[index], View.VISIBLE)
                    val priceSpannable = buildPriceSpannable(cur, product, settings)
                    views.setTextViewText(PRICE_IDS[index], priceSpannable)
                    views.setTextColor(PRICE_IDS[index], fg)
                    views.setFloat(PRICE_IDS[index], "setTextSize", settings.fontSizeSp.toFloat())
                }

                // Trend – skrýt jako první (priorita 4)
                if (hideTrend) {
                    views.setViewVisibility(TREND_IDS[index], View.GONE)
                } else {
                    views.setViewVisibility(TREND_IDS[index], View.VISIBLE)
                    val arrow = if (old != null && cur != null) {
                        val oldVal = PriceFormatter.valueFor(old, settings.currency)
                        val curVal = PriceFormatter.valueFor(cur, settings.currency)
                        when {
                            oldVal == null || curVal == null -> "="
                            curVal > oldVal -> "▲"
                            curVal < oldVal -> "▼"
                            else -> "="
                        }
                    } else "="
                    views.setTextViewText(TREND_IDS[index], arrow)
                    views.setTextColor(TREND_IDS[index], fg)
                    views.setFloat(TREND_IDS[index], "setTextSize", settings.fontSizeSp.toFloat())
                }
            } else {
                views.setViewVisibility(rowId, View.GONE)
            }
        }

        return views
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
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun isNight(context: Context): Boolean {
        return (context.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
    }
}