package cz.tankono.widget.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import cz.tankono.widget.R
import cz.tankono.widget.data.model.PriceFormatter
import cz.tankono.widget.data.model.PriceState
import cz.tankono.widget.data.model.Product
import cz.tankono.widget.data.prefs.SettingsStore
import cz.tankono.widget.data.prefs.WidgetSettings
import cz.tankono.widget.data.remote.TankOnoScraper
import cz.tankono.widget.data.repo.PriceRepository
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

    private const val TAG = "WidgetRenderer"

    private const val COLOR_LIGHT_BG = 0xFFFFD600.toInt()
    private const val COLOR_LIGHT_FG = 0xFFC92200.toInt()
    private const val COLOR_DARK_BG  = 0xFFC92200.toInt()
    private const val COLOR_DARK_FG  = 0xFFFFD600.toInt()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** ID řádků – musí odpovídat ID ve widget_tankono.xml */
    private val ROW_IDS = intArrayOf(
        R.id.row_0, R.id.row_1, R.id.row_2, R.id.row_3, R.id.row_4,
        R.id.row_5, R.id.row_6, R.id.row_7, R.id.row_8, R.id.row_9,
        R.id.row_10
    )

    /** ID oddělovačů – mezi skupinami */
    private val DIVIDER_IDS = intArrayOf(
        R.id.divider_0, R.id.divider_1, R.id.divider_2
    )

    fun render(context: Context, mgr: AppWidgetManager, widgetId: Int) {
        scope.launch {
            try {
                val settings = SettingsStore(context).settings.first()
                val state = PriceRepository(context).loadState()
                val views = buildViews(context, settings, state)
                withContext(Dispatchers.Main) {
                    mgr.updateAppWidget(widgetId, views)
                }
                Log.d(TAG, "Widget $widgetId vykreslen")
            } catch (t: Throwable) {
                Log.e(TAG, "Chyba při renderu widgetu $widgetId", t)
            }
        }
    }

    private fun buildViews(
        context: Context,
        settings: WidgetSettings,
        state: PriceState
    ): RemoteViews {
        val night = isNight(context)
        val bg = if (night) COLOR_DARK_BG else COLOR_LIGHT_BG
        val fg = if (night) COLOR_DARK_FG else COLOR_LIGHT_FG

        val views = RemoteViews(context.packageName, R.layout.widget_tankono)

        // Pozadí
        views.setInt(R.id.widget_root, "setBackgroundColor", bg)

        // Klik na widget = refresh
        val pi = buildRefreshPendingIntent(context)
        views.setOnClickPendingIntent(R.id.widget_root, pi)

        // Hlavička
        val published = TankOnoScraper.formatPublished(state.current?.publishedAt) ?: "--"
        val fetched = state.current?.fetchedAt?.let { ts ->
            if (ts > 0L) SimpleDateFormat("H:mm", Locale("cs", "CZ")).format(Date(ts))
            else "--"
        } ?: "--"
        views.setTextViewText(R.id.header_datetime, "$published ($fetched)")
        views.setTextColor(R.id.header_datetime, fg)

        // Viditelné produkty v pevném pořadí
        val visible = Product.entries.filter { it in settings.visibleProducts }

        // Rozdělit do skupin a vytvořit jeden "plochý" seznam
        // (řádek po řádku, kde se mezi skupiny vkládá oddělovač)
        data class RowSlot(
            val isDivider: Boolean,
            val product: Product?
        )

        val slots = mutableListOf<RowSlot>()
        val groups = listOf(
            Product.Kind.FUEL,
            Product.Kind.OTHER,
            Product.Kind.EXCHANGE
        ).map { kind -> visible.filter { it.kind == kind } }
            .filter { it.isNotEmpty() }

        groups.forEachIndexed { index, group ->
            if (index > 0) {
                slots.add(RowSlot(isDivider = true, product = null))
            }
            group.forEach { p ->
                slots.add(RowSlot(isDivider = false, product = p))
            }
        }

        // Maximálně 11 řádků + 3 oddělovače (co XML obsahuje)
        val maxRows = ROW_IDS.size
        val maxDividers = DIVIDER_IDS.size

        var rowIndex = 0
        var dividerIndex = 0

        for (slot in slots) {
            if (slot.isDivider) {
                if (dividerIndex < maxDividers) {
                    views.setViewVisibility(DIVIDER_IDS[dividerIndex], View.VISIBLE)
                    dividerIndex++
                }
            } else {
                if (rowIndex < maxRows) {
                    val product = slot.product!!
                    val rowId = ROW_IDS[rowIndex]

                    views.setViewVisibility(rowId, View.VISIBLE)

                    val cur = state.current?.entries?.get(product)
                    val old = state.previous?.entries?.get(product)

                    // Název
                    val nameId = getNameId(rowIndex)
                    views.setTextViewText(nameId, product.displayName)
                    views.setTextColor(nameId, fg)
                    views.setFloat(nameId, "setTextSize", settings.fontSizeSp.toFloat())

                    // Stará cena
                    val oldId = getOldId(rowIndex)
                    val oldText = if (old != null)
                        "(${PriceFormatter.format(old, settings.currency)})"
                    else ""
                    views.setTextViewText(oldId, oldText)
                    views.setTextColor(oldId, fg)
                    views.setFloat(oldId, "setTextSize", (settings.fontSizeSp - 1).toFloat())

                    // Aktuální cena
                    val priceId = getPriceId(rowIndex)
                    views.setTextViewText(
                        priceId,
                        PriceFormatter.format(cur, settings.currency)
                    )
                    views.setTextColor(priceId, fg)
                    views.setFloat(priceId, "setTextSize", settings.fontSizeSp.toFloat())

                    // Trend
                    val trendId = getTrendId(rowIndex)
                    val arrow = if (old != null && cur != null) {
                        val oldVal = PriceFormatter.valueFor(old, settings.currency)
                        val curVal = PriceFormatter.valueFor(cur, settings.currency)
                        when {
                            oldVal == null || curVal == null -> ""
                            curVal > oldVal -> "▲"
                            curVal < oldVal -> "▼"
                            else -> "="
                        }
                    } else ""
                    views.setTextViewText(trendId, arrow)
                    views.setTextColor(trendId, fg)
                    views.setFloat(trendId, "setTextSize", settings.fontSizeSp.toFloat())

                    rowIndex++
                }
            }
        }

        // Skrýt zbytek řádků
        for (i in rowIndex until maxRows) {
            views.setViewVisibility(ROW_IDS[i], View.GONE)
        }
        // Skrýt zbytek oddělovačů
        for (i in dividerIndex until maxDividers) {
            views.setViewVisibility(DIVIDER_IDS[i], View.GONE)
        }

        return views
    }

    // ---- Pomocné funkce pro ID dětí jednotlivých řádků ----
    private fun getNameId(i: Int): Int = when (i) {
        0 -> R.id.row_name_0; 1 -> R.id.row_name_1; 2 -> R.id.row_name_2
        3 -> R.id.row_name_3; 4 -> R.id.row_name_4; 5 -> R.id.row_name_5
        6 -> R.id.row_name_6; 7 -> R.id.row_name_7; 8 -> R.id.row_name_8
        9 -> R.id.row_name_9; 10 -> R.id.row_name_10
        else -> 0
    }
    private fun getOldId(i: Int): Int = when (i) {
        0 -> R.id.row_old_0; 1 -> R.id.row_old_1; 2 -> R.id.row_old_2
        3 -> R.id.row_old_3; 4 -> R.id.row_old_4; 5 -> R.id.row_old_5
        6 -> R.id.row_old_6; 7 -> R.id.row_old_7; 8 -> R.id.row_old_8
        9 -> R.id.row_old_9; 10 -> R.id.row_old_10
        else -> 0
    }
    private fun getPriceId(i: Int): Int = when (i) {
        0 -> R.id.row_price_0; 1 -> R.id.row_price_1; 2 -> R.id.row_price_2
        3 -> R.id.row_price_3; 4 -> R.id.row_price_4; 5 -> R.id.row_price_5
        6 -> R.id.row_price_6; 7 -> R.id.row_price_7; 8 -> R.id.row_price_8
        9 -> R.id.row_price_9; 10 -> R.id.row_price_10
        else -> 0
    }
    private fun getTrendId(i: Int): Int = when (i) {
        0 -> R.id.row_trend_0; 1 -> R.id.row_trend_1; 2 -> R.id.row_trend_2
        3 -> R.id.row_trend_3; 4 -> R.id.row_trend_4; 5 -> R.id.row_trend_5
        6 -> R.id.row_trend_6; 7 -> R.id.row_trend_7; 8 -> R.id.row_trend_8
        9 -> R.id.row_trend_9; 10 -> R.id.row_trend_10
        else -> 0
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