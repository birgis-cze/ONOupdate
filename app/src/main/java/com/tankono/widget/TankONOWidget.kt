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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class TankONOWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = DataManager.getPrices(context)
        val lastChangeDate = DataManager.getLastChangeDate(context)
        val lastUpdate = DataManager.getLastUpdate(context)
        val visibleItems = MainActivity.getVisibleItems(context)
        val visibleKeys = visibleItems.map { it.first }.toSet()

        provideContent {
            WidgetContent(
                data = data,
                lastChangeDate = lastChangeDate,
                lastUpdate = lastUpdate,
                visibleKeys = visibleKeys
            )
        }
    }

    @Composable
    private fun WidgetContent(
        data: PriceData?,
        lastChangeDate: Long,
        lastUpdate: Long,
        visibleKeys: Set<String>
    ) {
        val yellow = Color(0xFFFFD600)
        val red = Color(0xFFC92200)
        val context = LocalContext.current

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(yellow)
                .padding(3.dp)
                .clickable(actionStartActivity(Intent(context, MainActivity::class.java)))
        ) {
            // HLAVIČKA – vrstvená (linka + logo)
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .height(28.dp)
            ) {
                // Vrstva 1: Linka pozadí (zarovnaná dolů)
                Image(
                    provider = ImageProvider(R.drawable.logo_linka),
                    contentDescription = "Linka",
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .height(28.dp)
                )

                // Vrstva 2: Obsah nad linkou
                Row(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .padding(start = 4.dp, end = 4.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    Image(
                        provider = ImageProvider(R.drawable.logo_text),
                        contentDescription = "ONO",
                        modifier = GlanceModifier
                            .width(50.dp)
                            .height(24.dp)
                    )

                    Spacer(modifier = GlanceModifier.defaultWeight())

                    Text(
                        text = buildTimestampText(lastChangeDate, lastUpdate),
                        style = TextStyle(
                            color = ColorProvider(red),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }

            Spacer(modifier = GlanceModifier.height(2.dp))

            // CENY
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
                        fontSize = 10.sp
                    )
                )
            }
        }
    }

    /**
     * Sestaví text: "st 09.09.2026 15:45 (10:25)"
     * - datum a čas poslední změny cen
     * - v závorce čas posledního updatu
     */
    private fun buildTimestampText(lastChangeDate: Long, lastUpdate: Long): String {
        val changeFormatter = SimpleDateFormat("EEE dd.MM.yyyy HH:mm", Locale("cs", "CZ"))
        val updateFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())

        val changeStr = if (lastChangeDate > 0) {
            changeFormatter.format(Date(lastChangeDate)).lowercase(Locale("cs", "CZ"))
        } else {
            "--"
        }

        val updateStr = if (lastUpdate > 0) {
            updateFormatter.format(Date(lastUpdate))
        } else {
            "--:--"
        }

        return "$changeStr ($updateStr)"
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
                .height(15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = TextStyle(
                    color = ColorProvider(color),
                    fontSize = 10.sp,
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
                modifier = GlanceModifier.size(10.dp)
            )

            Spacer(modifier = GlanceModifier.width(3.dp))

            Text(
                text = String.format("%.2f", value),
                style = TextStyle(
                    color = ColorProvider(color),
                    fontSize = 10.sp,
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