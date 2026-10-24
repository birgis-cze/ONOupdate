package cz.tankono.widget.util

import android.content.Context
import android.media.MediaDrm
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.UUID

/**
 * Odešle jednorázově hash zařízení na Google Apps Script endpoint.
 * Slouží pouze pro orientační statistiku počtu instalací.
 *
 * - Odesílá se POUZE JEDNOU za život aplikace (flag v SharedPreferences).
 * - Posílá se hash (SHA-256 z MediaDrm ID / Android ID + sůl), nikdy surové ID.
 * - Pokud odeslání selže, zkusí to znovu při dalším spuštění.
 */
object InstallReporter {

    // === ZMĚŇ NA SVOJE HODNOTY ===
    private const val ENDPOINT_URL = "https://script.google.com/macros/s/AKfycbwyeSZO33wBOIG6dhGGGbin8FcFUB_ZLnG6t6Nlm-fW59sRMrurQvuBCZY5Zc0_aNLJ/exec"
    private const val ADMIN_TOKEN  = "4f4a0ba5-b5f3-43a2-a2fb-72b3f3eae428"

    // Sůl pro hash – změň na nějaký náhodný string (např. UUID)
    private const val SALT = "4f4a0ba5-b5f3-43a2-a2fb-72b3f3eae428"

    private const val PREFS_NAME = "install_reporter"
    private const val KEY_SENT = "sent"

    // Scope pro odesílání na pozadí
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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

        val appContext = context.applicationContext
        scope.launch {
            val ok = send(appContext)
            if (ok) {
                prefs.edit().putBoolean(KEY_SENT, true).apply()
                AppLogger.i("InstallReporter: hash odeslán")
            } else {
                AppLogger.w("InstallReporter: odeslání selhalo, zkusí se příště")
            }
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
            // Statistika není kritická – selhání sítě = [W], ne [E]
            AppLogger.w("InstallReporter.fetchTotalCount: chyba: ${t.message}")
            null
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

            val payload = JSONObject().apply {
                put("hash", hash)
                put("token", ADMIN_TOKEN)
            }.toString()

            OutputStreamWriter(conn.outputStream, "UTF-8").use { it.write(payload) }

            val code = conn.responseCode
            if (code == 200) {
                val response = conn.inputStream.bufferedReader().readText()
                AppLogger.d("InstallReporter: odpověď = $response")
                true
            } else {
                AppLogger.w("InstallReporter: HTTP $code")
                false
            }
        } catch (t: Throwable) {
            // Selhání sítě = [W], zkusí se příště
            AppLogger.w("InstallReporter: odeslání selhalo: ${t.message}")
            false
        }
    }

    /**
     * Vytvoří hash ze zařízení.
     * Primárně používá stabilní MediaDrm ID, fallback na ANDROID_ID.
     * Formát výsledku: "ID_" + SHA-256(deviceId + SALT)
     */
    private fun buildDeviceHash(context: Context): String? {
        return try {
            var deviceId = getMediaDrmId()
            
            if (deviceId.isNullOrBlank()) {
                AppLogger.d("InstallReporter: MediaDrm ID nedostupné, zkouším ANDROID_ID")
                deviceId = Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ANDROID_ID
                )
            }

            if (deviceId.isNullOrBlank() || deviceId == "9774d56d682e549c") {
                return null
            }

            val input = (deviceId + SALT).toByteArray(Charsets.UTF_8)
            val digest = MessageDigest.getInstance("SHA-256")
            val bytes = digest.digest(input)
            
            // Přidáme prefix "ID_" před plný hex hash
            "ID_" + bytes.joinToString("") { "%02x".format(it) }
        } catch (t: Throwable) {
            // Raritní (emulátory, poškozený Android ID) = [W], ne [E]
            AppLogger.w("InstallReporter: hash error: ${t.message}")
            null
        }
    }

    /**
     * Získá stabilní ID zařízení přes MediaDrm (Widevine).
     * Přežije vymazání dat aplikace i přeinstalaci.
     */
    private fun getMediaDrmId(): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2) return null
        try {
            val widevineUuid = UUID.fromString("edef8ba9-79d6-4ace-a3c8-27dcd51d21ed")
            val mediaDrm = MediaDrm(widevineUuid)
            val deviceUniqueIdArray = mediaDrm.getPropertyByteArray(MediaDrm.PROPERTY_DEVICE_UNIQUE_ID)
            mediaDrm.close()
            return deviceUniqueIdArray.joinToString("") { "%02x".format(it) }
        } catch (t: Throwable) {
            return null
        }
    }
}