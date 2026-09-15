package cz.tankono.widget.util

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Jednoduchý logger, který:
 *  - píše do Android Logcat (pro vývoj)
 *  - ukládá do souboru `app_log.txt` v interním úložišti aplikace
 *
 * Log soubor přežije restart aplikace. Slouží k diagnostice.
 * Max velikost: 200 kB (pak se rotuje – starý se přepíše).
 */
object AppLogger {

    private const val TAG = "TankONO"
    private const val LOG_FILE = "app_log.txt"
    private const val MAX_SIZE = 200 * 1024  // 200 kB

    private val timeFmt = SimpleDateFormat("HH:mm:ss.SSS", Locale("cs", "CZ"))

    @Volatile
    private var appContext: Context? = null

    /** Zavolat jednou při startu aplikace (v MainActivity.onCreate). */
    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun d(message: String) {
        Log.d(TAG, message)
        write("D", message)
    }

    fun i(message: String) {
        Log.i(TAG, message)
        write("I", message)
    }

    fun w(message: String) {
        Log.w(TAG, message)
        write("W", message)
    }

    fun e(message: String, throwable: Throwable? = null) {
        Log.e(TAG, message, throwable)
        val full = if (throwable != null)
            "$message\n${throwable.stackTraceToString()}"
        else message
        write("E", full)
    }

    private fun write(level: String, message: String) {
        val ctx = appContext ?: return
        try {
            val file = File(ctx.filesDir, LOG_FILE)
            if (file.exists() && file.length() > MAX_SIZE) {
                file.delete()
            }
            val line = "${timeFmt.format(Date())} [$level] $message\n"
            file.appendText(line)
        } catch (t: Throwable) {
            Log.e(TAG, "Nelze zapsat do log souboru", t)
        }
    }

    /** Vrátí obsah logu jako String. */
    fun readLog(context: Context): String {
        return try {
            val file = File(context.filesDir, LOG_FILE)
            if (file.exists()) file.readText() else "(log je prázdný)"
        } catch (t: Throwable) {
            "Chyba při čtení logu: ${t.message}"
        }
    }

    /** Smaže log soubor. */
    fun clearLog(context: Context) {
        try {
            File(context.filesDir, LOG_FILE).delete()
        } catch (_: Throwable) {
        }
    }

    /** Vrátí File objekt logu (pro sdílení). */
    fun getLogFile(context: Context): File {
        return File(context.filesDir, LOG_FILE)
    }
    /** Vytvoří soubor s logem pro sdílení. */
    fun createExportFile(context: Context): File {
        val exportFile = File(context.cacheDir, "tankono_log_export.txt")
        exportFile.writeText(readLog(context))
        return exportFile
    }
}