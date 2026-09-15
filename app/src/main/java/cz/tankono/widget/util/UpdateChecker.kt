package cz.tankono.widget.util

import android.content.Context
import cz.tankono.widget.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object UpdateChecker {

    private const val OWNER = "birgis-cze"
    private const val REPO = "ONOupdate"

    data class UpdateInfo(
        val version: String,
        val downloadApiUrl: String,
        val releaseNotes: String?
    )

    /**
     * Zkontroluje GitHub Releases a vrátí info o novější verzi, nebo null.
     * Pro privátní repo používá token z BuildConfig.
     */
    suspend fun checkForUpdate(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://api.github.com/repos/$OWNER/$REPO/releases/latest")
            val connection = url.openConnection() as HttpURLConnection

            connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
            connection.setRequestProperty("User-Agent", "TankONO-Widget")

            val token = BuildConfig.GH_TOKEN
            if (token.isNotEmpty()) {
                connection.setRequestProperty("Authorization", "Bearer $token")
                AppLogger.d("UpdateChecker: token použit")
            } else {
                AppLogger.w("UpdateChecker: token NENÍ k dispozici")
            }

            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000

            val responseCode = connection.responseCode
            AppLogger.d("UpdateChecker: HTTP $responseCode")

            if (responseCode != 200) {
                AppLogger.w("UpdateChecker: neočekávaný kód $responseCode")
                return@withContext null
            }

            val response = connection.inputStream.bufferedReader().readText()
            val json = JSONObject(response)

            val latestVersion = json.getString("tag_name")
            val releaseNotes = if (json.has("body")) json.getString("body") else null

            // Najít APK asset – použít API URL (funguje s tokenem)
            val assets = json.getJSONArray("assets")
            var downloadApiUrl: String? = null
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.getString("name")
                if (name.endsWith(".apk")) {
                    downloadApiUrl = asset.getString("url")   // API URL, ne browser_download_url
                    break
                }
            }

            if (downloadApiUrl == null) {
                AppLogger.w("UpdateChecker: APK asset nenalezen")
                return@withContext null
            }

            val currentVersion = BuildConfig.VERSION_NAME
            val isNewer = isNewerVersion(latestVersion, currentVersion)

            AppLogger.d("UpdateChecker: latest=$latestVersion, current=$currentVersion, isNewer=$isNewer")

            if (isNewer) {
                UpdateInfo(latestVersion, downloadApiUrl, releaseNotes)
            } else {
                null
            }
        } catch (t: Throwable) {
            AppLogger.e("UpdateChecker: chyba", t)
            null
        }
    }

    /**
     * Stáhne APK z GitHub API URL (s tokenem) do cache.
     * Vrací File, nebo null při chybě.
     */
    suspend fun downloadApk(context: Context, apiUrl: String): File? = withContext(Dispatchers.IO) {
        try {
            val url = URL(apiUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.setRequestProperty("Accept", "application/octet-stream")
            connection.setRequestProperty("User-Agent", "TankONO-Widget")

            val token = BuildConfig.GH_TOKEN
            if (token.isNotEmpty()) {
                connection.setRequestProperty("Authorization", "Bearer $token")
            }

            connection.connectTimeout = 30_000
            connection.readTimeout = 60_000

            val responseCode = connection.responseCode
            AppLogger.d("UpdateChecker.downloadApk: HTTP $responseCode")

            if (responseCode != 200) {
                AppLogger.w("UpdateChecker.downloadApk: neočekávaný kód $responseCode")
                return@withContext null
            }

            val file = File(context.cacheDir, "update.apk")
            // Smazat starý soubor, pokud existuje
            if (file.exists()) file.delete()

            connection.inputStream.use { input ->
                file.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            AppLogger.i("UpdateChecker.downloadApk: staženo do ${file.absolutePath} (${file.length()} B)")
            file
        } catch (t: Throwable) {
            AppLogger.e("UpdateChecker.downloadApk: chyba", t)
            null
        }
    }

    private fun isNewerVersion(latest: String, current: String): Boolean {
        val l = latest.removePrefix("v").split(".").mapNotNull { it.toIntOrNull() }
        val c = current.removePrefix("v").split(".").mapNotNull { it.toIntOrNull() }

        val maxLen = maxOf(l.size, c.size)
        for (i in 0 until maxLen) {
            val lv = l.getOrElse(i) { 0 }
            val cv = c.getOrElse(i) { 0 }
            if (lv > cv) return true
            if (lv < cv) return false
        }
        return false
    }
}