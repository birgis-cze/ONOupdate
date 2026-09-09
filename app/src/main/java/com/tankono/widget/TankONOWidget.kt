package com.tankono.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TankONOWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // OKAMŽITÁ AKTUALIZACE PŘI PŘIDÁNÍ WIDGETU
        val workRequest = OneTimeWorkRequestBuilder<UpdateWorker>().build()
        WorkManager.getInstance(context).enqueue(workRequest)
        
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        
        if (intent.action == "UPDATE_WIDGET") {
            // OKAMŽITÁ AKTUALIZACE PŘI KLIKNUTÍ
            val workRequest = OneTimeWorkRequestBuilder<UpdateWorker>().build()
            WorkManager.getInstance(context).enqueue(workRequest)
            
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, TankONOWidget::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            for (appWidgetId in appWidgetIds) {
                updateWidget(context, appWidgetManager, appWidgetId)
            }
        }
    }

    companion object {
        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, TankONOWidget::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            for (appWidgetId in appWidgetIds) {
                updateWidget(context, appWidgetManager, appWidgetId)
            }
        }

        private fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_layout)
            val data = DataManager.getPrices(context)
            val timestamp = DataManager.getLastUpdate(context)
            val lastChangeDate = DataManager.getLastChangeDate(context)

            // KLIKNUTÍ NA WIDGET
            val intent = Intent(context, TankONOWidget::class.java)
            intent.action = "UPDATE_WIDGET"
            val pendingIntent = PendingIntent.getBroadcast(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

            // ============================================================
            // 1. NASTAVENÍ ČASU – POUŽIJEME DATUM POSLEDNÍ ZMĚNY CEN
            // ============================================================
            if (lastChangeDate > 0) {
                // Formát: "8.9. 15:24" – datum poslední změny cen
                val formatter = SimpleDateFormat("d.M. HH:mm", Locale.getDefault())
                views.setTextViewText(R.id.tv_time, formatter.format(Date(lastChangeDate)))
            } else if (timestamp > 0) {
                // FALLBACK – čas poslední aktualizace aplikace
                val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
                views.setTextViewText(R.id.tv_time, formatter.format(Date(timestamp)))
            } else {
                views.setTextViewText(R.id.tv_time, "--:--")
            }

            // ============================================================
            // 2. ZJISTÍME VIDITELNÉ POLOŽKY Z NASTAVENÍ
            // ============================================================
            val visibleItems = MainActivity.getVisibleItems(context)
            val visibleKeys = visibleItems.map { it.first }.toSet()

            // ============================================================
            // 3. DEFINICE POLOŽEK – KLÍČ, ID TEXTU, ID TRENDU
            // ============================================================
            val keys = listOf(
                "n95", "n95p", "n98", "diesel", "dieselPlus", 
                "lpg", "adBlue", "om", "nm", "euro"
            )
            val textIds = listOf(
                R.id.tv_n95, R.id.tv_n95p, R.id.tv_n98, 
                R.id.tv_diesel, R.id.tv_diesel_plus,
                R.id.tv_lpg, R.id.tv_adblue, 
                R.id.tv_om, R.id.tv_nm, R.id.tv_euro
            )
            val trendIds = listOf(
                R.id.tv_n95_trend, R.id.tv_n95p_trend, R.id.tv_n98_trend,
                R.id.tv_diesel_trend, R.id.tv_diesel_plus_trend, 
                R.id.tv_lpg_trend, R.id.tv_adblue_trend,
                R.id.tv_om_trend, R.id.tv_nm_trend, R.id.tv_euro_trend
            )

            // ============================================================
            // 4. ZOBRAZENÍ DAT
            // ============================================================
            if (data != null && data.n95 > 0) {
                for (i in keys.indices) {
                    val key = keys[i]
                    val textId = textIds[i]
                    val trendId = trendIds[i]
                    val isVisible = key in visibleKeys
                    
                    if (isVisible) {
                        // Získání hodnoty podle klíče
                        val value = when (key) {
                            "n95" -> data.n95
                            "n95p" -> data.n95p
                            "n98" -> data.n98
                            "diesel" -> data.diesel
                            "dieselPlus" -> data.dieselPlus
                            "lpg" -> data.lpg
                            "adBlue" -> data.adBlue
                            "om" -> data.om
                            "nm" -> data.nm
                            "euro" -> data.euro
                            else -> 0.0
                        }
                        
                        // Získání trendu podle klíče
                        val trend = when (key) {
                            "n95" -> data.n95Trend
                            "n95p" -> data.n95pTrend
                            "n98" -> data.n98Trend
                            "diesel" -> data.dieselTrend
                            "dieselPlus" -> data.dieselPlusTrend
                            "lpg" -> data.lpgTrend
                            "adBlue" -> data.adBlueTrend
                            "om" -> data.omTrend
                            "nm" -> data.nmTrend
                            "euro" -> data.euroTrend
                            else -> 0
                        }
                        
                        views.setTextViewText(textId, String.format("%.2f", value))
                        views.setImageViewResource(trendId, getTrendIcon(trend))
                        views.setViewVisibility(textId, android.view.View.VISIBLE)
                        views.setViewVisibility(trendId, android.view.View.VISIBLE)
                    } else {
                        views.setViewVisibility(textId, android.view.View.GONE)
                        views.setViewVisibility(trendId, android.view.View.GONE)
                    }
                }
            } else {
                // ŽÁDNÁ DATA – ZOBRAZÍME ČÁRKY
                for (textId in textIds) {
                    views.setTextViewText(textId, "--")
                    views.setViewVisibility(textId, android.view.View.VISIBLE)
                }
                for (trendId in trendIds) {
                    views.setViewVisibility(trendId, android.view.View.GONE)
                }
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun getTrendIcon(trend: Int): Int {
            return when (trend) {
                1 -> R.drawable.ic_arrow_up
                -1 -> R.drawable.ic_arrow_down
                else -> R.drawable.ic_arrow_equal
            }
        }
    }
}