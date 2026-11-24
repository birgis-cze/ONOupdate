package cz.tankono.widget.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import cz.tankono.widget.data.db.PumpEntity
import cz.tankono.widget.data.model.Currency
import cz.tankono.widget.data.model.PriceFormatter
import cz.tankono.widget.data.model.PriceState
import cz.tankono.widget.data.model.Product
import cz.tankono.widget.data.prefs.NavigationApp
import cz.tankono.widget.data.prefs.SettingsStore
import cz.tankono.widget.data.remote.TankOnoScraper
import cz.tankono.widget.data.repo.PriceRepository
import cz.tankono.widget.data.repo.PumpRepository
import cz.tankono.widget.ui.components.NearestPumpSection
import cz.tankono.widget.ui.components.OnoHeader
import cz.tankono.widget.util.AppLogger
import cz.tankono.widget.util.LocationProvider
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(onNavigateToSettings: () -> Unit) {
    val context = LocalContext.current

    // Stavy pro ceny
    var state by remember { mutableStateOf<PriceState?>(null) }

    // Stavy pro nastavení (navigace)
    var preferredNav by remember { mutableStateOf(NavigationApp.SYSTEM) }

    // Stavy pro nejbližší stanici
    var nearestPump by remember { mutableStateOf<PumpEntity?>(null) }
    var nearestDistanceKm by remember { mutableStateOf<Double?>(null) }
    var hasLocation by remember { mutableStateOf(false) }

    // Načtení cen a nastavení
    LaunchedEffect(Unit) {
        state = PriceRepository(context).loadState()

        try {
            val settings = SettingsStore(context).settings.first()
            preferredNav = settings.preferredNavigation
        } catch (t: Throwable) {
            AppLogger.e("HomeScreen: chyba při načítání nastavení", t)
        }
    }

    // Načtení polohy a nejbližší stanice
    LaunchedEffect(Unit) {
        try {
            val hasPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                          ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            hasLocation = hasPerm

            if (hasPerm) {
                val loc = LocationProvider.getCurrentLocation(context)
                if (loc != null) {
                    val pumpRepo = PumpRepository(context)
                    val sorted = pumpRepo.getAllSortedByDistance(loc.first, loc.second)
                    if (sorted.isNotEmpty()) {
                        nearestPump = sorted.first().first
                        nearestDistanceKm = sorted.first().second
                    }
                }
            }
        } catch (t: Throwable) {
            AppLogger.e("HomeScreen: chyba při načítání polohy", t)
        }
    }

    val s = state
    if (s == null) {
        Box(Modifier.fillMaxSize().background(OnoYellow), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = OnoRed)
        }
        return
    }

    Surface(color = OnoYellow, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
        ) {
            // --- HLAVIČKA ---
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                OnoHeader()
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // --- NEJBLIŽŠÍ STANICE ---
                SettingsCard {
                    NearestPumpSection(
                        nearestPump = nearestPump,
                        distanceKm = nearestDistanceKm,
                        hasLocation = hasLocation,
                        onShowAll = { /* Zde by šlo otevřít seznam všech stanic – zatím prázdné */ },
                        onNavigate = { pump ->
                            val lat = pump.lat
                            val lng = pump.lng
                            if (lat != null && lng != null) {
                                preferredNav.navigate(context, lat, lng, pump.name)
                            }
                        }
                    )
                }

                // --- CENY ---
                SettingsCard {
                    SectionTitle("Aktuální ceny")

                    Product.entries.forEach { product ->
                        val cur = s.current?.entries?.get(product)
                        val old = s.previous?.entries?.get(product)
                        if (cur != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    product.displayName,
                                    color = OnoRed,
                                    fontSize = 14.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    PriceFormatter.format(old, Currency.CZK),
                                    color = OnoRed.copy(alpha = 0.6f),
                                    fontSize = 12.sp
                                )
                                Text(
                                    PriceFormatter.format(cur, Currency.CZK),
                                    color = OnoRed,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }
                    }
                }

                // --- ČASY AKTUALIZACÍ ---
                SettingsCard {
                    SectionTitle("Poslední aktualizace")
                    Text(
                        "Ceník zveřejněn: " + (TankOnoScraper.formatPublished(s.current?.publishedAt) ?: "--"),
                        color = OnoRed,
                        fontSize = 13.sp
                    )
                    Text(
                        "Widget aktualizován: " + (s.current?.fetchedAt?.let {
                            SimpleDateFormat("d.M.yyyy H:mm", Locale("cs", "CZ")).format(Date(it))
                        } ?: "--"),
                        color = OnoRed,
                        fontSize = 13.sp
                    )
                }

                // --- TLAČÍTKO NASTAVENÍ ---
                OutlinedButton(
                    onClick = onNavigateToSettings,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = OnoRed),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Nastavení")
                }
            }
        }
    }
}