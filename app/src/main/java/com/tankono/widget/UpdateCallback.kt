package com.tankono.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.updateAll

class UpdateCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        DebugHelper.log(context, "UpdateCallback", "=== KLIKNUTÍ NA WIDGET ===")

        try {
            // 1. Stáhneme data
            val success = DataFetcher.fetchAndSave(context)

            if (success) {
                DebugHelper.log(context, "UpdateCallback", "✅ Data aktualizována")
            } else {
                DebugHelper.log(context, "UpdateCallback", "⚠️ Aktualizace selhala, použijeme stará data")
            }

            // 2. ✅ VYNUCENĚ aktualizujeme KONKRÉTNÍ widget
            TankONOWidget().update(context, glanceId)
            DebugHelper.log(context, "UpdateCallback", "✅ Widget update(glanceId)")

            // 3. ✅ PRO JISTOTU aktualizujeme VŠECHNY widgety
            TankONOWidget().updateAll(context)
            DebugHelper.log(context, "UpdateCallback", "✅ Widget updateAll")

        } catch (e: Exception) {
            DebugHelper.log(context, "UpdateCallback", "❌ CHYBA: ${e.message}")
            e.printStackTrace()
        }
    }
}