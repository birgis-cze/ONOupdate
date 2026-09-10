package com.tankono.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TankONOWidget : GlanceAppWidget() {

    // ✅ POLOVIČNÍ VÝŠKA (57/2 ≈ 28)
    private val HEADER_HEIGHT = 28.dp
    private val LOGO_WIDTH = 78.dp
    private val LOGO_HEIGHT = 28.dp

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = DataManager.getPrices(context)
        val lastChangeDate = DataManager.getLastChangeDate(context)
        val lastUpdate = DataManager.getLastUpdate(context)
        val visibleItems = MainActivity.getVisibleItems(context)
        val visibleKeys = visibleItems.map { it.first }.toSet()
        val textSize = MainActivity.getWidgetTextSize(context)

        DebugHelper.log(context, "TankONOWidget",
            "provideGlance: NM=${data?.nm}, EUR=${data?.euro}, keys=$visibleKeys, textSize=$textSize")

        if (data == null) {
            try {
                val workRequest = OneTimeWorkRequestBuilder<UpdateWorker>().build()
                WorkManager.getInstance(context).enqueue(workRequest)
            } catch (e: Exception) {
                DebugHelper.log(context, "TankONOWidget", "Chyba: ${e.message}")
            }
        }

        provideContent {
            WidgetContent(
                data = data,
                lastChangeDate = lastChangeDate,
                lastUpdate = lastUpdate,
                visibleKeys = visibleKeys,
                textSize = textSize
            )
        }
    }

    @Composable
    private fun WidgetContent(
        data: PriceData?,
        lastChangeDate: Long,
        lastUpdate: Long,
        visibleKeys: Set<String>,
        textSize: Int
    ) {
        val yellow = Color(0xFFFFD600)
        val red = Color(0xFFC92200)

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(yellow)
                .padding(3.dp)
                .clickable(actionRunCallback<UpdateCallback>())
        ) {
            // HLAVIČKA
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .height(HEADER_HEIGHT)
            ) {
                Image(
                    provider = ImageProvider(R.drawable.logo_linka),
                    contentDescription = "Linka",
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .height(HEADER_HEIGHT)
                )

                Row(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .padding(start = 0.dp, end = 4.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    Image(
                        provider = ImageProvider(R.drawable.logo_text),
                        contentDescription = "ONO",
                        modifier = GlanceModifier
                            .width(LOGO_WIDTH)
                            .height(LOGO_HEIGHT)
                    )

                    Spacer(modifier = GlanceModifier.defaultWeight())

                    Text(
                        text = buildTimestampText(lastChangeDate, lastUpdate),
                        style = TextStyle(
                            color = ColorProvider(red),
                            fontSize = (textSize - 4).coerceAtLeast(6).sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }

            Spacer(modifier = GlanceModifier.height(2.dp))

            // CENY – bez kontroly n95
            if (data != null) {
                if ("n95" in visibleKeys) PriceRow("Natural 95", data.n95, data.n95Trend, red, textSize)
                if ("n95p" in visibleKeys) PriceRow("Natural 95+", data.n95p, data.n95pTrend, red, textSize)
                if ("n98" in visibleKeys) PriceRow("Natural 98", data.n98, data.n98Trend, red, textSize)
                if ("diesel" in visibleKeys) PriceRow("Diesel", data.diesel, data.dieselTrend, red, textSize)
                if ("dieselPlus" in visibleKeys) PriceRow("Diesel+", data.dieselPlus, data.dieselPlusTrend, red, textSize)
                if ("lpg" in visibleKeys) PriceRow("LPG", data.lpg, data.lpgTrend, red, textSize)
                if ("adBlue" in visibleKeys) PriceRow("AdBlue", data.adBlue, data.adBlueTrend, red, textSize)
                if ("om" in visibleKeys) PriceRow("Osobní myčka", data.om, data.omTrend, red, textSize)
                if ("nm" in visibleKeys) PriceRow("Nákladní myčka", data.nm, data.nmTrend, red, textSize)
                if ("euro" in visibleKeys) PriceRow("EUR", data.euro, data.euroTrend, red, textSize)
            } else {
                Text(
                    text = "Klikni pro načtení...",
                    style = TextStyle(
                        color = ColorProvider(red),
                        fontSize = textSize.sp
                    )
                )
            }
        }
    }

    private fun buildTimestampText(lastChangeDate: Long, lastUpdate: Long): String {
        val changeFormatter = SimpleDateFormat("EEE dd.MM.yyyy HH:mm", Locale("cs", "CZ"))
        val updateFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())

        val changeStr = if (lastChangeDate > 0) {
            changeFormatter.format(Date(lastChangeDate)).lowercase(Locale("cs", "CZ"))
        } else "--"

        val updateStr = if (lastUpdate > 0) {
            updateFormatter.format(Date(lastUpdate))
        } else "--:--"

        return "$changeStr ($updateStr)"
    }

    @Composable
    private fun PriceRow(
        label: String,
        value: Double,
        trend: Int,
        color: Color,
        textSize: Int
    ) {
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(vertical = 1.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = TextStyle(
                    color = ColorProvider(color),
                    fontSize = textSize.sp,
                    fontWeight = FontWeight.Bold
                ),
                modifier = GlanceModifier.defaultWeight()
            )

            val trendIcon = when (trend) {
                1 -> R.drawable.ic_arrow_up
                -1 -> R.drawable.ic_arrow_down
                else -> R.drawable.ic_arrow_equal
            }
            Image(
                provider = ImageProvider(trendIcon),
                contentDescription = "Trend",
                modifier = GlanceModifier.size(textSize.dp)
            )

            Spacer(modifier = GlanceModifier.width(3.dp))

            Text(
                text = String.format("%.2f", value),
                style = TextStyle(
                    color = ColorProvider(color),
                    fontSize = textSize.sp,
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }
}

class TankONOWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TankONOWidget()

    override fun onUpdate(
        context: Context,
        appWidgetManager: android.appwidget.AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        DebugHelper.log(context, "TankONOWidgetReceiver", "onUpdate (${appWidgetIds.size})")

        try {
            val workRequest = OneTimeWorkRequestBuilder<UpdateWorker>().build()
            WorkManager.getInstance(context).enqueue(workRequest)
        } catch (e: Exception) {
            DebugHelper.log(context, "TankONOWidgetReceiver", "Chyba: ${e.message}")
        }
    }
}