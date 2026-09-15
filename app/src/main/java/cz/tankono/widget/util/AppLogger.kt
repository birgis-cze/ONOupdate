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
 * Log soubor přežije restart aplikace.
 *
 * Rotace:
 *  - při startu aplikace se odstraní záznamy starší než 24 h
 *  - záznamy se špatným tvarem (bez parsovatelného timestampu) se také odstraní
 *  - žádná velikostní rotace (soubor nikdy nebude tak velký)
 */
object AppLogger {

    private const val TAG = "TankONO"
    private const val LOG_FILE = "app_log.txt"
    private const val MAX_AGE_MS = 24L * 60 * 60 * 1000  // 24 h

    private val fullFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale("cs", "CZ"))

    @Volatile
    private var appContext: Context? = null

    /**
     * Zavolat jednou při startu aplikace (v MainActivity.onCreate).
     * Provede pročištění logu – odstraní záznamy starší než 24 h
     * a záznamy se špatným tvarem (např. ze staré verze bez data).
     */
    fun init(context: Context) {
        appContext = context.applicationContext
        cleanOldEntries(context.applicationContext)
    }

    /**
     * Při startu odstraní:
     * - záznamy starší než 24 h
     * - záznamy se špatným tvarem (neplatný timestamp)
     */
    private fun cleanOldEntries(context: Context) {
        try {
            val file = File(context.filesDir, LOG_FILE)
            if (!file.exists()) return

            val cutoff = System.currentTimeMillis() - MAX_AGE_MS
            val lines = file.readLines()

            val filtered = lines.filter { line ->
                val ts = parseTimestamp(line)
                // Zachovat pouze řádky s platným timestampem >= cutoff
                ts != null && ts >= cutoff
            }

            val removed = lines.size - filtered.size
            if (removed > 0) {
                file.writeText(filtered.joinToString("\n"))
                Log.i(TAG, "Log: odstraněno $removed záznamů (staré nebo neplatné)")
                // Přidat značku o rotaci
                val marker = "${fullFmt.format(Date())} [I] === Log pročištěn " +
                             "(odstraněno $removed záznamů) ===\n"
                file.appendText(if (filtered.isEmpty()) marker else "\n$marker")
            } else {
                Log.d(TAG, "Log: žádné záznamy k odstranění")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Nelze pročistit log", t)
        }
    }

    /**
     * Parsuje timestamp ze začátku řádku.
     * Formát: "2026-09-15 15:38:42.123 [I] ..."
     *
     * @return timestamp v ms, nebo null pokud řádek nemá platný timestamp
     */
    private fun parseTimestamp(line: String): Long? {
        return try {
            if (line.length < 23) return null
            val dateStr = line.substring(0, 23)
            // Strict parse – SimpleDateFormat je defaultně benevolentní,
            // ale substring(0,23) vynutí přesnou délku
            fullFmt.parse(dateStr)?.time
        } catch (_: Throwable) {
            null
        }
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
            val line = "${fullFmt.format(Date())} [$level] $message\n"
            file.appendText(line)
        } catch (t: Throwable) {
            Log.e(TAG, "Nelze zapsat do log souboru", t)
        }
    }

    fun readLog(context: Context): String {
        return try {
            val file = File(context.filesDir, LOG_FILE)
            if (file.exists()) file.readText() else "(log je prázdný)"
        } catch (t: Throwable) {
            "Chyba při čtení logu: ${t.message}"
        }
    }

    fun clearLog(context: Context) {
        try {
            File(context.filesDir, LOG_FILE).delete()
        } catch (_: Throwable) {
        }
    }

    fun getLogFile(context: Context): File {
        return File(context.filesDir, LOG_FILE)
    }

    fun createExportFile(context: Context): File {
        val exportFile = File(context.cacheDir, "tankono_log_export.txt")
        exportFile.writeText(readLog(context))
        return exportFile
    }
}