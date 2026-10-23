package cz.tankono.widget

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
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
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextDecoration
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import cz.tankono.widget.data.db.PumpEntity
import cz.tankono.widget.data.model.Currency
import cz.tankono.widget.data.model.Product
import cz.tankono.widget.data.prefs.NavigationApp
import cz.tankono.widget.data.prefs.SettingsStore
import cz.tankono.widget.data.prefs.ThemeMode
import cz.tankono.widget.data.prefs.WidgetSettings
import cz.tankono.widget.data.remote.TankOnoScraper
import cz.tankono.widget.data.repo.PumpRefreshProgress
import cz.tankono.widget.data.repo.PumpRepository
import cz.tankono.widget.util.AppLogger
import cz.tankono.widget.util.InstallReporter
import cz.tankono.widget.util.LocationProvider
import cz.tankono.widget.util.UpdateChecker
import cz.tankono.widget.work.WorkScheduler
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.app.Activity
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

private const val GITHUB_URL = "https://github.com/birgis-cze/ONOupdate"

private val OnoYellow = Color(0xFFFFD600)
private val OnoRed    = Color(0xFFC92200)

enum class UpdateState {
    IDLE, CHECKING, DOWNLOADING, READY_TO_INSTALL, UP_TO_DATE
}

/**
 * Ořeže "ČS " prefix z názvu stanice pro zobrazení.
 * V DB zůstává plný název.
 */
private fun pumpDisplayName(fullName: String): String =
    fullName.removePrefix("ČS ").trim()

/**
 * Zkontroluje cache, zda tam neleží stažený balíček.
 * Pokud najde balíček, který má stejnou verzi jako aktuální aplikace, smaže ho (již proběhla instalace).
 * Pokud najde balíček s jinou (novější) verzí, vrátí ho jako připravený k instalaci.
 */
fun checkPendingUpdate(context: Context): File? {
    val files = context.cacheDir.listFiles { file -> file.name.startsWith("update_v") && file.name.endsWith(".apk") } ?: return null
    
    for (file in files) {
        // OČIŠTĚNÍ: Odstraníme "update_v" a případné "v" navíc
        val fileVersion = file.name.removePrefix("update_v").removeSuffix(".apk").removePrefix("v")
        
        if (fileVersion == BuildConfig.VERSION_NAME) {
            file.delete()
            AppLogger.i("Úklid: Smazán úspěšně nainstalovaný balíček $fileVersion")
        } else {
            AppLogger.i("Nalezen čekající balíček k instalaci: $fileVersion")
            return file
        }
    }
    return null
}

/**
 * Zavibruje 2 sekundy (pro potvrzení tajné akce).
 */
private fun vibrateLong(context: Context) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    if (vibrator.hasVibrator()) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(2000, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(2000)
        }
    }
}

class MainActivity : ComponentActivity() {

    private var configWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID
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

        // Vynutíme tmavé (černé) ikony v horní stavové liště i ve spodním navigačním panelu
        val windowInsetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
        
        windowInsetsController.isAppearanceLightStatusBars = true
        windowInsetsController.isAppearanceLightNavigationBars = true

        // Pročistíme cache od starých/nainstalovaných balíčků
        checkPendingUpdate(this)

        AppLogger.init(this)
        AppLogger.i("=== Aplikace spuštěna ===")

        InstallReporter.reportIfNeeded(this)

        // Startup refresh ceníku
        lifecycleScope.launch {
            try {
                AppLogger.i("Startup refresh: kontroluji ceník…")
                val repo = cz.tankono.widget.data.repo.PriceRepository(this@MainActivity)
                val changed = repo.refresh()
                if (changed) {
                    AppLogger.i("Startup refresh: ceník aktualizován → překresluji widget")
                    cz.tankono.widget.widget.TankOnoWidget.requestUpdate(this@MainActivity)
                } else {
                    AppLogger.d("Startup refresh: ceník beze změny")
                }
            } catch (t: Throwable) {
                AppLogger.e("Startup refresh: chyba", t)
            }
        }

        configWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (configWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            setResult(RESULT_CANCELED, Intent().apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, configWidgetId)
            })
        }

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
                    onRequestLocation = { requestLocationPermissionIfNeeded() },
                    onRequestNotification = { requestNotificationPermissionIfNeeded() },
                    onInstallApk = { file -> installApk(file) },
                    isConfiguring = configWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID
                )
            }
        }

        WorkScheduler.schedule(this)
        WorkScheduler.schedulePumpSync(this)
        WorkScheduler.scheduleUpdateCheck(this) // <-- NOVÉ
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) {
            AppLogger.i("MainActivity onStop – překresluji widget")
            cz.tankono.widget.widget.TankOnoWidget.requestUpdate(this)
        }
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

    private fun installApk(file: File): Boolean {
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
            return true
        } catch (t: Throwable) {
            AppLogger.e("installApk: chyba", t)
            Toast.makeText(this, "Nelze otevřít instalátor: ${t.message}", Toast.LENGTH_LONG).show()
            file.delete() // Smažeme špatný balíček
            return false
        }
    }

    private fun saveSettings(settings: WidgetSettings) {
        lifecycleScope.launch {
            AppLogger.i("Ukládám nastavení: visible=${settings.visibleProducts.size}, " +
                    "currency=${settings.currency}, nav=${settings.preferredNavigation}")

            SettingsStore(this@MainActivity).save(settings)

            if (!settings.showNearestPump) {
                SettingsStore(this@MainActivity).clearCachedLocation()
            }

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
    onRequestLocation: () -> Unit,
    onRequestNotification: () -> Unit,
    onInstallApk: (File) -> Boolean,
    isConfiguring: Boolean
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val settingsFlow = remember { SettingsStore(context).settings }
    val loaded by settingsFlow.collectAsStateWithLifecycle(initialValue = null)

    val state = remember { mutableStateOf<WidgetSettings?>(null) }

    var lastPublished by remember { mutableStateOf<String?>(null) }
    var lastFetched by remember { mutableStateOf<Long?>(null) }

    var installCount by remember { mutableStateOf<Int?>(null) }
    var installCountLoading by remember { mutableStateOf(false) }   
    var deviceHash by remember { mutableStateOf<String?>(null) }

    // Stavy pro chytré tlačítko
    var updateState by remember { mutableStateOf(UpdateState.IDLE) }
    var updateInfo by remember { mutableStateOf<UpdateChecker.UpdateInfo?>(null) }
    var downloadedFile by remember { mutableStateOf<File?>(null) }
    var pendingVersion by remember { mutableStateOf<String?>(null) }
    var downloadProgress by remember { mutableIntStateOf(0) }

    // Stavy pro kontrolu systému (Poloha, Notifikace, Baterie)
    var hasLoc by remember { mutableStateOf(false) }
    var hasNotif by remember { mutableStateOf(false) }
    var batteryOk by remember { mutableStateOf(false) }

    // Pumpy / poloha / progress
    val pumpRepo = remember { PumpRepository(context) }
    val progress by pumpRepo.progress.collectAsStateWithLifecycle()

    var nearestPump by remember { mutableStateOf<PumpEntity?>(null) }
    var nearestDistanceKm by remember { mutableStateOf<Double?>(null) }
    var allPumpsSorted by remember { mutableStateOf<List<Pair<PumpEntity, Double>>>(emptyList()) }
    var userLocation by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var showAllPumpsSheet by remember { mutableStateOf(false) }
    var navTargetPump by remember { mutableStateOf<PumpEntity?>(null) }
    var showQrDialog by remember { mutableStateOf(false) }

    // Tajné odemknutí exportu logu
    var footerTapCount by remember { mutableIntStateOf(0) }
    var showLogFlash by remember { mutableStateOf(false) }

    // Nainstalované navigační appky
    val installedNavApps = remember {
        NavigationApp.installed(context)
    }

    // --- ŽIVÁ KONTROLA SYSTÉMOVÝCH NASTAVENÍ ---
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasLoc = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                         ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                
                hasNotif = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                } else true
                
                val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                batteryOk = pm.isIgnoringBatteryOptimizations(context.packageName)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // --- OKAMŽITÉ NAČTENÍ Z CACHE (aby text "Nedostupná poloha" neproblikával) ---
    LaunchedEffect(Unit) {
        val cachedLoc = SettingsStore(context).getCachedLocation()
        if (cachedLoc != null && locationGranted) {
            userLocation = cachedLoc // Tím se hasLocation v NearestPumpSection změní na true
            val sorted = pumpRepo.getAllSortedByDistance(cachedLoc.first, cachedLoc.second)
            if (sorted.isNotEmpty()) {
                nearestPump = sorted.first().first
                nearestDistanceKm = sorted.first().second
            }
        }
    }

    // Načtení uloženého stavu
    LaunchedEffect(loaded) {
        val loadedLocal = loaded ?: return@LaunchedEffect

        if (state.value == null) {
            state.value = loadedLocal

            if (installedNavApps.size == 1 &&
                loadedLocal.preferredNavigation == NavigationApp.SYSTEM
            ) {
                val autoNav = installedNavApps.first()
                AppLogger.i("Auto-nastavuji navigaci na $autoNav")
                state.value = loadedLocal.copy(preferredNavigation = autoNav)
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

    // Zkontrolujeme, zda nezůstal viset stažený balíček z minula
    LaunchedEffect(Unit) {
        val pendingFile = checkPendingUpdate(context)
        if (pendingFile != null) {
            downloadedFile = pendingFile
            pendingVersion = pendingFile.name.removePrefix("update_v").removeSuffix(".apk").removePrefix("v")
            updateState = UpdateState.READY_TO_INSTALL
        }
    }

    // Tichá kontrola aktualizací na pozadí při otevření nastavení
    LaunchedEffect(Unit) {
        // Spustíme kontrolu POUZE pokud zrovna nemáme stažený balíček k instalaci
        // (aby nedošlo k přepsání stavu READY_TO_INSTALL)
        if (downloadedFile == null && updateState == UpdateState.IDLE) {
            try {
                AppLogger.d("Auto-check: tichá kontrola aktualizací…")
                val info = UpdateChecker.checkForUpdate()
                if (info != null) {
                    updateInfo = info
                    updateState = UpdateState.IDLE // Zůstává IDLE, ale tlačítko se změní podle updateInfo
                    AppLogger.i("Auto-check: nalezena novější verze ${info.version}")
                } else {
                    AppLogger.d("Auto-check: máš nejnovější verzi")
                }
            } catch (t: Throwable) {
                AppLogger.e("Auto-check: chyba", t)
            }
        }
    }

    // Načtení počtu evidovaných zařízení + hashe tohoto zařízení
    LaunchedEffect(Unit) {
        deviceHash = InstallReporter.getDeviceHash(context)
        installCountLoading = true
        installCount = InstallReporter.fetchTotalCount()
        installCountLoading = false
    }

    // ---- Auto-save s debounce 500 ms ----
    LaunchedEffect(state.value) {
        val current = state.value ?: return@LaunchedEffect
        if (isConfiguring) return@LaunchedEffect

        val loadedLocal = loaded ?: return@LaunchedEffect
        if (current == loadedLocal) return@LaunchedEffect

        delay(500)

        AppLogger.i("Auto-save: ukládám nastavení (font=${current.fontSizeSp}, nav=${current.preferredNavigation})")
        SettingsStore(context).save(current)
        WorkScheduler.schedule(context)
    }

    // Načtení pump + polohy
    LaunchedEffect(locationGranted) {
        try {
            val settingsStore = SettingsStore(context)
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

            val gpsCount = pumpRepo.refreshGpsForAllPumps()
            if (gpsCount > 0) {
                AppLogger.i("SETTINGS: Došti GPS pro $gpsCount pump")
            }

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

    // Funkce, která provede celý cyklus najednou
    fun startUpdateProcess() {
        scope.launch {
            updateState = UpdateState.CHECKING
            val info = UpdateChecker.checkForUpdate()
            
            if (info == null) {
                updateState = UpdateState.UP_TO_DATE
                Toast.makeText(context, "Máš nejnovější verzi", Toast.LENGTH_SHORT).show()
                return@launch
            }
            
            updateInfo = info
            updateState = UpdateState.DOWNLOADING
            downloadProgress = 0 // Reset
            
            // PŘEDÁNÍ CALLBACKU
            val file = UpdateChecker.downloadApk(
                context = context, 
                apiUrl = info.downloadApiUrl, 
                versionName = info.version,
                onProgress = { progress -> downloadProgress = progress }
            )
            
            if (file == null) {
                updateState = UpdateState.IDLE
                Toast.makeText(context, "Stahování selhalo.", Toast.LENGTH_LONG).show()
                return@launch
            }
            
            downloadedFile = file
            pendingVersion = info.version.removePrefix("v")
            updateState = UpdateState.READY_TO_INSTALL
            
            val success = onInstallApk(file)
            if (!success) {
                downloadedFile = null
                pendingVersion = null
                updateState = UpdateState.IDLE
            }
        }
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
                    SectionTitle("Poslední aktualizace:")
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

                    if (progress is PumpRefreshProgress.FetchingList) {
                        Spacer(Modifier.height(1.dp))
                        Text("Stahuji seznam stanic…", color = OnoRed, fontSize = 11.sp)
                        Spacer(Modifier.height(2.dp))
                        LinearProgressIndicator(
                            color = OnoRed,
                            trackColor = OnoRed.copy(alpha = 0.2f),
                            modifier = Modifier.fillMaxWidth().height(4.dp)
                        )
                    }

                    Spacer(Modifier.height(0.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(OnoRed.copy(alpha = 0.3f))
                    )
                    Spacer(Modifier.height(0.dp))

                    NearestPumpSection(
                        nearestPump = nearestPump,
                        distanceKm = nearestDistanceKm,
                        hasLocation = userLocation != null,
                        onShowAll = { showAllPumpsSheet = true },
                        onNavigate = { pump -> navTargetPump = pump }
                    )
                }

                // --- DYNAMICKÁ KARTA PRO SYSTÉMOVÁ NASTAVENÍ ---
                if (!hasLoc || !hasNotif || !batteryOk) {
                    SettingsCard {
                        SectionTitle("Jsou potřeba povolení:")
                        if (!hasLoc) {
                            OutlinedButton(
                                onClick = onRequestLocation,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = OnoRed),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Poloha")
                            }
                        }
                        if (!hasNotif) {
                            OutlinedButton(
                                onClick = {
                                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                    }
                                    context.startActivity(intent)
                                },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = OnoRed),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Notifikace")
                            }
                        }
                        if (!batteryOk) {
                            OutlinedButton(
                                onClick = onOpenBatterySettings,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = OnoRed),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Napájení (bez omezení)")
                            }
                        }
                    }
                }

                // ---- Navigace ----
                SettingsCard {
                    if (installedNavApps.size >= 2) {
                        NavigationRow(
                            current = s.preferredNavigation,
                            installedApps = installedNavApps,
                            onSelect = { nav ->
                                state.value = s.copy(preferredNavigation = nav)
                            }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Zobrazit nejbližší na widgetu:",
                            color = OnoRed,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = s.showNearestPump,
                            onCheckedChange = { checked ->
                                state.value = s.copy(showNearestPump = checked)
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

                // ---- Produkty ----
                SettingsCard {
                    SectionTitle("Zobrazované produkty")
                    ProductGroup("Paliva:",
                        Product.entries.filter { it.kind == Product.Kind.FUEL }, s, state)
                    ProductGroup("Ostatní:",
                        Product.entries.filter { it.kind == Product.Kind.OTHER }, s, state)
                    ProductGroup("Směnárna:",
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
                                "Velikost textu:",
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

                // ---- Vzhled (Tmavý režim) ----
                SettingsCard {
                    var themeExpanded by remember { mutableStateOf(false) }

                    Box(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Vzhled widgetu: ",
                                    color = OnoRed,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    s.themeMode.displayName,
                                    color = OnoRed,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Normal
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .border(2.dp, OnoRed, RoundedCornerShape(8.dp))
                                    .clickable { themeExpanded = true },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "▼",
                                    color = OnoRed,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = themeExpanded,
                            onDismissRequest = { themeExpanded = false },
                            modifier = Modifier
                                .background(OnoYellow)
                                .border(2.dp, OnoRed)
                        ) {
                            ThemeMode.entries.forEach { mode ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            mode.displayName,
                                            color = OnoRed,
                                            fontSize = 14.sp,
                                            fontWeight = if (s.themeMode == mode) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    onClick = {
                                        state.value = s.copy(themeMode = mode)
                                        themeExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))

                if (isConfiguring) {
                    Button(
                        onClick = { onSave(s) },
                        enabled = s.visibleProducts.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = OnoRed,
                            contentColor = OnoYellow
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Přidat widget")
                    }
                }

                OutlinedButton(
                    onClick = {
                        onRefresh()
                        scope.launch {
                            delay(1500)
                            try {
                                val repo = cz.tankono.widget.data.repo.PriceRepository(context)
                                val s = repo.loadState()
                                lastPublished = s.current?.publishedAt
                                lastFetched = s.current?.fetchedAt
                            } catch (_: Throwable) {}
                        }
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = OnoRed),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Aktualizovat data")
                }

                // Zjistíme, zda je k dispozici nová verze (pro zvýraznění tlačítka)
                val isUpdateAvailable = updateState == UpdateState.IDLE && updateInfo != null

                // ---- Chytré tlačítko pro aktualizaci s progress barem ----
                OutlinedButton(
                    onClick = {
                        when (updateState) {
                            UpdateState.IDLE -> {
                                if (updateInfo != null) {
                                    // Už známe novou verzi (z auto-checku) → rovnou stáhnout
                                    scope.launch {
                                        updateState = UpdateState.DOWNLOADING
                                        downloadProgress = 0
                                        val file = UpdateChecker.downloadApk(
                                            context = context,
                                            apiUrl = updateInfo!!.downloadApiUrl,
                                            versionName = updateInfo!!.version,
                                            onProgress = { progress -> downloadProgress = progress }
                                        )
                                        if (file == null) {
                                            updateState = UpdateState.IDLE
                                            Toast.makeText(context, "Stahování selhalo.", Toast.LENGTH_LONG).show()
                                            return@launch
                                        }
                                        downloadedFile = file
                                        pendingVersion = updateInfo!!.version.removePrefix("v")
                                        updateState = UpdateState.READY_TO_INSTALL
                                        val success = onInstallApk(file)
                                        if (!success) {
                                            downloadedFile = null
                                            pendingVersion = null
                                            updateState = UpdateState.IDLE
                                        }
                                    }
                                } else {
                                    // Nic neznáme → kompletní proces (kontrola + stažení + instalace)
                                    startUpdateProcess()
                                }
                            }
                            UpdateState.UP_TO_DATE -> startUpdateProcess()
                            UpdateState.READY_TO_INSTALL -> {
                                if (downloadedFile != null) {
                                    val success = onInstallApk(downloadedFile!!)
                                    if (!success) {
                                        downloadedFile = null
                                        pendingVersion = null
                                        updateState = UpdateState.IDLE
                                    }
                                } else {
                                    updateState = UpdateState.IDLE
                                }
                            }
                            UpdateState.CHECKING, UpdateState.DOWNLOADING -> {}
                        }
                    },
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (isUpdateAvailable) OnoRed else Color.Transparent,
                        contentColor = if (isUpdateAvailable) OnoYellow else OnoRed
                    ),
                    // ZDE BYL PROBLÉM: Odstraněn parametr 'border', aby tlačítko vypadalo naprosto stejně jako ostatní
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    contentPadding = PaddingValues(0.dp),
                    enabled = updateState != UpdateState.CHECKING && updateState != UpdateState.DOWNLOADING
                ) {
                    if (updateState == UpdateState.DOWNLOADING) {
                        // Zobrazení progress baru
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            // Barevný pruh na pozadí podle procent
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(downloadProgress / 100f)
                                    .background(OnoRed.copy(alpha = 0.2f))
                            )
                            // Text přes pruh (barva musí být čitelná na světlém pozadí)
                            Text("Stahuji… $downloadProgress %", fontWeight = FontWeight.Bold, color = OnoRed)
                        }
                    } else {
                        // Normální texty
                        val rawVersion = updateInfo?.version ?: pendingVersion
                        val displayVersion = rawVersion?.removePrefix("v")
                        
                        Text(
                            when (updateState) {
                                UpdateState.IDLE -> if (updateInfo != null) "Stáhnout verzi (v$displayVersion)" else "Zkontrolovat aktualizace"
                                UpdateState.CHECKING -> "Kontroluji…"
                                UpdateState.READY_TO_INSTALL -> "Spustit instalaci (v$displayVersion)"
                                UpdateState.UP_TO_DATE -> "Zkontrolovat aktualizace"
                                else -> ""
                            },
                            fontWeight = if (isUpdateAvailable) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }

                // ---- Sdílení aplikace a QR kód (vedle sebe) ----
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "Tank ONO widget")
                                putExtra(Intent.EXTRA_TEXT, "Zkus tento super widget na ceny Tank ONO! Stáhneš ho zde:\n$GITHUB_URL")
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Sdílet aplikaci"))
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = OnoRed),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Sdílet")
                    }

                    OutlinedButton(
                        onClick = { showQrDialog = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = OnoRed),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("QR kód")
                    }
                }

                // ---- ZÁPATÍ (jeden blok textu) ----
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    Spacer(Modifier.height(8.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 48.dp)
                            .height(1.dp)
                            .background(OnoRed.copy(alpha = 0.25f))
                    )

                    Spacer(Modifier.height(8.dp))

                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(
                                color = OnoRed.copy(alpha = 0.6f),
                                fontSize = 10.sp, 
                                fontWeight = FontWeight.Bold
                            )) {
                                append("Tank ONO widget v${BuildConfig.VERSION_NAME}\nAutor: Jirka Zeman pomocí GEMINI AI")
                            }
                            append("\n")

                            withStyle(SpanStyle(
                                color = OnoRed.copy(alpha = 0.85f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )) {
                                append(when {
                                    installCountLoading -> "Počet instalací: …"
                                    installCount != null -> "Počet instalací: $installCount"
                                    else -> "Počet instalací: —"
                                })
                            }
                            append("\n\n")

                            withStyle(SpanStyle(
                                color = OnoRed.copy(alpha = 0.55f),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )) {
                                append("Aplikace odeslala hash tohoto zařízení")
                            }
                            append("\n")
                            
                            val hash = deviceHash
                            if (hash != null) {
                                val half = (hash.length + 1) / 2
                                val firstHalf = hash.substring(0, half)
                                val secondHalf = hash.substring(half)
                                withStyle(SpanStyle(
                                    color = OnoRed.copy(alpha = 0.5f),
                                    fontSize = 8.sp,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 0.5.sp
                                )) {
                                    append("$firstHalf\n$secondHalf")
                                }
                            } else {
                                withStyle(SpanStyle(
                                    color = OnoRed.copy(alpha = 0.4f),
                                    fontSize = 8.sp,
                                    fontStyle = FontStyle.Italic
                                )) {
                                    append("(nedostupné)")
                                }
                            }
                            append("\n")
                            
                            withStyle(SpanStyle(
                                color = OnoRed.copy(alpha = 0.55f),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )) {
                                append("pro statistiku stažení")
                            }
                            append("\n\n")

                            withStyle(SpanStyle(
                                color = OnoRed.copy(alpha = 0.75f),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )) {
                                append("Nic jiného se neposílá.")
                            }
                        },
                        color = OnoRed,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                footerTapCount++
                                if (footerTapCount >= 10) {
                                    footerTapCount = 0
                                    showLogFlash = true
                                    vibrateLong(context) // Zavibruje 2s
                                    
                                    // Spustíme odpočet 2s, než otevřeme sdílení
                                    scope.launch {
                                        delay(2000)
                                        showLogFlash = false
                                        onExportLog()
                                    }
                                }
                            },
                        textAlign = TextAlign.Center,
                        lineHeight = 13.sp
                    )
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
                navTargetPump = pump
            }
        )
    }

    if (showQrDialog) {
        QrCodeDialog(onDismiss = { showQrDialog = false })
    }

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

    // Tajný 2s Flash při generování logu
    if (showLogFlash) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { /* Nedovolíme zavřít kliknutím mimo */ },
            properties = androidx.compose.ui.window.DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(OnoRed.copy(alpha = 0.95f))
                    .clickable(enabled = false) {}, // Zablokuje prokliknutí
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "GENERUJI LOG\nPRO EXPORT",
                    color = OnoYellow,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                    lineHeight = 34.sp
                )
            }
        }
    }
}


