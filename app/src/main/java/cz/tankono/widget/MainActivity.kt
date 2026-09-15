package cz.tankono.widget

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import cz.tankono.widget.data.db.PumpEntity
import cz.tankono.widget.data.model.Currency
import cz.tankono.widget.data.model.Product
import cz.tankono.widget.data.prefs.NavigationApp
import cz.tankono.widget.data.prefs.SettingsStore
import cz.tankono.widget.data.prefs.WidgetSettings
import cz.tankono.widget.data.remote.TankOnoScraper
import cz.tankono.widget.data.repo.PumpRefreshProgress
import cz.tankono.widget.data.repo.PumpRepository
import cz.tankono.widget.util.AppLogger
import cz.tankono.widget.util.LocationProvider
import cz.tankono.widget.util.UpdateChecker
import cz.tankono.widget.work.WorkScheduler
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


private val OnoYellow = Color(0xFFFFD600)
private val OnoRed    = Color(0xFFC92200)


class MainActivity : ComponentActivity() {

    private var configWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID

    // Stav oprávnění k poloze – mění se, když uživatel povolí
    private var locationGrantedState by androidx.compose.runtime.mutableStateOf(false)

    private val notifPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(
                this,
                "Notifikace o novém ceníku nebudou zobrazeny.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                      result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        AppLogger.i("Location permission granted=$granted")
        locationGrantedState = granted
        if (!granted) {
            Toast.makeText(
                this,
                "Bez polohy nelze určit nejbližší stanici.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        AppLogger.init(this)
        AppLogger.i("=== Aplikace spuštěna ===")

        configWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (configWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            setResult(RESULT_CANCELED, Intent().apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, configWidgetId)
            })
        }

        // Zjistit aktuální stav oprávnění
        locationGrantedState = hasLocationPermission()

        requestNotificationPermissionIfNeeded()
        requestLocationPermissionIfNeeded()

        setContent {
            MaterialTheme {
                SettingsScreen(
                    locationGranted = locationGrantedState,
                    onSave = { settings -> saveSettings(settings) },
                    onRefresh = { refreshNow() },
                    onExportLog = { exportLog() },
                    onOpenBatterySettings = { openBatterySettings() },
                    onInstallApk = { file -> installApk(file) },
                    isConfiguring = configWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID
                )
            }
        }

        WorkScheduler.schedule(this)
        WorkScheduler.schedulePumpSync(this)
    }

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun requestLocationPermissionIfNeeded() {
        if (!hasLocationPermission()) {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun openBatterySettings() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
            Toast.makeText(this, "Najdi Baterie → Neomezené", Toast.LENGTH_LONG).show()
        } catch (t: Throwable) {
            AppLogger.e("Nelze otevřít nastavení baterie", t)
        }
    }

    private fun installApk(file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                this, "$packageName.fileprovider", file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
            AppLogger.i("installApk: instalátor otevřen")
        } catch (t: Throwable) {
            AppLogger.e("installApk: chyba", t)
            Toast.makeText(this, "Nelze otevřít instalátor: ${t.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun saveSettings(settings: WidgetSettings) {
        lifecycleScope.launch {
            AppLogger.i("Ukládám nastavení: visible=${settings.visibleProducts.size}, " +
                    "currency=${settings.currency}, nav=${settings.preferredNavigation}")
            SettingsStore(this@MainActivity).save(settings)
            WorkScheduler.schedule(this@MainActivity)
            cz.tankono.widget.widget.TankOnoWidget.requestUpdate(this@MainActivity)

            if (configWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                val resultValue = Intent().apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, configWidgetId)
                }
                setResult(RESULT_OK, resultValue)
                WorkScheduler.runNow(this@MainActivity)
            }

            Toast.makeText(this@MainActivity, getString(R.string.saved_toast), Toast.LENGTH_SHORT).show()

            if (configWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                finish()
            }
        }
    }

    private fun refreshNow() {
        AppLogger.i("Manuální refresh – ceník + pumpy")
        Toast.makeText(this, getString(R.string.refreshing_toast), Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            try {
                val repo = cz.tankono.widget.data.repo.PriceRepository(this@MainActivity)
                repo.forceRefresh()
                cz.tankono.widget.widget.TankOnoWidget.requestUpdate(this@MainActivity)
            } catch (t: Throwable) {
                AppLogger.e("Chyba při forceRefresh ceníku", t)
            }
        }
        WorkScheduler.runPumpSyncNow(this)
    }

    private fun exportLog() {
        try {
            val logFile = AppLogger.createExportFile(this)

            val uri = FileProvider.getUriForFile(
                this, "$packageName.fileprovider", logFile
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Tank ONO widget – log")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Sdílet log"))
        } catch (t: Throwable) {
            AppLogger.e("Chyba při exportu logu", t)
        }
    }
}


