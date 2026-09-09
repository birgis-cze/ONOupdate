package com.tankono.widget

import android.content.Context
import android.os.Environment
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
            android.util.Log.d(tag, message)
        } catch (e: Exception) {
            android.util.Log.e("DebugHelper", "Chyba zápisu logu: ${e.message}")
        }
    }

    fun clearLog(context: Context) {
        try {
            val file = getLogFile(context)
            if (file.exists()) file.delete()
            log(context, "DebugHelper", "=== LOG SMAZÁN ===")
        } catch (e: Exception) {
            android.util.Log.e("DebugHelper", "Chyba mazání logu: ${e.message}")
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