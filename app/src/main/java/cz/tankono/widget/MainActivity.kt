package cz.tankono.widget

import android.Manifest
import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Vibrator
import android.os.VibratorManager
import android.os.VibrationEffect
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import cz.tankono.widget.data.prefs.SettingsStore
import cz.tankono.widget.data.prefs.WidgetSettings
import cz.tankono.widget.ui.HomeScreen
import cz.tankono.widget.ui.SettingsScreen
import cz.tankono.widget.util.AppLogger
import cz.tankono.widget.util.InstallReporter
import cz.tankono.widget.work.WorkScheduler
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {

    private var configWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID
    private var locationGrantedState by mutableStateOf(false)

    private val notifPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(this, "Notifikace o novém ceníku nebudou zobrazeny.", Toast.LENGTH_LONG).show()
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
            Toast.makeText(this, "Bez polohy nelze určit nejbližší stanici.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val windowInsetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.isAppearanceLightStatusBars = true
        windowInsetsController.isAppearanceLightNavigationBars = true

        checkPendingUpdate(this)

        AppLogger.init(this)
        AppLogger.i("=== Aplikace spuštěna ===")
        InstallReporter.reportIfNeeded(this)

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
                var showSettings by remember { mutableStateOf(false) }

                // Zpracování tlačítka Zpět – pokud jsme v nastavení, vrátíme se na Home
                BackHandler(enabled = showSettings) {
                    showSettings = false
                }

                if (showSettings) {
                    SettingsScreen(
                        locationGranted = locationGrantedState,
                        onSave = { settings -> saveSettings(settings) },
                        onRefresh = { refreshNow() },
                        onExportLog = { exportLog() },
                        onOpenBatterySettings = { openBatterySettings() },
                        onRequestLocation = { requestLocationPermissionIfNeeded() },
                        onRequestNotification = { requestNotificationPermissionIfNeeded() },
                        onInstallApk = { file -> installApk(file) },
                        isConfiguring = configWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID,
                        onNavigateToHome = { showSettings = false }
                    )
                } else {
                    HomeScreen(onNavigateToSettings = { showSettings = true })
                }
            }
        }

        WorkScheduler.schedule(this)
        WorkScheduler.schedulePumpSync(this)
        WorkScheduler.scheduleUpdateCheck(this)
    }

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            if (!granted) notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun requestLocationPermissionIfNeeded() {
        if (!hasLocationPermission()) {
            locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    private fun openBatterySettings() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply { data = Uri.parse("package:$packageName") }
            startActivity(intent)
            Toast.makeText(this, "Najdi Baterie → Neomezené", Toast.LENGTH_LONG).show()
        } catch (t: Throwable) { AppLogger.e("Nelze otevřít nastavení baterie", t) }
    }

    private fun installApk(file: File): Boolean {
        try {
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
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
            file.delete()
            return false
        }
    }

    private fun saveSettings(settings: WidgetSettings) {
        lifecycleScope.launch {
            AppLogger.i("Ukládám nastavení: visible=${settings.visibleProducts.size}, currency=${settings.currency}, nav=${settings.preferredNavigation}")
            SettingsStore(this@MainActivity).save(settings)

            if (!settings.showNearestPump) {
                SettingsStore(this@MainActivity).clearCachedLocation()
            }

            WorkScheduler.schedule(this@MainActivity)
            cz.tankono.widget.widget.TankOnoWidget.requestUpdate(this@MainActivity)

            if (configWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                val resultValue = Intent().apply { putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, configWidgetId) }
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
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", logFile)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Tank ONO widget – log")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Sdílet log"))
        } catch (t: Throwable) { AppLogger.e("Chyba při exportu logu", t) }
    }
}

fun vibrateLong(context: Context) {
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

fun checkPendingUpdate(context: Context): File? {
    val files = context.cacheDir.listFiles { file -> file.name.startsWith("update_v") && file.name.endsWith(".apk") } ?: return null
    for (file in files) {
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