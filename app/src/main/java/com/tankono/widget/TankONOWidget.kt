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

    // KLIKNUTÍ NA WIDGET – AKTUALIZACE POKUD JE DATA STARŠÍ NEŽ 10 MIN
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        
        if (intent.action == "UPDATE_WIDGET") {
            val lastUpdate = DataManager.getLastUpdate(context)
            val now = System.currentTimeMillis()
            val diffMinutes = TimeUnit.MILLISECONDS.toMinutes(now - lastUpdate)
            
            if (diffMinutes > 10) {
                // Spustí aktualizaci na pozadí
                val workRequest = OneTimeWorkRequestBuilder<UpdateWorker>()
                    .build()
                WorkManager.getInstance(context).enqueue(workRequest)
            }
            
            // Aktualizuje widget
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

            // NASTAVÍ KLIKÁNÍ NA WIDGET
            val intent = Intent(context, TankONOWidget::class.java)
            intent.action = "UPDATE_WIDGET"
            val pendingIntent = PendingIntent.getBroadcast(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

            if (data != null && data.n95 > 0) {
                val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
                views.setTextViewText(R.id.tv_time, formatter.format(Date(timestamp)))

                views.setTextViewText(R.id.tv_n95, String.format("%.2f", data.n95))
                views.setTextViewText(R.id.tv_n95p, String.format("%.2f", data.n95p))
                views.setTextViewText(R.id.tv_nafta, String.format("%.2f", data.nafta))
                views.setTextViewText(R.id.tv_lpg, String.format("%.2f", data.lpg))

                views.setImageViewResource(R.id.tv_n95_trend, getTrendIcon(data.n95Trend))
                views.setImageViewResource(R.id.tv_n95p_trend, getTrendIcon(data.n95pTrend))
                views.setImageViewResource(R.id.tv_nafta_trend, getTrendIcon(data.naftaTrend))
                views.setImageViewResource(R.id.tv_lpg_trend, getTrendIcon(data.lpgTrend))
            } else {
                views.setTextViewText(R.id.tv_n95, "--")
                views.setTextViewText(R.id.tv_n95p, "--")
                views.setTextViewText(R.id.tv_nafta, "--")
                views.setTextViewText(R.id.tv_lpg, "--")
                views.setTextViewText(R.id.tv_time, "--:--")
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