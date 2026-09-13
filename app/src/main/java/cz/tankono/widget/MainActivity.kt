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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import cz.tankono.widget.data.model.Currency
import cz.tankono.widget.data.model.Product
import cz.tankono.widget.data.prefs.SettingsStore
import cz.tankono.widget.data.prefs.WidgetSettings
import cz.tankono.widget.data.remote.TankOnoScraper
import cz.tankono.widget.util.AppLogger
import cz.tankono.widget.util.UpdateChecker
import cz.tankono.widget.work.WorkScheduler
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


private val OnoYellow = Color(0xFFFFD600)
private val OnoRed    = Color(0xFFC92200)


class MainActivity : ComponentActivity() {

    private var configWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID

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

        requestNotificationPermissionIfNeeded()

        setContent {
            MaterialTheme {
                SettingsScreen(
                    onSave = { settings -> saveSettings(settings) },
                    onRefresh = { refreshNow() },
                    onExportLog = { exportLog() },
                    onOpenBatterySettings = { openBatterySettings() },
                    onOpenUrl = { url -> openUrl(url) },
                    isConfiguring = configWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID
                )
            }
        }

        WorkScheduler.schedule(this)
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

    /** Otevře URL v prohlížeči. */
    private fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
            AppLogger.i("Otevřeno URL: $url")
        } catch (t: Throwable) {
            AppLogger.e("Nelze otevřít URL: $url", t)
            Toast.makeText(this, "Nelze otevřít odkaz", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveSettings(settings: WidgetSettings) {
        lifecycleScope.launch {
            AppLogger.i("Ukládám nastavení: visible=${settings.visibleProducts.size}, " +
                    "currency=${settings.currency}, short=${settings.useShortNames}")
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
        AppLogger.i("Manuální refresh – forceRefresh")
        Toast.makeText(this, getString(R.string.refreshing_toast), Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            try {
                val repo = cz.tankono.widget.data.repo.PriceRepository(this@MainActivity)
                repo.forceRefresh()
                cz.tankono.widget.widget.TankOnoWidget.requestUpdate(this@MainActivity)
            } catch (t: Throwable) {
                AppLogger.e("Chyba při forceRefresh", t)
            }
        }
    }

    private fun exportLog() {
        try {
            val logFile = AppLogger.getLogFile(this)
            if (!logFile.exists()) {
                Toast.makeText(this, "Log je prázdný", Toast.LENGTH_SHORT).show()
                return
            }
            val cacheFile = java.io.File(cacheDir, "tankono_log.txt")
            logFile.copyTo(cacheFile, overwrite = true)

            val uri = androidx.core.content.FileProvider.getUriForFile(
                this, "$packageName.fileprovider", cacheFile
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
    onSave: (WidgetSettings) -> Unit,
    onRefresh: () -> Unit,
    onExportLog: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onOpenUrl: (String) -> Unit,
    isConfiguring: Boolean
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val settingsFlow = remember { SettingsStore(context).settings }
    val loaded by settingsFlow.collectAsStateWithLifecycle(initialValue = null)

    val state = remember { mutableStateOf<WidgetSettings?>(null) }

    var lastPublished by remember { mutableStateOf<String?>(null) }
    var lastFetched by remember { mutableStateOf<Long?>(null) }

    // Update stav
    var updateInfo by remember { mutableStateOf<UpdateChecker.UpdateInfo?>(null) }
    var isChecking by remember { mutableStateOf(false) }

    LaunchedEffect(loaded) {
        if (loaded != null && state.value == null) {
            state.value = loaded
        }
        try {
            val repo = cz.tankono.widget.data.repo.PriceRepository(context)
            val s = repo.loadState()
            lastPublished = s.current?.publishedAt
            lastFetched = s.current?.fetchedAt
        } catch (_: Throwable) {}
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

                // ---- Info o posledních aktualizacích ----
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
                        Spacer(Modifier.width(10.dp))
                    }
                }

                // ---- Špička ----
                SettingsCard {
                    SectionTitle("Špička (pravděpodobný čas aktualizace cen)")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            NumberStepper(
                                value = formatTime(s.peakStartMinutes),
                                onMinus = {
                                    val newVal = (s.peakStartMinutes - 15).coerceAtLeast(0)
                                    state.value = s.copy(peakStartMinutes = newVal)
                                },
                                onPlus = {
                                    val newVal = (s.peakStartMinutes + 15).coerceAtMost(1440)
                                    state.value = s.copy(peakStartMinutes = newVal)
                                },
                                canMinus = s.peakStartMinutes > 0,
                                canPlus = s.peakStartMinutes < 1440,
                                onMin = { state.value = s.copy(peakStartMinutes = 0) },
                                onMax = { state.value = s.copy(peakStartMinutes = 1440) }
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                        Box(modifier = Modifier.weight(1f)) {
                            NumberStepper(
                                value = formatTime(s.peakEndMinutes),
                                onMinus = {
                                    val newVal = (s.peakEndMinutes - 15).coerceAtLeast(0)
                                    state.value = s.copy(peakEndMinutes = newVal)
                                },
                                onPlus = {
                                    val newVal = (s.peakEndMinutes + 15).coerceAtMost(1440)
                                    state.value = s.copy(peakEndMinutes = newVal)
                                },
                                canMinus = s.peakEndMinutes > 0,
                                canPlus = s.peakEndMinutes < 1440,
                                onMin = { state.value = s.copy(peakEndMinutes = 0) },
                                onMax = { state.value = s.copy(peakEndMinutes = 1440) }
                            )
                        }
                    }
                }

                // ---- Interval ----
                SettingsCard {
                    SectionTitle("Interval aktualizací špička / mimo špičku (min)")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            NumberStepper(
                                value = s.intervalPeakMin.toString(),
                                onMinus = {
                                    val newVal = (s.intervalPeakMin - 5).coerceAtLeast(15)
                                    state.value = s.copy(intervalPeakMin = newVal)
                                },
                                onPlus = {
                                    val newVal = (s.intervalPeakMin + 5).coerceAtMost(360)
                                    state.value = s.copy(intervalPeakMin = newVal)
                                },
                                canMinus = s.intervalPeakMin > 15,
                                canPlus = s.intervalPeakMin < 360,
                                onMin = { state.value = s.copy(intervalPeakMin = 15) },
                                onMax = { state.value = s.copy(intervalPeakMin = 360) }
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                        Box(modifier = Modifier.weight(1f)) {
                            NumberStepper(
                                value = s.intervalOffPeakMin.toString(),
                                onMinus = {
                                    val newVal = (s.intervalOffPeakMin - 5).coerceAtLeast(15)
                                    state.value = s.copy(intervalOffPeakMin = newVal)
                                },
                                onPlus = {
                                    val newVal = (s.intervalOffPeakMin + 5).coerceAtMost(360)
                                    state.value = s.copy(intervalOffPeakMin = newVal)
                                },
                                canMinus = s.intervalOffPeakMin > 15,
                                canPlus = s.intervalOffPeakMin < 360,
                                onMin = { state.value = s.copy(intervalOffPeakMin = 15) },
                                onMax = { state.value = s.copy(intervalOffPeakMin = 360) }
                            )
                        }
                    }
                }

                // ---- Velikost písma ----
                SettingsCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Velikost textu widgetu:",
                            color = OnoRed,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        Box(modifier = Modifier.width(160.dp)) {
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

                // ---- Uložit ----
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

                // ---- Aktualizovat data ----
                OutlinedButton(
                    onClick = onRefresh,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = OnoRed),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("↻  Aktualizovat data")
                }

                // ---- Kontrola aktualizací ----
                OutlinedButton(
                    onClick = {
                        isChecking = true
                        updateInfo = null
                        scope.launch {
                            val info = UpdateChecker.checkForUpdate()
                            updateInfo = info
                            isChecking = false
                            if (info == null) {
                                Toast.makeText(
                                    context,
                                    "Máš nejnovější verzi",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = OnoRed),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isChecking
                ) {
                    Text(if (isChecking) "Kontroluji…" else "Zkontrolovat aktualizace")
                }

                // ---- Stáhnout novou verzi ----
                updateInfo?.let { info ->
                    Button(
                        onClick = { onOpenUrl(info.downloadUrl) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = OnoRed,
                            contentColor = OnoYellow
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Stáhnout ${info.version}")
                    }
                }

                // ---- Nastavení baterie ----
                OutlinedButton(
                    onClick = onOpenBatterySettings,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = OnoRed),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Nastavení baterie")
                }

                // ---- Export logu ----
                OutlinedButton(
                    onClick = onExportLog,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = OnoRed),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Exportovat log")
                }

                Spacer(Modifier.height(16.dp))

                Text(
                    "Tank ONO widget v1.0 · autor: birgis",
                    color = OnoRed.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(8.dp))
            }
        }
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
    val buttonSize = 44.dp

    BoxWithConstraints {
        val availableWidth = maxWidth
        val buttonsWidth = buttonSize * 2
        val remaining = availableWidth - buttonsWidth

        val gap = when {
            remaining >= 130.dp -> 4.dp
            remaining >= 110.dp -> 3.dp
            remaining >= 90.dp  -> 2.dp
            else                -> 1.dp
        }

        val valueWidth = (remaining - (gap * 2)).coerceAtLeast(20.dp)

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
                    Text("−", color = OnoRed, fontSize = 26.sp, fontWeight = FontWeight.Bold)
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
                    Text("+", color = OnoRed, fontSize = 26.sp, fontWeight = FontWeight.Bold)
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
            .width(90.dp)
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