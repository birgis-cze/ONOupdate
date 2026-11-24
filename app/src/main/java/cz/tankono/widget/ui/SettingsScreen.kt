package cz.tankono.widget.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import cz.tankono.widget.BuildConfig
import cz.tankono.widget.R
import cz.tankono.widget.data.model.Currency
import cz.tankono.widget.data.model.Product
import cz.tankono.widget.data.prefs.NavigationApp
import cz.tankono.widget.data.prefs.SettingsStore
import cz.tankono.widget.data.prefs.ThemeMode
import cz.tankono.widget.data.prefs.WidgetSettings
import cz.tankono.widget.data.remote.TankOnoScraper
import cz.tankono.widget.notify.Notifier
import cz.tankono.widget.ui.components.CurrencyButton
import cz.tankono.widget.ui.components.NavigationRow
import cz.tankono.widget.ui.components.NumberStepper
import cz.tankono.widget.ui.components.OnoHeader
import cz.tankono.widget.ui.components.ProductGroup
import cz.tankono.widget.ui.components.SoundSelectionSheet
import cz.tankono.widget.ui.components.TwoSteppersRow
import cz.tankono.widget.util.AppLogger
import cz.tankono.widget.util.InstallReporter
import cz.tankono.widget.util.SoundHelper
import cz.tankono.widget.util.UpdateChecker
import cz.tankono.widget.work.WorkScheduler
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import cz.tankono.widget.UpdateState
import cz.tankono.widget.ui.components.SoundSelectionSheet
import cz.tankono.widget.vibrateLong
import cz.tankono.widget.ui.components.QrCodeDialog
import android.content.Context


