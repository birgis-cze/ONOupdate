package cz.tankono.widget.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.tankono.widget.data.db.PumpEntity
import cz.tankono.widget.data.prefs.NavigationApp
import cz.tankono.widget.ui.OnoRed
import cz.tankono.widget.ui.OnoYellow

@Composable
fun NavigationRow(current: NavigationApp, installedApps: List<NavigationApp>, onSelect: (NavigationApp) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Text("Navigovat přes: ", color = OnoRed, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(current.displayName, color = OnoRed, fontSize = 14.sp, fontWeight = FontWeight.Normal)
            }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .border(2.dp, OnoRed, RoundedCornerShape(8.dp))
                    .clickable { expanded = true },
                contentAlignment = Alignment.Center
            ) {
                Text("▼", color = OnoRed, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(OnoYellow).border(2.dp, OnoRed)
        ) {
            DropdownMenuItem(
                text = {
                    Text(
                        "Systém (vždy hlavní pro geo)",
                        color = OnoRed,
                        fontSize = 14.sp,
                        fontWeight = if (current == NavigationApp.SYSTEM) FontWeight.Bold else FontWeight.Normal
                    )
                },
                onClick = { onSelect(NavigationApp.SYSTEM); expanded = false }
            )
            installedApps.forEach { app ->
                DropdownMenuItem(
                    text = {
                        Text(
                            app.displayName,
                            color = OnoRed,
                            fontSize = 14.sp,
                            fontWeight = if (current == app) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    onClick = { onSelect(app); expanded = false }
                )
            }
        }
    }
}

@Composable
fun NearestPumpSection(
    nearestPump: PumpEntity?,
    distanceKm: Double?,
    hasLocation: Boolean,
    onShowAll: () -> Unit,
    onNavigate: (PumpEntity) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        when {
            nearestPump != null && distanceKm != null -> {
                Text("Nejbližší stanice:", color = OnoRed, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            nearestPump.name.removePrefix("ČS ").trim(),
                            color = OnoRed,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            if (distanceKm < 10.0) "%.1f km".format(distanceKm).replace('.', ',')
                            else "%.0f km".format(distanceKm),
                            color = OnoRed,
                            fontSize = 13.sp
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SquareButton("M") { onNavigate(nearestPump) }
                        SquareButton("S") { onShowAll() }
                    }
                }
            }
            !hasLocation -> {
                Text("Nejbližší stanice:", color = OnoRed, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text("   Nedostupná poloha", color = OnoRed, fontSize = 12.sp)
            }
            else -> {
                Text("Nejbližší stanice:", color = OnoRed, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text("   Zjišťuji…", color = OnoRed, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun SquareButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .border(2.dp, OnoRed, RoundedCornerShape(8.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = OnoRed, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}