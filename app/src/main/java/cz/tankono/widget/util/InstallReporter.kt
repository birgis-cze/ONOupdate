package cz.tankono.widget.util

import android.content.Context
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Odešle jednorázově hash zařízení na Google Apps Script endpoint.
 * Slouží pouze pro orientační statistiku počtu instalací.
 *
 * - Odesílá se POUZE JEDNOU za život aplikace (flag v SharedPreferences).
 * - Posílá se hash (SHA-256 z Android ID + sůl), nikdy surové ID.
 * - Pokud odeslání selže, zkusí to znovu při dalším spuštění.
 */
object InstallReporter {

    // === ZMĚŇ NA SVOJE HODNOTY ===
    private const val ENDPOINT_URL = "https://script.google.com/macros/s/AKfycbwyeSZO33wBOIG6dhGGGbin8FcFUB_ZLnG6t6Nlm-fW59sRMrurQvuBCZY5Zc0_aNLJ/exec"
    private const val ADMIN_TOKEN  = "4f4a0ba5-b5f3-43a2-a2fb-72b3f3eae428"  // stejný jako v Apps Script

    // Sůl pro hash – změň na nějaký náhodný string (např. UUID)
    private const val SALT = "onoupdate-salt-zmen-me-2026"

    private const val PREFS_NAME = "install_reporter"
    private const val KEY_SENT = "sent"

    /**
     * Zavolej v MainActivity.onCreate().
     * Pokud už bylo odesláno, nic nedělá.
     */
    fun reportIfNeeded(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_SENT, false)) {
            AppLogger.d("InstallReporter: již odesláno, přeskakuji")
            return
        }

        // Spustit na pozadí, aby to neblokovalo UI
        kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
            val ok = send(context)
            if (ok) {
                prefs.edit().putBoolean(KEY_SENT, true).apply()
                AppLogger.i("InstallReporter: hash odeslán")
            } else {
                AppLogger.w("InstallReporter: odeslání selhalo, zkusí se příště")
            }
        }
    }

    /**
     * Vlastní odeslání.
     */
    private suspend fun send(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            val hash = buildDeviceHash(context)
            if (hash.isNullOrBlank()) {
                AppLogger.w("InstallReporter: nelze získat device ID")
                return@withContext false
            }

            val url = URL(ENDPOINT_URL)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            conn.doOutput = true
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000

            // Tělo požadavku
            val payload = JSONObject().apply {
                put("hash", hash)
                put("token", ADMIN_TOKEN)
            }.toString()

            OutputStreamWriter(conn.outputStream, "UTF-8").use { it.write(payload) }

            val code = conn.responseCode
            if (code == 200) {
                // Přečíst odpověď (nepovinné, jen pro log)
                val response = conn.inputStream.bufferedReader().readText()
                AppLogger.d("InstallReporter: odpověď = $response")
                true
            } else {
                AppLogger.w("InstallReporter: HTTP $code")
                false
            }
        } catch (t: Throwable) {
            AppLogger.e("InstallReporter: chyba", t)
            false
        }
    }

    /**
     * Vytvoří hash ze zařízení: SHA-256(ANDROID_ID + SALT).
     * Vrací null, pokud se nepodaří ID získat.
     */
    private fun buildDeviceHash(context: Context): String? {
        return try {
            val androidId = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ANDROID_ID
            ) ?: return null

            if (androidId.isBlank() || androidId == "9774d56d682e549c") {
                // Druhé je známé vadné ID ze starých emulátorů
                return null
            }

            val input = (androidId + SALT).toByteArray(Charsets.UTF_8)
            val digest = MessageDigest.getInstance("SHA-256")
            val bytes = digest.digest(input)
            bytes.joinToString("") { "%02x".format(it) }
        } catch (t: Throwable) {
            AppLogger.e("InstallReporter: hash error", t)
            null
        }
    }
    
    /**
     * Získá aktuální počet evidovaných zařízení z Apps Scriptu.
     * Vrací null při chybě.
     */
    suspend fun fetchTotalCount(): Int? = withContext(Dispatchers.IO) {
        try {
            val url = URL("$ENDPOINT_URL?token=$ADMIN_TOKEN")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000

            val code = conn.responseCode
            if (code != 200) {
                AppLogger.w("InstallReporter.fetchTotalCount: HTTP $code")
                return@withContext null
            }

            val response = conn.inputStream.bufferedReader().readText()
            val json = JSONObject(response)
            if (json.optBoolean("ok", false)) {
                json.optInt("total", -1).takeIf { it >= 0 }
            } else {
                AppLogger.w("InstallReporter.fetchTotalCount: ${json.optString("error")}")
                null
            }
        } catch (t: Throwable) {
            AppLogger.e("InstallReporter.fetchTotalCount: chyba", t)
            null
        }
    }

    /**
     * Vrátí hash tohoto zařízení (stejný, jaký se odesílá na server).
     * Slouží pro transparentní zobrazení v nastavení.
     * Vrací null, pokud hash nelze získat.
     */
    fun getDeviceHash(context: Context): String? {
        return buildDeviceHash(context)
    }
}