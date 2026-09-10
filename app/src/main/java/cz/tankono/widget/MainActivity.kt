package cz.tankono.widget

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.tankono.widget.data.model.Currency
import cz.tankono.widget.data.model.Product
import cz.tankono.widget.data.prefs.SettingsStore
import cz.tankono.widget.data.prefs.WidgetSettings
import cz.tankono.widget.work.WorkScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope

// Barvy Tank ONO (musí odpovídat res/values/colors.xml)
private val OnoYellow = Color(0xFFFFD600)
private val OnoRed    = Color(0xFFC92200)

class MainActivity : ComponentActivity() {

    // ID widgetu, který se právě konfiguruje (pokud uživatel přidává widget)
    private var configWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID

    // Launcher pro permission POST_NOTIFICATIONS
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

        // Zjistit, zda jsme spuštěni jako konfigurace widgetu
        configWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        // Pokud konfigurujeme widget, musíme poslat RESULT_CANCELED na začátku
        // (aby se widget nepřidal, když uživatel stiskne zpět)
        if (configWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            setResult(RESULT_CANCELED, Intent().apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, configWidgetId)
            })
        }

        // Vyžádat permission pro notifikace (Android 13+)
        requestNotificationPermissionIfNeeded()

        setContent {
            MaterialTheme {
                SettingsScreen(
                    onSave = { settings -> saveSettings(settings) },
                    onRefresh = { refreshNow() },
                    isConfiguring = configWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID
                )
            }
        }

        // Po prvním spuštění naplánovat periodickou práci
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
        // Uložit do DataStore
        lifecycleScope.launch {
            SettingsStore(this@MainActivity).save(settings)

            // Přeplánovat práci (mohl se změnit interval)
            WorkScheduler.schedule(this@MainActivity)

            // Překreslit widget
            cz.tankono.widget.widget.TankOnoWidget.requestUpdate(this@MainActivity)

            // Pokud konfigurujeme widget, vrátit RESULT_OK
            if (configWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                val resultValue = Intent().apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, configWidgetId)
                }
                setResult(RESULT_OK, resultValue)

                // Přidat widget hned (aby se zobrazil s nastavením)
                WorkScheduler.runNow(this@MainActivity)
            }

            Toast.makeText(
                this@MainActivity,
                getString(R.string.saved_toast),
                Toast.LENGTH_SHORT
            ).show()

            // Pokud jsme v konfiguraci widgetu, zavřít aktivitu
            if (configWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                finish()
            }
        }
    }

    private fun refreshNow() {
        Toast.makeText(this, getString(R.string.refreshing_toast), Toast.LENGTH_SHORT).show()
        WorkScheduler.runNow(this)
    }
}
// =============================================================================
// COMPOSE OBRAZOVKA
// =============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    onSave: (WidgetSettings) -> Unit,
    onRefresh: () -> Unit,
    isConfiguring: Boolean
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Načíst aktuální nastavení
    val settingsFlow = remember { SettingsStore(context).settings }
    val loaded by settingsFlow.collectAsStateWithLifecycle(initialValue = null)

    // Lokální stav (kopie pro editaci)
    val state = remember { mutableStateOf<WidgetSettings?>(null) }

    // Když se nastavení načte poprvé, zkopírujeme do lokálního stavu
    LaunchedEffect(loaded) {
        if (loaded != null && state.value == null) {
            state.value = loaded
        }
    }

    val s = state.value
    if (s == null) {
        // Načítání
        Box(Modifier.fillMaxSize().background(OnoYellow), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = OnoRed)
        }
        return
    }

    // ---- UI ----
    Surface(color = OnoYellow, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Nadpis
            Text(
                text = if (isConfiguring) "Nastavení widgetu" else "Nastavení",
                color = OnoRed,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            // ---- Produkty ----
            SectionTitle("Zobrazované produkty")
            ProductGroup("Paliva", Product.entries.filter { it.kind == Product.Kind.FUEL }, s, state)
            ProductGroup("Ostatní", Product.entries.filter { it.kind == Product.Kind.OTHER }, s, state)
            ProductGroup("Směnárna", Product.entries.filter { it.kind == Product.Kind.EXCHANGE }, s, state)

            if (s.visibleProducts.isEmpty()) {
                Text(
                    "Musíte vybrat alespoň jeden produkt.",
                    color = OnoRed,
                    fontSize = 12.sp
                )
            }

            // ---- Měna ----
            SectionTitle("Měna")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CurrencyButton("Kč", s.currency == Currency.CZK) {
                    state.value = s.copy(currency = Currency.CZK)
                }
                CurrencyButton("€", s.currency == Currency.EUR) {
                    state.value = s.copy(currency = Currency.EUR)
                }
            }

            // ---- Špička ----
            SectionTitle("Špička (pravděpodobný čas aktualizace cen)")
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                NumberStepper(
                    value = formatTime(s.peakStartMinutes),
                    onMinus = {
                        val newVal = (s.peakStartMinutes - 15 + 1440) % 1440
                        state.value = s.copy(peakStartMinutes = newVal)
                    },
                    onPlus = {
                        val newVal = (s.peakStartMinutes + 15) % 1440
                        state.value = s.copy(peakStartMinutes = newVal)
                    }
                )
                Text("|", color = OnoRed, fontSize = 18.sp)
                NumberStepper(
                    value = formatTime(s.peakEndMinutes),
                    onMinus = {
                        val newVal = (s.peakEndMinutes - 15 + 1440) % 1440
                        state.value = s.copy(peakEndMinutes = newVal)
                    },
                    onPlus = {
                        val newVal = (s.peakEndMinutes + 15) % 1440
                        state.value = s.copy(peakEndMinutes = newVal)
                    }
                )
            }

            // ---- Interval ----
            SectionTitle("Interval aktualizací špička / mimo špičku (min)")
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                NumberStepper(
                    value = s.intervalPeakMin.toString(),
                    onMinus = {
                        val newVal = (s.intervalPeakMin - 5).coerceAtLeast(5)
                        state.value = s.copy(intervalPeakMin = newVal)
                    },
                    onPlus = {
                        val newVal = (s.intervalPeakMin + 5).coerceAtMost(180)
                        state.value = s.copy(intervalPeakMin = newVal)
                    }
                )
                Text("|", color = OnoRed, fontSize = 18.sp)
                NumberStepper(
                    value = s.intervalOffPeakMin.toString(),
                    onMinus = {
                        val newVal = (s.intervalOffPeakMin - 5).coerceAtLeast(5)
                        state.value = s.copy(intervalOffPeakMin = newVal)
                    },
                    onPlus = {
                        val newVal = (s.intervalOffPeakMin + 5).coerceAtMost(720)
                        state.value = s.copy(intervalOffPeakMin = newVal)
                    }
                )
            }
            Text(
                "Intervaly kratší než 15 min jsou systémem Androidu omezeny na 15 min.",
                color = OnoRed.copy(alpha = 0.7f),
                fontSize = 11.sp
            )

            // ---- Velikost písma ----
            SectionTitle("Velikost písma widgetu (sp)")
            NumberStepper(
                value = s.fontSizeSp.toString(),
                onMinus = {
                    val newVal = (s.fontSizeSp - 1).coerceAtLeast(10)
                    state.value = s.copy(fontSizeSp = newVal)
                },
                onPlus = {
                    val newVal = (s.fontSizeSp + 1).coerceAtMost(25)
                    state.value = s.copy(fontSizeSp = newVal)
                }
            )

            Spacer(Modifier.height(8.dp))

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
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        color = OnoRed,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 8.dp)
    )
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
        products.forEach { product ->
            val checked = product in settings.visibleProducts
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val newSet = if (checked)
                            settings.visibleProducts - product
                        else
                            settings.visibleProducts + product
                        state.value = settings.copy(visibleProducts = newSet)
                    }
                    .padding(vertical = 4.dp)
            ) {
                Checkbox(
                    checked = checked,
                    onCheckedChange = { isChecked ->
                        val newSet = if (isChecked)
                            settings.visibleProducts + product
                        else
                            settings.visibleProducts - product
                        state.value = settings.copy(visibleProducts = newSet)
                    },
                    colors = CheckboxDefaults.colors(
                        checkedColor = OnoRed,
                        uncheckedColor = OnoRed,
                        checkmarkColor = OnoYellow
                    )
                )
                Text(product.displayName, color = OnoRed, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun CurrencyButton(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) OnoRed else Color.Transparent
    val fg = if (selected) OnoYellow else OnoRed
    Box(
        modifier = Modifier
            .border(2.dp, OnoRed, RoundedCornerShape(8.dp))
            .background(bg, RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 24.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = fg, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun NumberStepper(
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        IconButton(
            onClick = onMinus,
            modifier = Modifier
                .border(2.dp, OnoRed, RoundedCornerShape(8.dp))
                .size(40.dp)
        ) {
            Icon(Icons.Default.Remove, contentDescription = "Mínus", tint = OnoRed)
        }
        Text(
            value,
            color = OnoRed,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .border(2.dp, OnoRed, RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        )
        IconButton(
            onClick = onPlus,
            modifier = Modifier
                .border(2.dp, OnoRed, RoundedCornerShape(8.dp))
                .size(40.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Plus", tint = OnoRed)
        }
    }
}

private fun formatTime(minutes: Int): String {
    val h = (minutes / 60) % 24
    val m = minutes % 60
    return "%d:%02d".format(h, m)
}