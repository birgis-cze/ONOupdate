package cz.tankono.widget.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import cz.tankono.widget.ui.components.AllPumpsSheet
import cz.tankono.widget.ui.components.NearestPumpSection
import cz.tankono.widget.ui.components.OnoHeader
import cz.tankono.widget.util.AppLogger
import cz.tankono.widget.util.LocationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToSettings: () -> Unit,
    onRefresh: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var state by remember { mutableStateOf<PriceState?>(null) }
    var preferredNav by remember { mutableStateOf(NavigationApp.SYSTEM) }

    var nearestPump by remember { mutableStateOf<PumpEntity?>(null) }
    var nearestDistanceKm by remember { mutableStateOf<Double?>(null) }
    var hasLocation by remember { mutableStateOf(false) }

    var showAllPumpsSheet by remember { mutableStateOf(false) }
    var allPumpsSorted by remember { mutableStateOf<List<Pair<PumpEntity, Double>>>(emptyList()) }

    var isRefreshing by remember { mutableStateOf(false) }
    val pullToRefreshState = rememberPullToRefreshState()

    // Načtení dat
    fun loadData() {
        state = PriceRepository(context).loadState()
    }

    LaunchedEffect(Unit) {
        loadData()
        try {
            val settings = SettingsStore(context).settings.first()
            preferredNav = settings.preferredNavigation
        } catch (t: Throwable) {
            AppLogger.e("HomeScreen: chyba při načítání nastavení", t)
        }
    }

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
                        allPumpsSorted = sorted
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
                .windowInsetsPadding(WindowInsets.navigationBars)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                OnoHeader(showTitle = false)
            }

            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = {
                    scope.launch {
                        isRefreshing = true
                        onRefresh()
                        kotlinx.coroutines.delay(1500)
                        loadData()
                        isRefreshing = false
                    }
                },
                state = pullToRefreshState,
                modifier = Modifier.fillMaxSize()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SettingsCard {
                        NearestPumpSection(
                            nearestPump = nearestPump,
                            distanceKm = nearestDistanceKm,
                            hasLocation = hasLocation,
                            onShowAll = { showAllPumpsSheet = true },
                            onNavigate = { pump ->
                                val lat = pump.lat
                                val lng = pump.lng
                                if (lat != null && lng != null) {
                                    preferredNav.navigate(context, lat, lng, pump.name)
                                }
                            }
                        )
                    }

                    // --- CENY S DATEM VPRAVO ---
                    SettingsCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SectionTitle("Aktuální ceny:")
                            Text(
                                text = TankOnoScraper.formatPublished(s.current?.publishedAt) ?: "--",
                                color = OnoRed.copy(alpha = 0.7f),
                                fontSize = 11.sp,
                                textAlign = TextAlign.End
                            )
                        }

                        Spacer(Modifier.height(4.dp))

                        Product.entries.forEach { product ->
                            val cur = s.current?.entries?.get(product)
                            val old = s.previous?.entries?.get(product)
                            if (cur != null) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        product.displayName,
                                        color = OnoRed,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        PriceFormatter.format(old, Currency.CZK),
                                        color = OnoRed.copy(alpha = 0.6f),
                                        fontSize = 12.sp,
                                        textAlign = TextAlign.End,
                                        modifier = Modifier.width(60.dp)
                                    )
                                    Text(
                                        text = if (old != null && cur != null) {
                                            val oldVal = PriceFormatter.valueFor(old, Currency.CZK)
                                            val curVal = PriceFormatter.valueFor(cur, Currency.CZK)
                                            when {
                                                oldVal == null || curVal == null -> ""
                                                curVal > oldVal -> "▲"
                                                curVal < oldVal -> "▼"
                                                else -> "="
                                            }
                                        } else "",
                                        color = OnoRed,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.width(20.dp)
                                    )
                                    Text(
                                        PriceFormatter.format(cur, Currency.CZK),
                                        color = OnoRed,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        textAlign = TextAlign.End,
                                        modifier = Modifier.width(60.dp)
                                    )
                                }
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = onRefresh,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = OnoRed),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Aktualizovat data")
                    }

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

    if (showAllPumpsSheet) {
        AllPumpsSheet(
            pumps = allPumpsSorted,
            onDismiss = { showAllPumpsSheet = false },
            onPick = { pump ->
                showAllPumpsSheet = false
                val lat = pump.lat
                val lng = pump.lng
                if (lat != null && lng != null) {
                    preferredNav.navigate(context, lat, lng, pump.name)
                }
            }
        )
    }
}