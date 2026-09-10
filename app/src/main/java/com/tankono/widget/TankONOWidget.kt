package com.tankono.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
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
        val prefs = context.getSharedPreferences("tankono_prefs", Context.MODE_PRIVATE)
        val selectedKeys = prefs.getStringSet("selected_fuels", null)
            ?: setOf("n95", "diesel", "lpg")

        val visibleKeys = fuelOrder.filter { it in selectedKeys }
        
        val data = DataManager.getPrices(context)
        val oldData = DataManager.getOldPrices(context)
        
        val lastUpdate = DataManager.getLastUpdate(context)
        val lastChangeDate = DataManager.getLastChangeDate(context)

        val isCompact = visibleKeys.size <= 4
        val textSize = if (isCompact) 13.sp else 11.sp
        val rowPadding = if (isCompact) 3.dp else 1.dp

        val redColor = ColorProvider(R.color.red)
        val whiteColor = ColorProvider(R.color.white)
        // OPRAVA: Načtení z R.color namísto přímého přetypování barev
        val grayColor = ColorProvider(R.color.widget_text_gray)
        val darkGrayColor = ColorProvider(R.color.widget_text_dark_gray)

        provideContent {
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(ImageProvider(R.drawable.widget_background))
                    .padding(8.dp)
            ) {
                Column(
                    modifier = GlanceModifier.fillMaxSize()
                ) {
                    // HLAVIČKA
                    Box(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .height(30.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            provider = ImageProvider(R.drawable.logo_linka),
                            contentDescription = null,
                            modifier = GlanceModifier.fillMaxWidth()
                        )

                        Row(
                            modifier = GlanceModifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Image(
                                provider = ImageProvider(R.drawable.logo_text),
                                contentDescription = "Tank ONO",
                                modifier = GlanceModifier.height(20.dp)
                            )

                            Spacer(modifier = GlanceModifier.defaultWeight())

                            Column(
                                horizontalAlignment = Alignment.End
                            ) {
                                if (lastChangeDate.isNotEmpty()) {
                                    Text(
                                        text = "Změna: $lastChangeDate",
                                        style = TextStyle(
                                            color = grayColor,
                                            fontSize = 9.sp
                                        )
                                    )
                                }
                                
                                val timeStr = if (lastUpdate > 0) {
                                    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(lastUpdate))
                                } else "--:--"

                                Text(
                                    text = "Akt: $timeStr",
                                    style = TextStyle(
                                        color = darkGrayColor,
                                        fontSize = 9.sp
                                    )
                                )
                            }
                        }
                    }

                    Spacer(modifier = GlanceModifier.height(6.dp))

                    // SEZNAM CEN
                    if (data != null) {
                        if ("n95" in visibleKeys) PriceRow("Natural 95", data.n95, data.getN95Trend(oldData), redColor, whiteColor, textSize, rowPadding)
                        if ("n95p" in visibleKeys) PriceRow("Natural 95+", data.n95p, data.getN95pTrend(oldData), redColor, whiteColor, textSize, rowPadding)
                        if ("n98" in visibleKeys) PriceRow("Natural 98", data.n98, data.getN98Trend(oldData), redColor, whiteColor, textSize, rowPadding)
                        if ("diesel" in visibleKeys) PriceRow("Diesel", data.diesel, data.getDieselTrend(oldData), redColor, whiteColor, textSize, rowPadding)
                        if ("dieselPlus" in visibleKeys) PriceRow("Diesel+", data.dieselPlus, data.getDieselPlusTrend(oldData), redColor, whiteColor, textSize, rowPadding)
                        if ("lpg" in visibleKeys) PriceRow("LPG", data.lpg, data.getLpgTrend(oldData), redColor, whiteColor, textSize, rowPadding)
                        if ("adBlue" in visibleKeys) PriceRow("AdBlue", data.adBlue, data.getAdBlueTrend(oldData), redColor, whiteColor, textSize, rowPadding)
                        if ("om" in visibleKeys) PriceRow("Osobní myčka", data.om, data.getOmTrend(oldData), redColor, whiteColor, textSize, rowPadding)
                        if ("nm" in visibleKeys) PriceRow("Nákladní myčka", data.nm, data.getNmTrend(oldData), redColor, whiteColor, textSize, rowPadding)
                        if ("euro" in visibleKeys) PriceRow("EUR", data.euro, data.getEuroTrend(oldData), redColor, whiteColor, textSize, rowPadding)
                    } else {
                        Text(
                            text = "Načítám data...",
                            style = TextStyle(color = whiteColor, fontSize = textSize)
                        )
                    }

                    Spacer(modifier = GlanceModifier.defaultWeight())

                    // PATKA
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Spacer(modifier = GlanceModifier.defaultWeight())

                        Image(
                            provider = ImageProvider(android.R.drawable.ic_popup_sync),
                            contentDescription = "Obnovit",
                            modifier = GlanceModifier
                                .padding(2.dp)
                                .clickable(actionRunCallback<RefreshCallback>())
                        )

                        Spacer(modifier = GlanceModifier.width(8.dp))

                        Image(
                            provider = ImageProvider(android.R.drawable.ic_menu_preferences),
                            contentDescription = "Nastavení",
                            modifier = GlanceModifier
                                .padding(2.dp)
                                .clickable(actionStartActivity<MainActivity>())
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun PriceRow(
        label: String,
        price: Double,
        trend: Int,
        priceColor: ColorProvider,
        labelColor: ColorProvider,
        textSize: androidx.compose.ui.unit.TextUnit,
        padding: androidx.compose.ui.unit.Dp
    ) {
        val arrowRes = when (trend) {
            1 -> R.drawable.ic_arrow_up
            -1 -> R.drawable.ic_arrow_down
            else -> R.drawable.ic_arrow_equal
        }

        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(vertical = padding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = TextStyle(
                    color = labelColor,
                    fontSize = textSize,
                    fontWeight = FontWeight.Bold
                )
            )
            Spacer(modifier = GlanceModifier.defaultWeight())
            Text(
                text = if (price > 0) String.format(Locale.US, "%.2f", price) else "--.--",
                style = TextStyle(
                    color = priceColor,
                    fontSize = textSize,
                    fontWeight = FontWeight.Bold
                )
            )
            Spacer(modifier = GlanceModifier.width(4.dp))
            Image(
                provider = ImageProvider(arrowRes),
                contentDescription = null
            )
        }
    }

    companion object {
        val fuelOrder = listOf(
            "n95", "n95p", "n98", "diesel", "dieselPlus",
            "lpg", "adBlue", "om", "nm", "euro"
        )
    }
}

class RefreshCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: androidx.glance.action.ActionParameters
    ) {
        try {
            DataFetcher.fetchAndSave(context)
            TankONOWidget().updateAll(context)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

suspend fun updateAllWidgetsState(context: Context) {
    try {
        TankONOWidget().updateAll(context)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}