// =============================================================================
// HLAVIČKA
// =============================================================================
@Composable
private fun OnoHeader(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth(0.9f)
            .height(40.dp)
            .background(OnoYellow)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .align(Alignment.BottomStart)
                .offset(y = (-8).dp)
                .background(OnoRed)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .align(Alignment.BottomStart)
                .offset(y = (-5).dp)
                .background(OnoRed)
        )
        Image(
            painter = painterResource(id = R.drawable.logo_text),
            contentDescription = "Tank ONO",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
            alignment = Alignment.CenterStart
        )
        Text(
            text = "Nastavení",
            color = OnoRed,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 8.dp, top = 4.dp)
        )
    }
}


// =============================================================================
// SETTINGS SCREEN
// =============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    locationGranted: Boolean,
    onSave: (WidgetSettings) -> Unit,
    onRefresh: () -> Unit,
    onExportLog: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onInstallApk: (File) -> Unit,
    isConfiguring: Boolean
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val settingsFlow = remember { SettingsStore(context).settings }
    val loaded by settingsFlow.collectAsStateWithLifecycle(initialValue = null)

    val state = remember { mutableStateOf<WidgetSettings?>(null) }

    var lastPublished by remember { mutableStateOf<String?>(null) }
    var lastFetched by remember { mutableStateOf<Long?>(null) }

    var updateInfo by remember { mutableStateOf<UpdateChecker.UpdateInfo?>(null) }
    var isChecking by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }

    // Pumpy / poloha / progress
    val pumpRepo = remember { PumpRepository(context) }
    val progress by pumpRepo.progress.collectAsStateWithLifecycle()

    var nearestPump by remember { mutableStateOf<PumpEntity?>(null) }
    var nearestDistanceKm by remember { mutableStateOf<Double?>(null) }
    var allPumpsSorted by remember { mutableStateOf<List<Pair<PumpEntity, Double>>>(emptyList()) }
    var userLocation by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var showAllPumpsSheet by remember { mutableStateOf(false) }
    var navTargetPump by remember { mutableStateOf<PumpEntity?>(null) }

    // Nainstalované navigační appky (jen pro UI)
    val installedNavApps = remember {
        NavigationApp.installed(context)
    }

    // Načtení uloženého stavu
    LaunchedEffect(loaded) {
        val loadedLocal = loaded ?: return@LaunchedEffect

        if (state.value == null) {
            state.value = loadedLocal

            // Pokud je nainstalovaná jen jedna navigační appka, auto-nastav ji
            if (installedNavApps.size == 1 &&
                loadedLocal.preferredNavigation == NavigationApp.SYSTEM
            ) {
                val autoNav = installedNavApps.first()
                AppLogger.i("Auto-nastavuji navigaci na $autoNav")
                state.value = loadedLocal.copy(preferredNavigation = autoNav)
                // Uložit na pozadí
                SettingsStore(context).save(loadedLocal.copy(preferredNavigation = autoNav))
            }
        }

        try {
            val repo = cz.tankono.widget.data.repo.PriceRepository(context)
            val s = repo.loadState()
            lastPublished = s.current?.publishedAt
            lastFetched = s.current?.fetchedAt
        } catch (_: Throwable) {}
    }

    // Auto-check aktualizací
    LaunchedEffect(Unit) {
        try {
            AppLogger.d("Auto-check: kontroluji aktualizace…")
            val info = UpdateChecker.checkForUpdate()
            if (info != null) {
                updateInfo = info
                AppLogger.i("Auto-check: nalezena novější verze ${info.version}")
            } else {
                AppLogger.d("Auto-check: máš nejnovější verzi")
            }
        } catch (t: Throwable) {
            AppLogger.e("Auto-check: chyba", t)
        }
    }

    // Načtení pump + polohy – přepočítá se, když se změní stav oprávnění
    LaunchedEffect(locationGranted) {
        try {
            val settingsStore = SettingsStore(context)

            // 1. Sync seznamu pump jen pokud uplynulo > 24 h
            val now = System.currentTimeMillis()
            val lastSync = settingsStore.getLastPumpSync()
            val dayMs = 24 * 60 * 60 * 1000L

            if (now - lastSync > dayMs) {
                AppLogger.i("SETTINGS: Sync pump (naposledy ${(now - lastSync) / 1000 / 60} min)")
                val sync = pumpRepo.syncWithWeb()
                if (!sync.failed) {
                    settingsStore.saveLastPumpSync(now)
                }
            } else {
                AppLogger.d("SETTINGS: Sync přeskočen (proběhl nedávno)")
            }

            // 2. GPS pro pumpy bez GPS
            val gpsCount = pumpRepo.refreshGpsForAllPumps()
            if (gpsCount > 0) {
                AppLogger.i("SETTINGS: Došti GPS pro $gpsCount pump")
            }

            // 3. Poloha uživatele (jen pokud máme oprávnění)
            if (locationGranted) {
                AppLogger.i("SETTINGS: Zjišťuji polohu…")
                val loc = LocationProvider.getCurrentLocation(context)
                if (loc != null) {
                    userLocation = loc
                    AppLogger.i("SETTINGS: Poloha = ${loc.first}, ${loc.second}")

                    val sorted = pumpRepo.getAllSortedByDistance(loc.first, loc.second)
                    allPumpsSorted = sorted
                    if (sorted.isNotEmpty()) {
                        nearestPump = sorted.first().first
                        nearestDistanceKm = sorted.first().second
                        AppLogger.i(
                            "SETTINGS: Nejbližší = ${sorted.first().first.name}, " +
                                    "${"%.1f".format(sorted.first().second)} km"
                        )
                    }
                } else {
                    AppLogger.w("SETTINGS: Polohu nelze získat")
                }
            } else {
                AppLogger.w("SETTINGS: Chybí oprávnění k poloze")
            }
        } catch (t: Throwable) {
            AppLogger.e("SETTINGS: chyba při načítání pump", t)
        } finally {
            pumpRepo.resetProgress()
        }
    }

    val s = state.value
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

                // ---- Poslední aktualizace + nejbližší ----
                SettingsCard {
                    SectionTitle("Poslední aktualizace")
                    Text(
                        "Ceník zveřejněn: " + (TankOnoScraper.formatPublished(lastPublished) ?: "--"),
                        color = OnoRed,
                        fontSize = 13.sp
                    )
                    Text(
                        "Widget aktualizován: " + (lastFetched?.let {
                            SimpleDateFormat("d.M.yyyy H:mm", Locale("cs", "CZ")).format(Date(it))
                        } ?: "--"),
                        color = OnoRed,
                        fontSize = 13.sp
                    )

                    // Progress bar
                    if (progress !is PumpRefreshProgress.Idle) {
                        val progressText = when (val p = progress) {
                            is PumpRefreshProgress.FetchingList -> "Stahuji seznam stanic…"
                            is PumpRefreshProgress.FetchingGps  -> "Stahuji GPS ${p.done}/${p.total}…"
                            is PumpRefreshProgress.Done         -> "Hotovo"
                            is PumpRefreshProgress.Error        -> "Chyba: ${p.message}"
                            PumpRefreshProgress.Idle            -> ""
                        }
                        val progressValue = when (val p = progress) {
                            is PumpRefreshProgress.FetchingList -> null
                            is PumpRefreshProgress.FetchingGps  ->
                                if (p.total > 0) p.done.toFloat() / p.total else null
                            is PumpRefreshProgress.Done         -> 1f
                            is PumpRefreshProgress.Error        -> null
                            PumpRefreshProgress.Idle            -> null
                        }

                        Spacer(Modifier.height(4.dp))
                        Text(progressText, color = OnoRed, fontSize = 11.sp)
                        Spacer(Modifier.height(2.dp))
                        if (progressValue != null) {
                            LinearProgressIndicator(
                                progress = { progressValue },
                                color = OnoRed,
                                trackColor = OnoRed.copy(alpha = 0.2f),
                                modifier = Modifier.fillMaxWidth().height(4.dp)
                            )
                        } else {
                            LinearProgressIndicator(
                                color = OnoRed,
                                trackColor = OnoRed.copy(alpha = 0.2f),
                                modifier = Modifier.fillMaxWidth().height(4.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(OnoRed.copy(alpha = 0.3f))
                    )
                    Spacer(Modifier.height(4.dp))

                    NearestPumpSection(
                        nearestPump = nearestPump,
                        distanceKm = nearestDistanceKm,
                        hasLocation = userLocation != null,
                        onShowAll = { showAllPumpsSheet = true },
                        onNavigate = { pump -> navTargetPump = pump }
                    )
                }

                // ---- Navigace (jen pokud 2+ nainstalované) ----
                if (installedNavApps.size >= 2) {
                    SettingsCard {
                        SectionTitle("Navigace")
                        Text(
                            "Preferovaná aplikace:",
                            color = OnoRed,
                            fontSize = 13.sp
                        )
                        NavigationRadioRow(
                            label = "Systém (vždy hlavní pro geo)",
                            selected = s.preferredNavigation == NavigationApp.SYSTEM,
                            onClick = {
                                state.value = s.copy(preferredNavigation = NavigationApp.SYSTEM)
                            }
                        )
                        installedNavApps.forEach { app ->
                            NavigationRadioRow(
                                label = app.displayName,
                                selected = s.preferredNavigation == app,
                                onClick = {
                                    state.value = s.copy(preferredNavigation = app)
                                }
                            )
                        }
                    }
                }

                // ---- Produkty ----
                SettingsCard {
                    SectionTitle("Zobrazované produkty")
                    ProductGroup("Paliva",
                        Product.entries.filter { it.kind == Product.Kind.FUEL }, s, state)
                    ProductGroup("Ostatní",
                        Product.entries.filter { it.kind == Product.Kind.OTHER }, s, state)
                    ProductGroup("Směnárna",
                        Product.entries.filter { it.kind == Product.Kind.EXCHANGE }, s, state)
                    if (s.visibleProducts.isEmpty()) {
                        Text("Musíte vybrat alespoň jeden produkt.", color = OnoRed, fontSize = 12.sp)
                    }
                }

                // ---- Krátké názvy ----
                SettingsCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Krátké názvy produktů:",
                            color = OnoRed,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = s.useShortNames,
                            onCheckedChange = { checked ->
                                state.value = s.copy(useShortNames = checked)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = OnoYellow,
                                checkedTrackColor = OnoRed,
                                checkedBorderColor = OnoRed,
                                uncheckedThumbColor = OnoRed,
                                uncheckedTrackColor = Color.White,
                                uncheckedBorderColor = OnoRed
                            )
                        )
                    }
                }

                // ---- Měna ----
                SettingsCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Zobrazování ceny:",
                            color = OnoRed,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CurrencyButton("Kč", s.currency == Currency.CZK) {
                                state.value = s.copy(currency = Currency.CZK)
                            }
                            CurrencyButton("€ur", s.currency == Currency.EUR) {
                                state.value = s.copy(currency = Currency.EUR)
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                }

                // ---- Špička ----
                SettingsCard {
                    SectionTitle("Špička (aktualizace cen)")
                    TwoSteppersRow(
                        value1 = formatTime(s.peakStartMinutes),
                        value2 = formatTime(s.peakEndMinutes),
                        onMinus1 = {
                            val newVal = (s.peakStartMinutes - 15).coerceAtLeast(0)
                            state.value = s.copy(peakStartMinutes = newVal)
                        },
                        onPlus1 = {
                            val newVal = (s.peakStartMinutes + 15).coerceAtMost(1440)
                            state.value = s.copy(peakStartMinutes = newVal)
                        },
                        onMinus2 = {
                            val newVal = (s.peakEndMinutes - 15).coerceAtLeast(0)
                            state.value = s.copy(peakEndMinutes = newVal)
                        },
                        onPlus2 = {
                            val newVal = (s.peakEndMinutes + 15).coerceAtMost(1440)
                            state.value = s.copy(peakEndMinutes = newVal)
                        },
                        canMinus1 = s.peakStartMinutes > 0,
                        canPlus1 = s.peakStartMinutes < 1440,
                        canMinus2 = s.peakEndMinutes > 0,
                        canPlus2 = s.peakEndMinutes < 1440,
                        onMin1 = { state.value = s.copy(peakStartMinutes = 0) },
                        onMax1 = { state.value = s.copy(peakStartMinutes = 1440) },
                        onMin2 = { state.value = s.copy(peakEndMinutes = 0) },
                        onMax2 = { state.value = s.copy(peakEndMinutes = 1440) }
                    )
                }

                // ---- Interval ----
                SettingsCard {
                    SectionTitle("Interval aktualizací špička/mimo")
                    TwoSteppersRow(
                        value1 = s.intervalPeakMin.toString(),
                        value2 = s.intervalOffPeakMin.toString(),
                        onMinus1 = {
                            val newVal = (s.intervalPeakMin - 5).coerceAtLeast(15)
                            state.value = s.copy(intervalPeakMin = newVal)
                        },
                        onPlus1 = {
                            val newVal = (s.intervalPeakMin + 5).coerceAtMost(360)
                            state.value = s.copy(intervalPeakMin = newVal)
                        },
                        onMinus2 = {
                            val newVal = (s.intervalOffPeakMin - 5).coerceAtLeast(15)
                            state.value = s.copy(intervalOffPeakMin = newVal)
                        },
                        onPlus2 = {
                            val newVal = (s.intervalOffPeakMin + 5).coerceAtMost(360)
                            state.value = s.copy(intervalOffPeakMin = newVal)
                        },
                        canMinus1 = s.intervalPeakMin > 15,
                        canPlus1 = s.intervalPeakMin < 360,
                        canMinus2 = s.intervalOffPeakMin > 15,
                        canPlus2 = s.intervalOffPeakMin < 360,
                        onMin1 = { state.value = s.copy(intervalPeakMin = 15) },
                        onMax1 = { state.value = s.copy(intervalPeakMin = 360) },
                        onMin2 = { state.value = s.copy(intervalOffPeakMin = 15) },
                        onMax2 = { state.value = s.copy(intervalOffPeakMin = 360) }
                    )
                }

                // ---- Text widgetu ----
                SettingsCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                "Text widgetu:",
                                color = OnoRed,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 8.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            NumberStepper(
                                value = s.fontSizeSp.toString(),
                                onMinus = {
                                    val newVal = (s.fontSizeSp - 1).coerceAtLeast(10)
                                    state.value = s.copy(fontSizeSp = newVal)
                                },
                                onPlus = {
                                    val newVal = (s.fontSizeSp + 1).coerceAtMost(30)
                                    state.value = s.copy(fontSizeSp = newVal)
                                },
                                canMinus = s.fontSizeSp > 10,
                                canPlus = s.fontSizeSp < 30,
                                onMin = { state.value = s.copy(fontSizeSp = 10) },
                                onMax = { state.value = s.copy(fontSizeSp = 30) }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))

                Button(
                    onClick = { onSave(s) },
                    enabled = s.visibleProducts.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = OnoRed,
                        contentColor = OnoYellow
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isConfiguring) "Přidat widget" else "Uložit nastavení widgetu")
                }

                OutlinedButton(
                    onClick = onRefresh,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = OnoRed),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("↻  Aktualizovat data")
                }

                OutlinedButton(
                    onClick = {
                        isChecking = true
                        updateInfo = null
                        scope.launch {
                            val info = UpdateChecker.checkForUpdate()
                            updateInfo = info
                            isChecking = false
                            if (info == null) {
                                Toast.makeText(context, "Máš nejnovější verzi", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = OnoRed),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isChecking && !isDownloading
                ) {
                    Text(if (isChecking) "Kontroluji…" else "Zkontrolovat aktualizace")
                }

                updateInfo?.let { info ->
                    Button(
                        onClick = {
                            isDownloading = true
                            scope.launch {
                                Toast.makeText(context, "Stahuji…", Toast.LENGTH_SHORT).show()
                                val file = UpdateChecker.downloadApk(context, info.downloadApiUrl)
                                isDownloading = false
                                if (file != null) {
                                    Toast.makeText(context, "Staženo, otevírám instalátor…", Toast.LENGTH_SHORT).show()
                                    onInstallApk(file)
                                } else {
                                    Toast.makeText(context, "Stahování selhalo", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = OnoRed,
                            contentColor = OnoYellow
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isDownloading
                    ) {
                        Text(if (isDownloading) "Stahuji…" else "Stáhnout ${info.version}")
                    }
                }

                OutlinedButton(
                    onClick = onOpenBatterySettings,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = OnoRed),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Nastavení baterie")
                }

                OutlinedButton(
                    onClick = onExportLog,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = OnoRed),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Exportovat log")
                }

                Spacer(Modifier.height(16.dp))

                Text(
                    "Tank ONO widget v${BuildConfig.VERSION_NAME} · autor: birgis",
                    color = OnoRed.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(8.dp))
            }
        }
    }

    if (showAllPumpsSheet) {
        AllPumpsSheet(
            pumps = allPumpsSorted,
            onDismiss = { showAllPumpsSheet = false },
            onPick = { pump ->
                showAllPumpsSheet = false
                navTargetPump = pump
            }
        )
    }

    // Rovnou navigovat (bez dialogu) – pokud je vybraná appka
    navTargetPump?.let { pump ->
        LaunchedEffect(pump) {
            val lat = pump.lat
            val lng = pump.lng
            if (lat != null && lng != null) {
                s.preferredNavigation.navigate(context, lat, lng, pump.name)
            }
            navTargetPump = null
        }
    }
}


// =============================================================================
// NAVIGACE – radio řádek
// =============================================================================
@Composable
private fun NavigationRadioRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(
                selectedColor = OnoRed,
                unselectedColor = OnoRed
            )
        )
        Spacer(Modifier.width(4.dp))
        Text(
            label,
            color = OnoRed,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}


