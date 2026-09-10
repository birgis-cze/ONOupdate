package cz.tankono.widget.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.util.Log
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
                // Fallback: alespoň prázdný widget s hlavičkou
                try {
                    val fallback = RemoteViews(context.packageName, R.layout.widget_tankono)
                    fallback.setTextViewText(R.id.header_datetime, "--")
                    mgr.updateAppWidget(widgetId, fallback)
                } catch (t2: Throwable) {
                    Log.e(TAG, "I fallback selhal", t2)
                }
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

        // Pozadí widgetu
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

        // Řádky
        views.removeAllViews(R.id.rows_container)

        val visible = Product.entries.filter { it in settings.visibleProducts }
        val groups = listOf(
            Product.Kind.FUEL,
            Product.Kind.OTHER,
            Product.Kind.EXCHANGE
        ).map { kind -> visible.filter { it.kind == kind } }
            .filter { it.isNotEmpty() }

        if (groups.isEmpty()) {
            val empty = RemoteViews(context.packageName, R.layout.widget_empty)
            empty.setTextViewText(R.id.empty_text, "Nejsou vybrány žádné produkty")
            empty.setTextColor(R.id.empty_text, fg)
            views.addView(R.id.rows_container, empty)
            return views
        }

        groups.forEachIndexed { index, group ->
            if (index > 0) {
                // Oddělovač – barvu má XML, takže žádný setInt
                val divider = RemoteViews(context.packageName, R.layout.widget_divider)
                views.addView(R.id.rows_container, divider)
            }
            group.forEach { product ->
                val row = buildRow(context, product, settings, state, fg)
                views.addView(R.id.rows_container, row)
            }
        }

        return views
    }

    private fun buildRow(
        context: Context,
        product: Product,
        settings: WidgetSettings,
        state: PriceState,
        fg: Int
    ): RemoteViews {
        val row = RemoteViews(context.packageName, R.layout.widget_row)

        val cur = state.current?.entries?.get(product)
        val old = state.previous?.entries?.get(product)

        // Název
        row.setTextViewText(R.id.row_name, product.displayName)
        row.setTextColor(R.id.row_name, fg)
        row.setFloat(R.id.row_name, "setTextSize", settings.fontSizeSp.toFloat())

        // Stará cena v závorce
        val oldText: CharSequence = if (old != null) {
            "(${PriceFormatter.format(old, settings.currency)})"
        } else ""
        row.setTextViewText(R.id.row_old, oldText)
        row.setTextColor(R.id.row_old, fg)
        row.setFloat(R.id.row_old, "setTextSize", (settings.fontSizeSp - 1).toFloat())

        // Aktuální cena – DOČASNĚ bez superscriptu (test)
        val curText: CharSequence = PriceFormatter.format(cur, settings.currency)
        row.setTextViewText(R.id.row_price, curText)
        row.setTextColor(R.id.row_price, fg)
        row.setFloat(R.id.row_price, "setTextSize", settings.fontSizeSp.toFloat())

        // Trend
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
        row.setTextViewText(R.id.row_trend, arrow)
        row.setTextColor(R.id.row_trend, fg)
        row.setFloat(R.id.row_trend, "setTextSize", settings.fontSizeSp.toFloat())

        return row
    }

    private fun buildRefreshPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, TankOnoWidget::class.java).apply {
            action = TankOnoWidget.ACTION_REFRESH
        }
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun isNight(context: Context): Boolean {
        return (context.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
    }
}