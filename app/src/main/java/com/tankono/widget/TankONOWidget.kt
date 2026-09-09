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

            // Nastavení kliknutí na widget
            val intent = Intent(context, TankONOWidget::class.java)
            intent.action = "UPDATE_WIDGET"
            val pendingIntent = PendingIntent.getBroadcast(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

            // Nastavení času
            if (timestamp > 0) {
                val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
                views.setTextViewText(R.id.tv_time, formatter.format(Date(timestamp)))
            } else {
                views.setTextViewText(R.id.tv_time, "--:--")
            }

            // Mapování ID pro text a trend
            val itemIds = listOf(
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

            // Mapování hodnot a trendů
            val visibleKeys = visibleItems.map { it.first }.toSet()

            if (data != null && data.n95 > 0) {
                // Pro každou položku zkontrolujeme, zda má být viditelná
                for ((key, textId, trendId) in itemIds) {
                    val isVisible = key in visibleKeys
                    
                    if (isVisible) {
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
                // Žádná data – skryjeme všechny položky
                for ((_, textId, trendId) in itemIds) {
                    views.setViewVisibility(textId, android.view.View.GONE)
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