// =============================================================================
// NEJBLIŽŠÍ STANICE
// =============================================================================
@Composable
private fun NearestPumpSection(
    nearestPump: PumpEntity?,
    distanceKm: Double?,
    hasLocation: Boolean,
    onShowAll: () -> Unit,
    onNavigate: (PumpEntity) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        when {
            nearestPump != null && distanceKm != null -> {
                // Layout podle zadání:
                // Row 1: Nejbližší:  |  Adresa           | [M]
                // Row 2:             |  2,4 km            | [S]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    // Levý sloupec – labely ("Nejbližší:")
                    Text(
                        "Nejbližší:",
                        color = OnoRed,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.width(90.dp)
                    )

                    // Prostřední sloupec – hodnoty (adresa, vzdálenost)
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            nearestPump.name,
                            color = OnoRed,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            formatDistanceKm(distanceKm),
                            color = OnoRed,
                            fontSize = 13.sp
                        )
                    }

                    // Pravý sloupec – tlačítka M/S svisle
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        SquareButton("M") { onNavigate(nearestPump) }
                        SquareButton("S") { onShowAll() }
                    }
                }
            }
            !hasLocation -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Nejbližší:",
                        color = OnoRed,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.width(90.dp)
                    )
                    Text(
                        "Poloha není dostupná – povol přístup k poloze.",
                        color = OnoRed,
                        fontSize = 12.sp
                    )
                }
            }
            else -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Nejbližší:",
                        color = OnoRed,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.width(90.dp)
                    )
                    Text(
                        "Zjišťuji…",
                        color = OnoRed,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}