// =============================================================================
// NAVIGACE – jednořádková s dropdown tlačítkem
// =============================================================================
@Composable
private fun NavigationRow(
    current: NavigationApp,
    installedApps: List<NavigationApp>,
    onSelect: (NavigationApp) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Navigovat přes: ",
                    color = OnoRed,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    current.displayName,
                    color = OnoRed,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal
                )
            }

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .border(2.dp, OnoRed, RoundedCornerShape(8.dp))
                    .clickable { expanded = true },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "▼",
                    color = OnoRed,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .background(OnoYellow)
                .border(2.dp, OnoRed)
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
                onClick = {
                    onSelect(NavigationApp.SYSTEM)
                    expanded = false
                }
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
                    onClick = {
                        onSelect(app)
                        expanded = false
                    }
                )
            }
        }
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
                Text(
                    "Nejbližší stanice:",
                    color = OnoRed,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            pumpDisplayName(nearestPump.name),
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
                Text(
                    "Nejbližší stanice:",
                    color = OnoRed,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "   Nedostupná poloha",
                    color = OnoRed,
                    fontSize = 12.sp
                )
            }
            else -> {
                Text(
                    "Nejbližší stanice:",
                    color = OnoRed,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "   Zjišťuji…",
                    color = OnoRed,
                    fontSize = 12.sp
                )
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
    // Vynutíme černé ikony i při otevřeném BottomSheetu
    val view = LocalView.current
    if (!view.isInEditMode) {
        androidx.compose.runtime.SideEffect {
            val window = (view.context as? android.app.Activity)?.window
            if (window != null) {
                val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
                insetsController.isAppearanceLightStatusBars = true
                insetsController.isAppearanceLightNavigationBars = true
            }
        }
    }

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
                                    pumpDisplayName(pump.name),
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
            verticalArrangement = Arrangement.spacedBy(4.dp),
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
                Text(product.displayName + " (" + product.shortName + ")", color = OnoRed, fontSize = 14.sp, modifier = Modifier.weight(1f))
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

// =============================================================================
// QR KÓD A SDÍLENÍ
// =============================================================================

/**
 * Vygeneruje QR kód jako ImageBitmap.
 */
private fun generateQrCode(text: String, size: Int = 512): ImageBitmap {
    val bitMatrix = MultiFormatWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    for (x in 0 until size) {
        for (y in 0 until size) {
            bitmap.setPixel(x, y, if (bitMatrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        }
    }
    return bitmap.asImageBitmap()
}

@Composable
private fun QrCodeDialog(
    onDismiss: () -> Unit
) {
    val qrBitmap = remember { generateQrCode(GITHUB_URL) }
    val uriHandler = LocalUriHandler.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Naskenuj a sdílej",
                color = OnoRed,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Naskenuj tento QR kód telefonem.",
                    color = OnoRed.copy(alpha = 0.7f),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Image(
                    bitmap = qrBitmap,
                    contentDescription = "QR kód pro stažení aplikace",
                    modifier = Modifier
                        .size(240.dp)
                        .border(4.dp, OnoRed, RoundedCornerShape(8.dp))
                        .padding(8.dp)
                )
                Spacer(Modifier.height(16.dp))
                // NOVÉ: Klikatelný odkaz
                Text(
                    text = GITHUB_URL,
                    color = OnoRed,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            try {
                                uriHandler.openUri(GITHUB_URL)
                            } catch (_: Throwable) {
                                // Pokud prohlížeč selže, ignorujeme
                            }
                        }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Zavřít", color = OnoRed, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = OnoYellow
    )
}