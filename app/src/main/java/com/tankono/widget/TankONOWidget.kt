package com.tankono.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TankONOWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = DataManager.getPrices(context)
        val lastChangeDate = DataManager.getLastChangeDate(context)
        val visibleItems = MainActivity.getVisibleItems(context)
        val visibleKeys = visibleItems.map { it.first }.toSet()

        provideContent {
            WidgetContent(
                data = data,
                lastChangeDate = lastChangeDate,
                visibleKeys = visibleKeys
            )
        }
    }

    @Composable
    private fun WidgetContent(
        data: PriceData?,
        lastChangeDate: Long,
        visibleKeys: Set<String>
    ) {
        val yellow = Color(0xFFFFD600)
        val red = Color(0xFFC92200)
        val context = LocalContext.current

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(yellow)
                .padding(4.dp)
                .clickable(actionStartActivity(Intent(context, MainActivity::class.java)))
        ) {
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .height(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    provider = ImageProvider(R.drawable.logo_text),
                    contentDescription = "ONO",
                    modifier = GlanceModifier
                        .width(80.dp)
                        .height(18.dp)
                )

                Spacer(modifier = GlanceModifier.defaultWeight())

                Text(
                    text = if (lastChangeDate > 0) {
                        val formatter = SimpleDateFormat("d.M. HH:mm", Locale.getDefault())
                        formatter.format(Date(lastChangeDate))
                    } else {
                        "--:--"
                    },
                    style = TextStyle(
                        color = ColorProvider(red),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            Spacer(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(red)
            )

            Spacer(modifier = GlanceModifier.height(2.dp))

            if (data != null && data.n95 > 0) {
                if ("n95" in visibleKeys) PriceRow("N95", data.n95, data.n95Trend, red)
                if ("n95p" in visibleKeys) PriceRow("N95+", data.n95p, data.n95pTrend, red)
                if ("n98" in visibleKeys) PriceRow("N98", data.n98, data.n98Trend, red)
                if ("diesel" in visibleKeys) PriceRow("Diesel", data.diesel, data.dieselTrend, red)
                if ("dieselPlus" in visibleKeys) PriceRow("Diesel+", data.dieselPlus, data.dieselPlusTrend, red)
                if ("lpg" in visibleKeys) PriceRow("LPG", data.lpg, data.lpgTrend, red)
                if ("adBlue" in visibleKeys) PriceRow("AdBlue", data.adBlue, data.adBlueTrend, red)
                if ("om" in visibleKeys) PriceRow("OM", data.om, data.omTrend, red)
                if ("nm" in visibleKeys) PriceRow("NM", data.nm, data.nmTrend, red)
                if ("euro" in visibleKeys) PriceRow("EUR", data.euro, data.euroTrend, red)
            } else {
                Text(
                    text = "Načítání...",
                    style = TextStyle(
                        color = ColorProvider(red),
                        fontSize = 11.sp
                    )
                )
            }
        }
    }

    @Composable
    private fun PriceRow(
        label: String,
        value: Double,
        trend: Int,
        color: Color
    ) {
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = TextStyle(
                    color = ColorProvider(color),
                    fontSize = 11.sp,
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
                modifier = GlanceModifier.size(12.dp)
            )

            Spacer(modifier = GlanceModifier.width(4.dp))

            Text(
                text = String.format("%.2f", value),
                style = TextStyle(
                    color = ColorProvider(color),
                    fontSize = 11.sp,
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
        DebugHelper.log(context, "TankONOWidgetReceiver", "onUpdate volán")
    }
}