@Composable
private fun SquareButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .border(2.dp, OnoRed, RoundedCornerShape(8.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = OnoRed,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
    }
}


// =============================================================================
// BOTTOM SHEET
// =============================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AllPumpsSheet(
    pumps: List<Pair<PumpEntity, Double>>,
    onDismiss: () -> Unit,
    onPick: (PumpEntity) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = OnoYellow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                "Všechny stanice",
                color = OnoRed,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(OnoRed)
            )
            Spacer(Modifier.height(12.dp))

            if (pumps.isEmpty()) {
                Text(
                    "Žádné stanice s GPS. Zkus později.",
                    color = OnoRed,
                    fontSize = 14.sp
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(pumps, key = { it.first.id }) { (pump, dist) ->
                        Surface(
                            color = Color.White,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, OnoRed.copy(alpha = 0.4f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(pump) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    pump.name,
                                    color = OnoRed,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    formatDistanceKm(dist),
                                    color = OnoRed,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}


// =============================================================================
// POMOCNÉ
// =============================================================================
private fun formatDistanceKm(km: Double): String {
    return if (km < 10.0) {
        "%.1f km".format(km).replace('.', ',')
    } else {
        "%.0f km".format(km)
    }
}


// =============================================================================
// SPOLEČNÉ KOMPONENTY
// =============================================================================
@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(2.dp, OnoRed),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text = text, color = OnoRed, fontSize = 14.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun TwoSteppersRow(
    value1: String,
    value2: String,
    onMinus1: () -> Unit,
    onPlus1: () -> Unit,
    onMinus2: () -> Unit,
    onPlus2: () -> Unit,
    canMinus1: Boolean = true,
    canPlus1: Boolean = true,
    canMinus2: Boolean = true,
    canPlus2: Boolean = true,
    onMin1: (() -> Unit)? = null,
    onMax1: (() -> Unit)? = null,
    onMin2: (() -> Unit)? = null,
    onMax2: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            NumberStepper(
                value = value1,
                onMinus = onMinus1,
                onPlus = onPlus1,
                canMinus = canMinus1,
                canPlus = canPlus1,
                onMin = onMin1,
                onMax = onMax1
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            NumberStepper(
                value = value2,
                onMinus = onMinus2,
                onPlus = onPlus2,
                canMinus = canMinus2,
                canPlus = canPlus2,
                onMin = onMin2,
                onMax = onMax2
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NumberStepper(
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    canMinus: Boolean = true,
    canPlus: Boolean = true,
    onMin: (() -> Unit)? = null,
    onMax: (() -> Unit)? = null
) {
    val buttonSize = 40.dp

    BoxWithConstraints {
        val availableWidth = maxWidth
        val buttonsWidth = buttonSize * 2
        val remaining = availableWidth - buttonsWidth

        val gap = when {
            remaining >= 120.dp -> 4.dp
            remaining >= 100.dp -> 3.dp
            remaining >= 80.dp  -> 2.dp
            else                -> 1.dp
        }

        val valueWidth = (remaining - (gap * 2)).coerceAtLeast(40.dp)

        val valueFontSize = when {
            valueWidth >= 70.dp -> 18.sp
            valueWidth >= 60.dp -> 16.sp
            valueWidth >= 50.dp -> 14.sp
            else                -> 12.sp
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(gap)
        ) {
            Box(
                modifier = Modifier
                    .size(buttonSize)
                    .border(2.dp, OnoRed, RoundedCornerShape(8.dp))
                    .then(
                        if (canMinus && onMin != null) {
                            Modifier.combinedClickable(onClick = onMinus, onLongClick = onMin)
                        } else if (canMinus) {
                            Modifier.clickable { onMinus() }
                        } else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (canMinus) {
                    Text("−", color = OnoRed, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }
            }

            Box(
                modifier = Modifier.width(valueWidth).height(buttonSize),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = value,
                    color = OnoRed,
                    fontSize = valueFontSize,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false
                )
            }

            Box(
                modifier = Modifier
                    .size(buttonSize)
                    .border(2.dp, OnoRed, RoundedCornerShape(8.dp))
                    .then(
                        if (canPlus && onMax != null) {
                            Modifier.combinedClickable(onClick = onPlus, onLongClick = onMax)
                        } else if (canPlus) {
                            Modifier.clickable { onPlus() }
                        } else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (canPlus) {
                    Text("+", color = OnoRed, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ProductGroup(
    title: String,
    products: List<Product>,
    settings: WidgetSettings,
    state: MutableState<WidgetSettings?>
) {
    Column {
        Text(title, color = OnoRed, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Box(
            modifier = Modifier
                .padding(top = 2.dp, bottom = 6.dp)
                .fillMaxWidth(0.4f)
                .height(1.dp)
                .background(OnoRed)
        )
        products.forEach { product ->
            val checked = product in settings.visibleProducts
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
            ) {
                Text(product.displayName, color = OnoRed, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Switch(
                    checked = checked,
                    onCheckedChange = { isChecked ->
                        val newSet = if (isChecked) settings.visibleProducts + product
                                     else settings.visibleProducts - product
                        state.value = settings.copy(visibleProducts = newSet)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = OnoYellow,
                        checkedTrackColor = OnoRed,
                        checkedBorderColor = OnoRed,
                        uncheckedThumbColor = OnoRed,
                        uncheckedTrackColor = Color.White,
                        uncheckedBorderColor = OnoRed
                    )
                )
            }
        }
    }
}

@Composable
private fun CurrencyButton(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) OnoRed else Color.White
    val fg = if (selected) OnoYellow else OnoRed
    Box(
        modifier = Modifier
            .width(70.dp)
            .height(44.dp)
            .border(2.dp, OnoRed, RoundedCornerShape(8.dp))
            .background(bg, RoundedCornerShape(8.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = fg, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

private fun formatTime(minutes: Int): String {
    if (minutes >= 1440) return "24:00"
    val h = (minutes / 60) % 24
    val m = minutes % 60
    return "%d:%02d".format(h, m)
}