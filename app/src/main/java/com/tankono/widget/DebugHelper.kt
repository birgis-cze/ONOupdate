package com.tankono.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DebugHelper {
    private const val LOG_FILE = "tankono_debug.log"
    private const val MIME_TYPE = "text/plain"

    /**
     * ✅ ULOŽÍ LOG DO DOWNLOAD PŘES MEDIASTORE
     * - Funguje na Androidu 10+ BEZ oprávnění
     * - Soubor se objeví v /Download/tankono_debug.log
     */
    private fun writeToDownload(context: Context, content: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ → MediaStore
                val resolver = context.contentResolver

                // Zkontrolujeme, zda soubor už existuje
                val existingUri = resolver.query(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    arrayOf(MediaStore.Downloads._ID),
                    "${MediaStore.Downloads.DISPLAY_NAME} = ?",
                    arrayOf(LOG_FILE),
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID))
                        android.content.ContentUris.withAppendedId(
                            MediaStore.Downloads.EXTERNAL_CONTENT_URI, id
                        )
                    } else null
                }

                val uri = if (existingUri != null) {
                    // Přepíšeme existující soubor
                    resolver.openOutputStream(existingUri, "wt")?.use { os ->
                        os.write(content.toByteArray())
                    }
                    existingUri
                } else {
                    // Vytvoříme nový soubor
                    val values = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, LOG_FILE)
                        put(MediaStore.Downloads.MIME_TYPE, MIME_TYPE)
                        put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    }
                    val newUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    newUri?.let {
                        resolver.openOutputStream(it)?.use { os ->
                            os.write(content.toByteArray())
                        }
                    }
                    newUri
                }

                uri != null
            } else {
                // Android 9 a nižší → přímý zápis do Download
                @Suppress("DEPRECATION")
                val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (downloadDir != null && (downloadDir.exists() || downloadDir.mkdirs())) {
                    val file = File(downloadDir, LOG_FILE)
                    file.writeText(content)
                    true
                } else false
            }
        } catch (e: Exception) {
            Log.e("DebugHelper", "writeToDownload chyba: ${e.message}")
            false
        }
    }

    /**
     * ✅ PŘEČTE LOG Z DOWNLOAD PŘES MEDIASTORE
     */
    private fun readFromDownload(context: Context): String? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val uri = resolver.query(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    arrayOf(MediaStore.Downloads._ID),
                    "${MediaStore.Downloads.DISPLAY_NAME} = ?",
                    arrayOf(LOG_FILE),
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID))
                        android.content.ContentUris.withAppendedId(
                            MediaStore.Downloads.EXTERNAL_CONTENT_URI, id
                        )
                    } else null
                }

                uri?.let {
                    resolver.openInputStream(it)?.use { is_ ->
                        is_.bufferedReader().readText()
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val file = File(downloadDir, LOG_FILE)
                if (file.exists()) file.readText() else null
            }
        } catch (e: Exception) {
            Log.e("DebugHelper", "readFromDownload chyba: ${e.message}")
            null
        }
    }

    /**
     * ✅ ZAPÍŠE ZÁZNAM DO LOGU (append)
     */
    fun log(context: Context, tag: String, message: String) {
        try {
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date())
            val logLine = "$timestamp [$tag] $message\n"

            // Načteme existující obsah
            val existing = readFromDownload(context) ?: ""
            val newContent = existing + logLine

            // Zapíšeme
            writeToDownload(context, newContent)

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

            log(context, "SystemInfo", "Widget IDs: ${appWidgetIds.size}")

            val prices = DataManager.getPrices(context)
            if (prices != null) {
                log(context, "SystemInfo", "Ceny: N95=${prices.n95}, Diesel=${prices.diesel}")
            }

            val scope = CoroutineScope(Dispatchers.IO)
            scope.launch {
                try {
                    TankONOWidget().updateAll(context)
                    log(context, "SystemInfo", "✅ Widget aktualizován")
                } catch (e: Exception) {
                    log(context, "SystemInfo", "❌ Chyba: ${e.message}")
                }
            }

            log(context, "SystemInfo", "=== KONEC ===")

        } catch (e: Exception) {
            log(context, "SystemInfo", "❌ CHYBA: ${e.message}")
        }
    }

    /**
     * ✅ SMAŽE LOG Z DOWNLOAD
     */
    fun clearLog(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val deleted = resolver.delete(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    "${MediaStore.Downloads.DISPLAY_NAME} = ?",
                    arrayOf(LOG_FILE)
                )
                Log.d("DebugHelper", "Smazáno záznamů: $deleted")
            } else {
                @Suppress("DEPRECATION")
                val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val file = File(downloadDir, LOG_FILE)
                if (file.exists()) file.delete()
            }
            log(context, "DebugHelper", "=== LOG SMAZÁN ===")
        } catch (e: Exception) {
            Log.e("DebugHelper", "Chyba mazání logu: ${e.message}")
        }
    }

    /**
     * ✅ PŘEČTE OBSAH LOGU PRO ZOBRAZENÍ V DIALOGU
     */
    fun getLogContent(context: Context): String {
        return readFromDownload(context) ?: "Log neexistuje.\nOčekávaná cesta: /Download/$LOG_FILE"
    }

    /**
     * ✅ EXPORT LOGU S ČASOVÝM RAZÍTKEM
     */
    fun exportLog(context: Context): String {
        return try {
            val content = readFromDownload(context)
            if (content.isNullOrEmpty()) {
                return "Log neexistuje"
            }

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val exportFileName = "tankono_log_$timestamp.txt"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, exportFileName)
                    put(MediaStore.Downloads.MIME_TYPE, MIME_TYPE)
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                uri?.let {
                    resolver.openOutputStream(it)?.use { os ->
                        os.write(content.toByteArray())
                    }
                }
                "Log exportován do:\n/Download/$exportFileName"
            } else {
                @Suppress("DEPRECATION")
                val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val file = File(downloadDir, exportFileName)
                file.writeText(content)
                "Log exportován do:\n${file.absolutePath}"
            }
        } catch (e: Exception) {
            "Chyba exportu: ${e.message}"
        }
    }
}