private const val GITHUB_URL = "https://github.com/birgis-cze/ONOupdate"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SettingsScreen(
    locationGranted: Boolean,
    onSave: (WidgetSettings) -> Unit,
    onRefresh: () -> Unit,
    onExportLog: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onRequestLocation: () -> Unit,
    onRequestNotification: () -> Unit,
    onInstallApk: (File) -> Boolean,
    isConfiguring: Boolean,
    onNavigateToHome: () -> Unit // <-- Parametr zůstává, i když tlačítko zakomentujete
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

    var updateState by remember { mutableStateOf(UpdateState.IDLE) }
    var updateInfo by remember { mutableStateOf<UpdateChecker.UpdateInfo?>(null) }
    var downloadedFile by remember { mutableStateOf<File?>(null) }
    var pendingVersion by remember { mutableStateOf<String?>(null) }
    var downloadProgress by remember { mutableIntStateOf(0) }

    var hasLoc by remember { mutableStateOf(false) }
    var hasNotif by remember { mutableStateOf(false) }
    var batteryOk by remember { mutableStateOf(false) }

    var showQrDialog by remember { mutableStateOf(false) }

    var footerTapCount by remember { mutableIntStateOf(0) }
    var showLogFlash by remember { mutableStateOf(false) }

    val availableSounds = remember { SoundHelper.getAvailableSounds(context) }
    val defaultSoundFile = availableSounds.firstOrNull()?.fileName ?: ""
    var soundSelectionExpanded by remember { mutableStateOf(false) }

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
        val pendingFile = cz.tankono.widget.checkPendingUpdate(context)
        if (pendingFile != null) {
            downloadedFile = pendingFile
            pendingVersion = pendingFile.name.removePrefix("update_v").removeSuffix(".apk").removePrefix("v")
            updateState = UpdateState.READY_TO_INSTALL
        }
    }

    // Tichá kontrola aktualizací na pozadí při otevření nastavení
    LaunchedEffect(Unit) {
        if (downloadedFile == null && updateState == UpdateState.IDLE) {
            try {
                AppLogger.d("Auto-check: tichá kontrola aktualizací…")
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
        Toast.makeText(context, "Uloženo", Toast.LENGTH_SHORT).show()

        if (current.notificationSound != loadedLocal.notificationSound) {
            AppLogger.i("Zvuk změněn, čistím staré notifikační kanály...")
            Notifier.cleanupUnusedPriceChannels(context, current.notificationSound)
        }

        WorkScheduler.schedule(context)
    }

    val s = state.value
    if (s == null) {
        Box(Modifier.fillMaxSize().background(OnoYellow), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = OnoRed)
        }
        return
    }

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
            downloadProgress = 0
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
                // Tlačítko Zpět na ceny – ZAKOMENTOVÁNO
                // Button(
                //     onClick = onNavigateToHome,
                //     colors = ButtonDefaults.buttonColors(containerColor = OnoRed, contentColor = OnoYellow),
                //     modifier = Modifier.fillMaxWidth()
                // ) {
                //     Text("Zpět na ceny", fontWeight = FontWeight.Bold)
                // }

                // --- DYNAMICKÁ KARTA PRO SYSTÉMOVÁ NASTAVENÍ ---
                if (!hasLoc || !hasNotif || !batteryOk) {
                    SettingsCard {
                        SectionTitle("Jsou potřeba povolení:")
                        if (!hasLoc) {
                            OutlinedButton(
                                onClick = onRequestLocation,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = OnoRed),
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Poloha") }
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
                            ) { Text("Notifikace") }
                        }
                        if (!batteryOk) {
                            OutlinedButton(
                                onClick = onOpenBatterySettings,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = OnoRed),
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Napájení (bez omezení)") }
                        }
                    }
                }

                // ---- Navigace ----
                SettingsCard {
                    if (installedNavApps.size >= 2) {
                        NavigationRow(
                            current = s.preferredNavigation,
                            installedApps = installedNavApps,
                            onSelect = { nav -> state.value = s.copy(preferredNavigation = nav) }
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
                            onCheckedChange = { checked -> state.value = s.copy(showNearestPump = checked) },
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
                    SectionTitle("Zobrazované produkty:")
                    ProductGroup("Paliva:", Product.entries.filter { it.kind == Product.Kind.FUEL }, s, state)
                    ProductGroup("Ostatní:", Product.entries.filter { it.kind == Product.Kind.OTHER }, s, state)
                    ProductGroup("Směnárna:", Product.entries.filter { it.kind == Product.Kind.EXCHANGE }, s, state)
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
                            "Krátké názvy na widgetu:",
                            color = OnoRed,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = s.useShortNames,
                            onCheckedChange = { checked -> state.value = s.copy(useShortNames = checked) },
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
                    SectionTitle("Špička (aktualizace cen):")
                    TwoSteppersRow(
                        value1 = formatTime(s.peakStartMinutes),
                        value2 = formatTime(s.peakEndMinutes),
                        onMinus1 = { state.value = s.copy(peakStartMinutes = (s.peakStartMinutes - 15).coerceAtLeast(0)) },
                        onPlus1 = { state.value = s.copy(peakStartMinutes = (s.peakStartMinutes + 15).coerceAtMost(1440)) },
                        onMinus2 = { state.value = s.copy(peakEndMinutes = (s.peakEndMinutes - 15).coerceAtLeast(0)) },
                        onPlus2 = { state.value = s.copy(peakEndMinutes = (s.peakEndMinutes + 15).coerceAtMost(1440)) },
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
                    SectionTitle("Interval aktualizací špička/mimo:")
                    TwoSteppersRow(
                        value1 = s.intervalPeakMin.toString(),
                        value2 = s.intervalOffPeakMin.toString(),
                        onMinus1 = { state.value = s.copy(intervalPeakMin = (s.intervalPeakMin - 5).coerceAtLeast(15)) },
                        onPlus1 = { state.value = s.copy(intervalPeakMin = (s.intervalPeakMin + 5).coerceAtMost(360)) },
                        onMinus2 = { state.value = s.copy(intervalOffPeakMin = (s.intervalOffPeakMin - 5).coerceAtLeast(15)) },
                        onPlus2 = { state.value = s.copy(intervalOffPeakMin = (s.intervalOffPeakMin + 5).coerceAtMost(360)) },
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

                // ---- Velikost textu ----
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
                            Text("Velikost textu:", color = OnoRed, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 8.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            NumberStepper(
                                value = s.fontSizeSp.toString(),
                                onMinus = { state.value = s.copy(fontSizeSp = (s.fontSizeSp - 1).coerceAtLeast(10)) },
                                onPlus = { state.value = s.copy(fontSizeSp = (s.fontSizeSp + 1).coerceAtMost(30)) },
                                canMinus = s.fontSizeSp > 10,
                                canPlus = s.fontSizeSp < 30,
                                onMin = { state.value = s.copy(fontSizeSp = 10) },
                                onMax = { state.value = s.copy(fontSizeSp = 30) }
                            )
                        }
                    }
                }

                // ---- Vzhled ----
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
                                Text("Vzhled widgetu: ", color = OnoRed, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text(s.themeMode.displayName, color = OnoRed, fontSize = 14.sp, fontWeight = FontWeight.Normal)
                            }
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .border(2.dp, OnoRed, RoundedCornerShape(8.dp))
                                    .clickable { themeExpanded = true },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("▼", color = OnoRed, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        DropdownMenu(
                            expanded = themeExpanded,
                            onDismissRequest = { themeExpanded = false },
                            modifier = Modifier.background(OnoYellow).border(2.dp, OnoRed)
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

                // ---- Zvuk ----
                SettingsCard {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .combinedClickable(
                                        onClick = { soundSelectionExpanded = true },
                                        onLongClick = {
                                            val actualSoundFile = if (s.notificationSound.isBlank()) defaultSoundFile else s.notificationSound
                                            Notifier.notifyNewPrices(context, actualSoundFile)
                                            Toast.makeText(context, "Zkušební notifikace odeslána", Toast.LENGTH_SHORT).show()
                                        }
                                    ),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Oznámení změny: ", color = OnoRed, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    availableSounds.find { it.fileName == (if (s.notificationSound.isBlank()) defaultSoundFile else s.notificationSound) }?.displayName ?: "Žádný zvuk",
                                    color = OnoRed, fontSize = 14.sp, fontWeight = FontWeight.Normal
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .border(2.dp, OnoRed, RoundedCornerShape(8.dp))
                                    .clickable { soundSelectionExpanded = true },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("▼", color = OnoRed, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))

                if (isConfiguring) {
                    Button(
                        onClick = { onSave(s) },
                        enabled = s.visibleProducts.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = OnoRed, contentColor = OnoYellow),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Přidat widget") }
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
                ) { Text("Aktualizovat data") }

                val isUpdateAvailable = updateState == UpdateState.IDLE && updateInfo != null
                val isDownloading = updateState == UpdateState.DOWNLOADING

                OutlinedButton(
                    onClick = {
                        when (updateState) {
                            UpdateState.IDLE -> {
                                if (updateInfo != null) {
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
                            else -> {}
                        }
                    },
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (isUpdateAvailable) OnoRed else Color.Transparent,
                        contentColor = if (isUpdateAvailable) OnoYellow else OnoRed
                    ),
                    modifier = Modifier.fillMaxWidth().then(if (isDownloading) Modifier.height(48.dp) else Modifier),
                    contentPadding = if (isDownloading) PaddingValues(0.dp) else ButtonDefaults.ContentPadding,
                    enabled = updateState != UpdateState.CHECKING && updateState != UpdateState.DOWNLOADING
                ) {
                    if (isDownloading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(downloadProgress / 100f)
                                    .background(OnoRed.copy(alpha = 0.2f))
                            )
                            Text("Stahuji… $downloadProgress %", fontWeight = FontWeight.Bold, color = OnoRed)
                        }
                    } else {
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
                            fontWeight = if (isUpdateAvailable) FontWeight.Bold else null
                        )
                    }
                }

                // ---- Sdílení a QR ----
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
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
                        shape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp, topEnd = 0.dp, bottomEnd = 0.dp),
                        modifier = Modifier.weight(1f)
                    ) { Text("Sdílet") }

                    OutlinedButton(
                        onClick = { showQrDialog = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = OnoRed),
                        shape = RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp, topEnd = 24.dp, bottomEnd = 24.dp),
                        modifier = Modifier.weight(1f)
                    ) { Text("QR kód") }
                }

                // ---- ZÁPATÍ ----
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
                            withStyle(SpanStyle(color = OnoRed.copy(alpha = 0.6f), fontSize = 10.sp, fontWeight = FontWeight.Bold)) {
                                append("Tank ONO widget v${BuildConfig.VERSION_NAME}\nAutor: Jirka Zeman pomocí GEMINI AI")
                            }
                            append("\n")
                            withStyle(SpanStyle(color = OnoRed.copy(alpha = 0.85f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)) {
                                append(when {
                                    installCountLoading -> "Počet instalací: …"
                                    installCount != null -> "Počet instalací: $installCount"
                                    else -> "Počet instalací: —"
                                })
                            }
                            append("\n\n")
                            withStyle(SpanStyle(color = OnoRed.copy(alpha = 0.55f), fontSize = 10.sp, fontWeight = FontWeight.Bold)) {
                                append("Aplikace odeslala hash tohoto zařízení")
                            }
                            append("\n")
                            val hash = deviceHash
                            if (hash != null) {
                                val half = (hash.length + 1) / 2
                                val firstHalf = hash.substring(0, half)
                                val secondHalf = hash.substring(half)
                                withStyle(SpanStyle(color = OnoRed.copy(alpha = 0.5f), fontSize = 8.sp, fontFamily = FontFamily.Monospace, letterSpacing = 0.5.sp)) {
                                    append("$firstHalf\n$secondHalf")
                                }
                            } else {
                                withStyle(SpanStyle(color = OnoRed.copy(alpha = 0.4f), fontSize = 8.sp, fontStyle = FontStyle.Italic)) {
                                    append("(nedostupné)")
                                }
                            }
                            append("\n")
                            withStyle(SpanStyle(color = OnoRed.copy(alpha = 0.55f), fontSize = 10.sp, fontWeight = FontWeight.Bold)) {
                                append("pro statistiku stažení")
                            }
                            append("\n\n")
                            withStyle(SpanStyle(color = OnoRed.copy(alpha = 0.75f), fontSize = 10.sp, fontWeight = FontWeight.Bold)) {
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
                                    vibrateLong(context)
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

    if (showQrDialog) {
        QrCodeDialog(onDismiss = { showQrDialog = false })
    }

    if (soundSelectionExpanded) {
        SoundSelectionSheet(
            currentSound = if (s.notificationSound.isBlank()) defaultSoundFile else s.notificationSound,
            onDismiss = { soundSelectionExpanded = false },
            onSelect = { soundName ->
                state.value = s.copy(notificationSound = soundName)
                soundSelectionExpanded = false
            }
        )
    }

    if (showLogFlash) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { },
            properties = androidx.compose.ui.window.DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(OnoRed.copy(alpha = 0.95f)),
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

fun formatTime(minutes: Int): String {
    if (minutes >= 1440) return "24:00"
    val h = (minutes / 60) % 24
    val m = minutes % 60
    return "%d:%02d".format(h, m)
}
