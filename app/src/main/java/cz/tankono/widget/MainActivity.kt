package cz.tankono.widget

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import cz.tankono.widget.data.model.Currency
import cz.tankono.widget.data.model.Product
import cz.tankono.widget.data.prefs.SettingsStore
import cz.tankono.widget.data.prefs.WidgetSettings
import cz.tankono.widget.util.AppLogger
import cz.tankono.widget.work.WorkScheduler
import kotlinx.coroutines.launch


// =============================================================================
// BARVY
// =============================================================================
private val OnoYellow = Color(0xFFFFD600)
private val OnoRed    = Color(0xFFC92200)


// =============================================================================
// HLAVIČKA – konstanta
// =============================================================================
private val HeaderHeight = 60.dp


// =============================================================================
// MAIN ACTIVITY
// =============================================================================
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

    private fun saveSettings(settings: WidgetSettings) {
        lifecycleScope.launch {
            AppLogger.i("Ukládám nastavení: visible=${settings.visibleProducts.size}, " +
                    "currency=${settings.currency}")
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

            Toast.makeText(
                this@MainActivity,
                getString(R.string.saved_toast),
                Toast.LENGTH_SHORT
            ).show()

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
                this,
                "$packageName.fileprovider",
                cacheFile
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
            Toast.makeText(this, "Chyba: ${t.message}", Toast.LENGTH_LONG).show()
        }
    }
}


// =============================================================================
// TOP-LEVEL HELPERY
// =============================================================================

/**
 * Převede [Drawable] na [Bitmap]. Pokud je to už [BitmapDrawable], vrátí rovnou jeho bitmapu.
 */
private fun Drawable.toBitmapSafe(width: Int = intrinsicWidth, height: Int = intrinsicHeight): Bitmap {
    if (this is BitmapDrawable) return bitmap
    val w = width.coerceAtLeast(1)
    val h = height.coerceAtLeast(1)
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    return bmp
}

/**
 * Vytvoří [ShaderBrush] z drawable – obrázek se PŘEDROZTÁHNE proporcionálně
 * na [targetHeight] (zachová poměr stran), pak se opakuje doprava (REPEAT)
 * a svisle se nechá být (CLAMP).
 */
@Composable
private fun tiledBrushFromResource(
    @DrawableRes id: Int,
    targetHeight: Dp
): ShaderBrush {
    val context = LocalContext.current
    val density = LocalDensity.current

    val scaledBitmap: ImageBitmap = remember(id, targetHeight, density) {
        val drawable = ContextCompat.getDrawable(context, id)
            ?: error("Drawable s id=$id nebyl nalezen")
        val src = drawable.toBitmapSafe().asImageBitmap().asAndroidBitmap()

        val targetHeightPx = with(density) { targetHeight.toPx() }.toInt().coerceAtLeast(1)

        // Proporcionální roztah – ZACHOVÁ POMĚR STRAN
        val scale = targetHeightPx.toFloat() / src.height
        val targetWidthPx = (src.width * scale).toInt().coerceAtLeast(1)

        val scaled = Bitmap.createScaledBitmap(src, targetWidthPx, targetHeightPx, true)
        scaled.asImageBitmap()
    }

    return remember(scaledBitmap) {
        val shader = BitmapShader(
            scaledBitmap.asAndroidBitmap(),
            Shader.TileMode.REPEAT,   // horizontálně: opakuje se doprava
            Shader.TileMode.CLAMP     // vertikálně: nic (už je správná výška)
        )
        ShaderBrush(shader)
    }
}


// =============================================================================
// HLAVIČKA – dvě vrstvy přesně přes sebe
// =============================================================================
@Composable
private fun OnoHeader(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth(0.9f)
            .height(HeaderHeight)
    ) {
        // ═══════════════════════════════════════════════════════════════
        // VRSTVA 1: logo_linka – pozadí, přesně velká jako Box
        // ═══════════════════════════════════════════════════════════════
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(tiledBrushFromResource(R.drawable.logo_linka, HeaderHeight))
        )

        // ═══════════════════════════════════════════════════════════════
        // VRSTVA 2: logo_text – popředí, přesně velká jako Box
        // ═══════════════════════════════════════════════════════════════
        Image(
            painter = painterResource(id = R.drawable.logo_text),
            contentDescription = "Tank ONO",
            modifier = Modifier
                .matchParentSize(),
            contentScale = ContentScale.Fit
            alignment = Alignment.CenterStart
        )

        // ═══════════════════════════════════════════════════════════════
        // Nastavení – vpravo nahoře
        // ═══════════════════════════════════════════════════════════════
        Text(
            text = "Nastavení",
            color = OnoRed,
            fontSize = 18.sp,
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
    isConfiguring: Boolean
) {
    val context = LocalContext.current

    val settingsFlow = remember { SettingsStore(context).settings }
    val loaded by settingsFlow.collectAsStateWithLifecycle(initialValue = null)

    val state = remember { mutableStateOf<WidgetSettings?>(null) }

    LaunchedEffect(loaded) {
        if (loaded != null && state.value == null) {
            state.value = loaded
        }
    }

    val s = state.value
    if (s == null) {
        Box(
            Modifier.fillMaxSize().background(OnoYellow),
            contentAlignment = Alignment.Center
        ) {
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

            // ============ HLAVIČKA ============
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                OnoHeader()
            }

            // ============ SCROLLOVATELNÝ OBSAH ============
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {

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
                        Text(
                            "Musíte vybrat alespoň jeden produkt.",
                            color = OnoRed,
                            fontSize = 12.sp
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
                            CurrencyButton("€", s.currency == Currency.EUR) {
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
                        Spacer(Modifier.width(16.dp))
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

                // ---- Interval ----
                SettingsCard {
                    SectionTitle("Interval aktualizací špička / mimo špičku (min)")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
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
                        Spacer(Modifier.width(16.dp))
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
                        Spacer(Modifier.width(10.dp))
                    }
                }

                Spacer(Modifier.height(4.dp))

                // ---- Tlačítka ----
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
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Aktualizovat data")
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
    Text(
        text = text,
        color = OnoRed,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold
    )
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
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .border(2.dp, OnoRed, RoundedCornerShape(8.dp))
                .then(
                    if (canMinus && onMin != null) {
                        Modifier.combinedClickable(
                            onClick = onMinus,
                            onLongClick = onMin
                        )
                    } else if (canMinus) {
                        Modifier.clickable { onMinus() }
                    } else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            if (canMinus) {
                Icon(
                    Icons.Default.Remove,
                    contentDescription = "Mínus",
                    tint = OnoRed,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Box(
            modifier = Modifier
                .width(80.dp)
                .height(44.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = value,
                color = OnoRed,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }

        Box(
            modifier = Modifier
                .size(44.dp)
                .border(2.dp, OnoRed, RoundedCornerShape(8.dp))
                .then(
                    if (canPlus && onMax != null) {
                        Modifier.combinedClickable(
                            onClick = onPlus,
                            onLongClick = onMax
                        )
                    } else if (canPlus) {
                        Modifier.clickable { onPlus() }
                    } else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            if (canPlus) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Plus",
                    tint = OnoRed,
                    modifier = Modifier.size(20.dp)
                )
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
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
            ) {
                Text(
                    product.displayName,
                    color = OnoRed,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = checked,
                    onCheckedChange = { isChecked ->
                        val newSet = if (isChecked)
                            settings.visibleProducts + product
                        else
                            settings.visibleProducts - product
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