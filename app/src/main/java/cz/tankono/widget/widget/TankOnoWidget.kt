package cz.tankono.widget.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import cz.tankono.widget.data.prefs.NavigationApp
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

        when (intent.action) {
            ACTION_REFRESH -> {
                AppLogger.i("Klik na widget – spouštím WorkManager")
                WorkScheduler.runNow(context)
            }
            ACTION_NAVIGATE -> {
                AppLogger.i("Klik na nejbližší stanici – spouštím navigaci")
                handleNavigate(context, intent)
            }
        }
    }

    /**
     * Otevře navigaci do zadané stanice pomocí přednastavené aplikace.
     * Stejná logika jako tlačítko "M" v nastavení.
     */
    private fun handleNavigate(context: Context, intent: Intent) {
        val lat = intent.getDoubleExtra(EXTRA_LAT, 0.0)
        val lng = intent.getDoubleExtra(EXTRA_LNG, 0.0)
        val label = intent.getStringExtra(EXTRA_LABEL) ?: ""
        val navAppId = intent.getStringExtra(EXTRA_NAV_APP)

        if (lat == 0.0 && lng == 0.0) {
            AppLogger.w("Navigace: neplatné souřadnice (0,0)")
            return
        }

        val navApp = NavigationApp.fromId(navAppId)
        AppLogger.i("Navigace: $navApp → $lat,$lng ($label)")
        navApp.navigate(context, lat, lng, label)
    }

    companion object {
        const val ACTION_REFRESH = "cz.tankono.widget.REFRESH"
        const val ACTION_NAVIGATE = "cz.tankono.widget.NAVIGATE"

        const val EXTRA_LAT = "extra_lat"
        const val EXTRA_LNG = "extra_lng"
        const val EXTRA_LABEL = "extra_label"
        const val EXTRA_NAV_APP = "extra_nav_app"

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