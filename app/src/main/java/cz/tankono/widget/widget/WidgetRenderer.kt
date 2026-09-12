package cz.tankono.widget.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.SuperscriptSpan
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

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun render(context: Context, mgr: AppWidgetManager, widgetId: Int) {
        AppLogger.d("=== RENDER widgetId=$widgetId ===")
        scope.launch {
            try {
                val settings = SettingsStore(context).settings.first()
                val state = PriceRepository(context).loadState()
                val views = buildViews(context, settings, state)
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
        settings: WidgetSettings,
        state: PriceState
    ): RemoteViews {
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
        views.setTextColor(R.id.header_date_web, fg)
        views.setFloat(R.id.header_date_web, "setTextSize", headerFontSize)

        views.setTextViewText(R.id.header_date_update, "($fetchedTime)")
        views.setTextColor(R.id.header_date_update, fg)
        views.setFloat(R.id.header_date_update, "setTextSize", headerFontSize)

        // ---------- PRODUKTY ----------
        val visible = Product.entries.filter { it in settings.visibleProducts }
        val groups = listOf(
            Product.Kind.FUEL,
            Product.Kind.OTHER,
            Product.Kind.EXCHANGE
        ).map { kind -> visible.filter { it.kind == kind } }
            .filter { it.isNotEmpty() }

        val sb = SpannableStringBuilder()

        groups.forEachIndexed { gIndex, group ->
            if (gIndex > 0) sb.append("\n")
            group.forEachIndexed { pIndex, product ->
                if (pIndex > 0) sb.append("\n")
                appendProductRow(sb, product, state, settings)
            }
        }

        views.setTextViewText(R.id.rows_text, sb)
        views.setTextColor(R.id.rows_text, fg)
        views.setFloat(R.id.rows_text, "setTextSize", settings.fontSizeSp.toFloat())

        return views
    }

    /**
     * Jeden řádek:
     *   "Natural 95      (42,50)   42,90 ▲"
     *
     * - název: BOLD
     * - stará cena: v závorce, ITALIC, menší
     * - aktuální cena: BOLD, desetinná část superscript + menší
     * - trend: ▲ / ▼ / =
     */
    private fun appendProductRow(
        sb: SpannableStringBuilder,
        product: Product,
        state: PriceState,
        settings: WidgetSettings
    ) {
        val cur = state.current?.entries?.get(product)
        val old = state.previous?.entries?.get(product)

        // 1) Název – BOLD
        val nameStart = sb.length
        sb.append(product.displayName)
        sb.setSpan(
            StyleSpan(Typeface.BOLD),
            nameStart, sb.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        // Zarovnání názvu na pevnou šířku mezerami (standardní font = proměnná šířka)
        val nameLen = product.displayName.length
        val padTo = 16
        repeat((padTo - nameLen).coerceAtLeast(2)) { sb.append(" ") }

        // 2) Stará cena v závorce – ITALIC + menší
        if (old != null) {
            val oldStr = "(${PriceFormatter.format(old, settings.currency)})"
            val oldStart = sb.length
            sb.append(oldStr)
            sb.setSpan(
                StyleSpan(Typeface.ITALIC),
                oldStart, sb.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            sb.setSpan(
                RelativeSizeSpan(0.8f),
                oldStart, sb.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            // Doplňkové mezery pro zarovnání
            repeat((11 - oldStr.length).coerceAtLeast(1)) { sb.append(" ") }
        } else {
            repeat(11) { sb.append(" ") }
        }

        // 3) Aktuální cena – BOLD, desetinná část superscript
        if (cur != null) {
            val curVal = PriceFormatter.valueFor(cur, settings.currency)
            if (curVal == null) {
                sb.append("--")
            } else {
                val whole: String
                val frac: String
                val isCzk = (product.kind == Product.Kind.EXCHANGE || settings.currency == Currency.CZK)
                if (isCzk) {
                    whole = (curVal / 100).toString()
                    frac = "%02d".format(curVal % 100)
                } else {
                    whole = (curVal / 1000).toString()
                    frac = "%03d".format(curVal % 1000)
                }

                val wholeStart = sb.length
                sb.append(whole)
                sb.setSpan(
                    StyleSpan(Typeface.BOLD),
                    wholeStart, sb.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )

                val fracStart = sb.length
                sb.append(frac)
                sb.setSpan(
                    StyleSpan(Typeface.BOLD),
                    fracStart, sb.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                sb.setSpan(
                    SuperscriptSpan(),
                    fracStart, sb.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                sb.setSpan(
                    RelativeSizeSpan(0.7f),
                    fracStart, sb.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        } else {
            sb.append("--")
        }

        // 4) Trend
        sb.append(" ")
        if (old != null && cur != null) {
            val oldVal = PriceFormatter.valueFor(old, settings.currency)
            val curVal = PriceFormatter.valueFor(cur, settings.currency)
            val arrow = when {
                oldVal == null || curVal == null -> ""
                curVal > oldVal -> "▲"
                curVal < oldVal -> "▼"
                else -> "="
            }
            sb.append(arrow)
        }
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