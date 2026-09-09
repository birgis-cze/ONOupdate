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
    
    fun log(context: Context, tag: String, message: String) {
        if (!isEnabled) return
        
        try {
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date())
            val logLine = "$timestamp [$tag] $message\n"
            
            // Zápis do souboru
            val file = File(context.getExternalFilesDir(null), LOG_FILE)
            FileOutputStream(file, true).use { fos ->
                fos.write(logLine.toByteArray())
            }
            
            // Také do Logcat
            android.util.Log.d(tag, message)
            
        } catch (e: Exception) {
            android.util.Log.e("DebugHelper", "Chyba zápisu logu: ${e.message}")
        }
    }
    
    fun clearLog(context: Context) {
        try {
            val file = File(context.getExternalFilesDir(null), LOG_FILE)
            if (file.exists()) {
                file.delete()
            }
            log(context, "DebugHelper", "=== LOG SMAZÁN ===")
        } catch (e: Exception) {
            android.util.Log.e("DebugHelper", "Chyba mazání logu: ${e.message}")
        }
    }
    
    fun getLogContent(context: Context): String {
        return try {
            val file = File(context.getExternalFilesDir(null), LOG_FILE)
            if (file.exists()) {
                file.readText()
            } else {
                "Log soubor neexistuje"
            }
        } catch (e: Exception) {
            "Chyba čtení logu: ${e.message}"
        }
    }
}