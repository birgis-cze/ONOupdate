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
        DebugHelper.log(context, "UpdateCallback", "=== KLIKNUTÍ NA WIDGET (glanceId=$glanceId) ===")

        try {
            // Stáhneme data
            val success = DataFetcher.fetchAndSave(context)

            if (success) {
                DebugHelper.log(context, "UpdateCallback", "✅ Data aktualizována")
            } else {
                DebugHelper.log(context, "UpdateCallback", "❌ Aktualizace selhala")
            }

            // ✅ AKTUALIZUJEME KONKRÉTNÍ WIDGET (ne všechny)
            TankONOWidget().update(context, glanceId)
            DebugHelper.log(context, "UpdateCallback", "✅ Widget překreslen (glanceId=$glanceId)")

        } catch (e: Exception) {
            DebugHelper.log(context, "UpdateCallback", "❌ CHYBA: ${e.message}")
        }
    }
}