package com.tankono.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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

    fun logSystemInfo(context: Context) {
        try {
            log(context, "SystemInfo", "=== SYSTÉMOVÉ INFORMACE ===")
            log(context, "SystemInfo", "Zařízení: ${Build.MANUFACTURER} ${Build.MODEL}")
            log(context, "SystemInfo", "Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")

            val packageManager = context.packageManager
            val packageName = context.packageName
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            log(context, "SystemInfo", "Aplikace: $packageName")

            // ✅ POUŽIJEME longVersionCode místo versionCode (odstraní deprecated warning)
            val longVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }
            log(context, "SystemInfo", "Verze: ${packageInfo.versionName} ($longVersionCode)")

            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, TankONOWidgetReceiver::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)

            log(context, "SystemInfo", "Widget IDs nalezeno: ${appWidgetIds.size}")

            if (appWidgetIds.isEmpty()) {
                log(context, "SystemInfo", "❌ ŽÁDNÝ WIDGET NENALEZEN NA PLOŠE!")
            }

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

            log(context, "SystemInfo", "=== ULOŽENÁ DATA ===")
            val prices = DataManager.getPrices(context)
            if (prices != null) {
                log(context, "SystemInfo", "Ceny: N95=${prices.n95}, Diesel=${prices.diesel}")
                log(context, "SystemInfo", "Poslední aktualizace: ${DataManager.getLastUpdate(context)}")
                log(context, "SystemInfo", "Poslední změna cen: ${DataManager.getLastChangeDate(context)}")
            } else {
                log(context, "SystemInfo", "❌ Žádná data nejsou uložena!")
            }

            log(context, "SystemInfo", "=== POKUS O AKTUALIZACI WIDGETU ===")

            // ✅ POUŽIJEME VLASTNÍ CoroutineScope místo GlobalScope (odstraní delicate API warning)
            val scope = CoroutineScope(Dispatchers.IO)
            scope.launch {
                try {
                    TankONOWidget().updateAll(context)
                    log(context, "SystemInfo", "✅ Widget aktualizován")
                } catch (e: Exception) {
                    log(context, "SystemInfo", "❌ Chyba: ${e.message}")
                }
            }

            log(context, "SystemInfo", "=== KONEC SYSTÉMOVÝCH INFORMACÍ ===")

        } catch (e: Exception) {
            log(context, "SystemInfo", "❌ CHYBA: ${e.message}")
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