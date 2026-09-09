package com.tankono.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DebugHelper {
    private const val LOG_FILE = "tankono_debug.log"
    private var isEnabled = true

    private fun getLogFile(context: Context): File {
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (downloadDir != null && (downloadDir.exists() || downloadDir.mkdirs())) {
            return File(downloadDir, LOG_FILE)
        }
        return File(context.getExternalFilesDir(null), LOG_FILE)
    }

    fun log(context: Context, tag: String, message: String) {
        if (!isEnabled) return
        try {
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date())
            val logLine = "$timestamp [$tag] $message\n"
            val file = getLogFile(context)
            FileOutputStream(file, true).use { fos ->
                fos.write(logLine.toByteArray())
            }
            Log.d(tag, message)
        } catch (e: Exception) {
            Log.e("DebugHelper", "Chyba zápisu logu: ${e.message}")
        }
    }

    /**
     * ZÍSKÁ KOMPLETNÍ SYSTÉMOVÉ INFO O WIDGETU A APLIKACI
     */
    fun logSystemInfo(context: Context) {
        try {
            log(context, "SystemInfo", "=== SYSTÉMOVÉ INFORMACE ===")
            
            // 1. ZÁKLADNÍ INFO O ZAŘÍZENÍ
            log(context, "SystemInfo", "Zařízení: ${Build.MANUFACTURER} ${Build.MODEL}")
            log(context, "SystemInfo", "Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            log(context, "SystemInfo", "Brand: ${Build.BRAND}, Product: ${Build.PRODUCT}")
            
            // 2. INFO O APLIKACI
            val packageManager = context.packageManager
            val packageName = context.packageName
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            log(context, "SystemInfo", "Aplikace: $packageName")
            log(context, "SystemInfo", "Verze: ${packageInfo.versionName} (${packageInfo.versionCode})")
            log(context, "SystemInfo", "Cesta: ${packageInfo.applicationInfo.sourceDir}")
            
            // 3. INFO O WIDGETU
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, TankONOWidget::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            
            log(context, "SystemInfo", "Widget component: ${componentName.flattenToString()}")
            log(context, "SystemInfo", "Widget IDs nalezeno: ${appWidgetIds.size}")
            
            if (appWidgetIds.isNotEmpty()) {
                for (id in appWidgetIds) {
                    log(context, "SystemInfo", "  - Widget ID: $id")
                    // Zkusíme získat info o velikosti widgetu
                    try {
                        val options = appWidgetManager.getAppWidgetOptions(id)
                        log(context, "SystemInfo", "    - MinWidth: ${options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)}")
                        log(context, "SystemInfo", "    - MinHeight: ${options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)}")
                    } catch (e: Exception) {
                        log(context, "SystemInfo", "    - Nelze získat velikost: ${e.message}")
                    }
                }
            } else {
                log(context, "SystemInfo", "❌ ŽÁDNÝ WIDGET NENALEZEN NA PLOŠE!")
                log(context, "SystemInfo", "Zkontrolujte, zda je widget přidán na plochu.")
            }
            
            // 4. OPRÁVNĚNÍ
            log(context, "SystemInfo", "=== OPRÁVNĚNÍ ===")
            val permissions = listOf(
                android.Manifest.permission.INTERNET,
                android.Manifest.permission.POST_NOTIFICATIONS
            )
            for (perm in permissions) {
                val status = if (packageManager.checkPermission(perm, packageName) == PackageManager.PERMISSION_GRANTED) {
                    "POVOLENO"
                } else {
                    "ZAMÍTNUTO"
                }
                log(context, "SystemInfo", "$perm: $status")
            }
            
            // 5. NASTAVENÍ SYSTÉMU (ovlivňuje widgety)
            log(context, "SystemInfo", "=== NASTAVENÍ SYSTÉMU ===")
            
            // Zda má aplikace povoleno zobrazovat widgety (Android 8+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    val canDrawOverlays = Settings.canDrawOverlays(context)
                    log(context, "SystemInfo", "Zobrazení přes jiné aplikace: $canDrawOverlays")
                } catch (e: Exception) {
                    log(context, "SystemInfo", "Nelze zjistit zobrazení přes jiné aplikace: ${e.message}")
                }
            }
            
            // 6. OMEZENÍ POZADÍ (Battery Optimization)
            try {
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
                val isIgnoringBatteryOptimizations = powerManager.isIgnoringBatteryOptimizations(context.packageName)
                log(context, "SystemInfo", "Vypnuta optimalizace baterie: $isIgnoringBatteryOptimizations")
            } catch (e: Exception) {
                log(context, "SystemInfo", "Nelze zjistit optimalizaci baterie: ${e.message}")
            }
            
            // 7. DATA MANAGER - KONTROLA ULOŽENÝCH DAT
            log(context, "SystemInfo", "=== ULOŽENÁ DATA ===")
            val prices = DataManager.getPrices(context)
            if (prices != null) {
                log(context, "SystemInfo", "Ceny: N95=${prices.n95}, Diesel=${prices.diesel}")
                log(context, "SystemInfo", "Poslední aktualizace: ${DataManager.getLastUpdate(context)}")
                log(context, "SystemInfo", "Poslední změna cen: ${DataManager.getLastChangeDate(context)}")
            } else {
                log(context, "SystemInfo", "❌ Žádná data nejsou uložena!")
            }
            
            // 8. ZKUSÍME WIDGET VYTVOŘIT/ZOBRAZIT
            log(context, "SystemInfo", "=== POKUS O VYTVOŘENÍ WIDGETU ===")
            try {
                // Zkusíme ručně aktualizovat widget
                TankONOWidget.updateAllWidgets(context)
                log(context, "SystemInfo", "✅ Widget aktualizován (updateAllWidgets volán)")
            } catch (e: Exception) {
                log(context, "SystemInfo", "❌ Chyba při aktualizaci widgetu: ${e.message}")
                e.printStackTrace()
            }
            
            log(context, "SystemInfo", "=== KONEC SYSTÉMOVÝCH INFORMACÍ ===")
            
        } catch (e: Exception) {
            log(context, "SystemInfo", "❌ CHYBA PŘI ZÍSKÁVÁNÍ SYSTÉMOVÝCH INFORMACÍ: ${e.message}")
            e.printStackTrace()
        }
    }

    fun clearLog(context: Context) {
        try {
            val file = getLogFile(context)
            if (file.exists()) file.delete()
            log(context, "DebugHelper", "=== LOG SMAZÁN ===")
        } catch (e: Exception) {
            Log.e("DebugHelper", "Chyba mazání logu: ${e.message}")
        }
    }

    fun getLogContent(context: Context): String {
        return try {
            val file = getLogFile(context)
            if (file.exists()) {
                file.readText()
            } else {
                "Log soubor neexistuje\n\nCesta: ${file.absolutePath}"
            }
        } catch (e: Exception) {
            "Chyba čtení logu: ${e.message}\n\nCesta: ${getLogFile(context).absolutePath}"
        }
    }

    fun exportLog(context: Context): String {
        return try {
            val sourceFile = getLogFile(context)
            if (!sourceFile.exists()) {
                return "Log neexistuje"
            }
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val exportFile = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "tankono_log_$timestamp.txt"
            )
            sourceFile.copyTo(exportFile, overwrite = true)
            "Log exportován do:\n${exportFile.absolutePath}"
        } catch (e: Exception) {
            "Chyba exportu: ${e.message}"
        }
    }
}