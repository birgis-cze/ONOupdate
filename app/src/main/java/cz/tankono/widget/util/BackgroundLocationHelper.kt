package cz.tankono.widget.util

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

object BackgroundLocationHelper {

    const val REQUEST_BACKGROUND = 1001

    fun hasForeground(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasBackground(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return hasForeground(context)
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_BACKGROUND_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Vrátí true, pokud uživatel má foreground, ale chybí background (a Android to vyžaduje).
     */
    fun needsBackgroundRequest(context: Context): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
               hasForeground(context) &&
               !hasBackground(context)
    }

    /**
     * Vyžádá background location. Předpokládá, že foreground už je udělený.
     * Na Androidu 10 zobrazí dialog, na 11+ otevře systémové nastavení.
     */
    fun requestBackground(activity: Activity, launcher: (String) -> Unit) {
        if (!hasForeground(activity)) {
            AppLogger.w("BackgroundLocation: chybí foreground, nelze žádat background")
            return
        }
        if (hasBackground(activity)) {
            AppLogger.d("BackgroundLocation: už uděleno")
            return
        }

        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
            // Android 10 – klasický dialog
            AppLogger.i("BackgroundLocation: Android 10, žádám dialog")
            launcher(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ – dialog nelze, otevřít nastavení
            AppLogger.i("BackgroundLocation: Android 11+, otevírám nastavení")
            openAppSettings(activity)
        }
    }

    fun openAppSettings(activity: Activity) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", activity.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            activity.startActivity(intent)
        } catch (t: Throwable) {
            AppLogger.w("BackgroundLocation: nelze otevřít nastavení: ${t.message}")
        }
    }
}