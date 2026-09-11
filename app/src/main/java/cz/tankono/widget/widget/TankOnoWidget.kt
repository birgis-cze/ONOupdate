package cz.tankono.widget.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * AppWidgetProvider – přijímá broadcasty od systému a od uživatele.
 *
 * Akce:
 *  - APPWIDGET_UPDATE: systém požaduje překreslení widgetu (po přidání, po změně velikosti)
 *  - ACTION_REFRESH: uživatel klikl na widget → vynutit aktualizaci
 */
class TankOnoWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        Log.d(TAG, "onUpdate: ${appWidgetIds.size} widgetů")
        for (id in appWidgetIds) {
            WidgetRenderer.render(context, appWidgetManager, id)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            Log.d(TAG, "Klik na widget – spouštím refresh")
            // Spustí WorkManager jednorázově
            //cz.tankono.widget.work.WorkScheduler.runNow(context)
            WorkScheduler.runNow(context)
        }
    }

    companion object {
        private const val TAG = "TankOnoWidget"

        /** Akce – klik na widget. */
        const val ACTION_REFRESH = "cz.tankono.widget.REFRESH"

        /**
         * Vyžádá překreslení všech instancí widgetu.
         * Volá se po změně nastavení nebo po stažení nových dat.
         */
        fun requestUpdate(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(
                ComponentName(context, TankOnoWidget::class.java)
            )
            if (ids.isEmpty()) {
                Log.d(TAG, "requestUpdate: žádné widgety na ploše")
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