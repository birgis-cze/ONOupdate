package cz.tankono.widget.util

import android.content.Context
import cz.tankono.widget.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object UpdateChecker {

    private const val OWNER = "birgis-cze"
    private const val REPO = "ONOupdate"

    data class UpdateInfo(
        val version: String,
        val downloadUrl: String,
        val releaseNotes: String?
    )

    /**
     * Zkontroluje GitHub Releases a vrátí info o novější verzi, nebo null.
     */
    suspend fun checkForUpdate(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://api.github.com/repos/$OWNER/$REPO/releases/latest")
            val connection = url.openConnection() as HttpURLConnection
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000

            val response = connection.inputStream.bufferedReader().readText()
            val json = JSONObject(response)

            val latestVersion = json.getString("tag_name")   // např. "v1.1"
            val releaseNotes = json.optString("body", null)

            // Najít APK asset
            val assets = json.getJSONArray("assets")
            var downloadUrl: String? = null
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.getString("name")
                if (name.endsWith(".apk")) {
                    downloadUrl = asset.getString("browser_download_url")
                    break
                }
            }

            if (downloadUrl == null) {
                AppLogger.w("UpdateChecker: APK asset nenalezen v release")
                return@withContext null
            }

            // Porovnat verze
            val currentVersion = BuildConfig.VERSION_NAME   // např. "1.0"
            val isNewer = isNewerVersion(latestVersion, currentVersion)

            AppLogger.d("UpdateChecker: latest=$latestVersion, current=$currentVersion, isNewer=$isNewer")

            if (isNewer) {
                UpdateInfo(latestVersion, downloadUrl, releaseNotes)
            } else {
                null
            }
        } catch (t: Throwable) {
            AppLogger.e("UpdateChecker: chyba", t)
            null
        }
    }

    /** Porovná verze – "v1.1" vs "1.0" → true. */
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