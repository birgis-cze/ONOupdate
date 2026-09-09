package com.tankono.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.work.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class TankONOWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        
        if (intent.action == "UPDATE_WIDGET") {
            val lastUpdate = DataManager.getLastUpdate(context)
            val now = System.currentTimeMillis()
            val diffMinutes = TimeUnit.MILLISECONDS.toMinutes(now - lastUpdate)
            
            if (diffMinutes > 10) {
                val workRequest = OneTimeWorkRequestBuilder<UpdateWorker>()
                    .build()
                WorkManager.getInstance(context).enqueue(workRequest)
            }
            
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, TankONOWidget::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            onUpdate(context, appWidgetManager, appWidgetIds)
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
            val visibleItems = MainActivity.getVisibleItems(context)

            val intent = Intent(context, TankONOWidget::class.java)
            intent.action = "UPDATE_WIDGET"
            val pendingIntent = PendingIntent.getBroadcast(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

            // Nastavíme čas
            if (timestamp > 0) {
                val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
                views.setTextViewText(R.id.tv_time, formatter.format(Date(timestamp)))
            } else {
                views.setTextViewText(R.id.tv_time, "--:--")
            }

            if (data != null && data.n95 > 0) {
                // Dynamické zobrazení položek podle nastavení
                val itemKeys = listOf(
                    "n95" to R.id.tv_n95 to R.id.tv_n95_trend,
                    "n95p" to R.id.tv_n95p to R.id.tv_n95p_trend,
                    "n98" to R.id.tv_n98 to R.id.tv_n98_trend,
                    "diesel" to R.id.tv_diesel to R.id.tv_diesel_trend,
                    "dieselPlus" to R.id.tv_diesel_plus to R.id.tv_diesel_plus_trend,
                    "lpg" to R.id.tv_lpg to R.id.tv_lpg_trend,
                    "adBlue" to R.id.tv_adblue to R.id.tv_adblue_trend,
                    "om" to R.id.tv_om to R.id.tv_om_trend,
                    "nm" to R.id.tv_nm to R.id.tv_nm_trend,
                    "euro" to R.id.tv_euro to R.id.tv_euro_trend
                )
                
                // Mapování klíčů na hodnoty a trendy
                val valueMap = mapOf(
                    "n95" to data.n95 to data.n95Trend,
                    "n95p" to data.n95p to data.n95pTrend,
                    "n98" to data.n98 to data.n98Trend,
                    "diesel" to data.diesel to data.dieselTrend,
                    "dieselPlus" to data.dieselPlus to data.dieselPlusTrend,
                    "lpg" to data.lpg to data.lpgTrend,
                    "adBlue" to data.adBlue to data.adBlueTrend,
                    "om" to data.om to data.omTrend,
                    "nm" to data.nm to data.nmTrend,
                    "euro" to data.euro to data.euroTrend
                )

                val visibleKeys = visibleItems.map { it.first }.toSet()
                
                for ((key, textViewId, trendViewId) in itemKeys) {
                    if (key in visibleKeys) {
                        val (value, trend) = valueMap[key] ?: (0.0 to 0)
                        views.setTextViewText(textViewId, String.format("%.2f", value))
                        views.setImageViewResource(trendViewId, getTrendIcon(trend))
                        views.setViewVisibility(textViewId, android.view.View.VISIBLE)
                        views.setViewVisibility(trendViewId, android.view.View.VISIBLE)
                    } else {
                        views.setViewVisibility(textViewId, android.view.View.GONE)
                        views.setViewVisibility(trendViewId, android.view.View.GONE)
                    }
                }

            } else {
                // Žádná data – skryjeme všechny položky
                val allIds = listOf(
                    R.id.tv_n95, R.id.tv_n95_trend,
                    R.id.tv_n95p, R.id.tv_n95p_trend,
                    R.id.tv_n98, R.id.tv_n98_trend,
                    R.id.tv_diesel, R.id.tv_diesel_trend,
                    R.id.tv_diesel_plus, R.id.tv_diesel_plus_trend,
                    R.id.tv_lpg, R.id.tv_lpg_trend,
                    R.id.tv_adblue, R.id.tv_adblue_trend,
                    R.id.tv_om, R.id.tv_om_trend,
                    R.id.tv_nm, R.id.tv_nm_trend,
                    R.id.tv_euro, R.id.tv_euro_trend
                )
                for (id in allIds) {
                    views.setViewVisibility(id, android.view.View.GONE)
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