package cz.tankono.widget.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import cz.tankono.widget.util.AppLogger
import cz.tankono.widget.work.WorkScheduler

class TankOnoWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        AppLogger.d("onUpdate: ${appWidgetIds.size} widgetů")
        for (id in appWidgetIds) {
            WidgetRenderer.render(context, appWidgetManager, id)
        }
    }

    /**
     * Volá se, když se změní velikost widgetu (uživatel ho zvětší/zmenší).
     */
    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        AppLogger.d("onAppWidgetOptionsChanged: widgetId=$appWidgetId, options=$newOptions")
        WidgetRenderer.render(context, appWidgetManager, appWidgetId)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        AppLogger.d("onReceive: action=${intent.action}")
        if (intent.action == ACTION_REFRESH) {
            AppLogger.i("Klik na widget – spouštím WorkManager")
            WorkScheduler.runNow(context)
        }
    }

    companion object {
        const val ACTION_REFRESH = "cz.tankono.widget.REFRESH"

        fun requestUpdate(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(
                ComponentName(context, TankOnoWidget::class.java)
            )
            if (ids.isEmpty()) {
                AppLogger.d("requestUpdate: žádné widgety")
                return
            }
            val intent = Intent(context, TankOnoWidget::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            }
            context.sendBroadcast(intent)
        }
    }
}