package cz.tankono.widget.data.prefs

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import cz.tankono.widget.R
import cz.tankono.widget.util.AppLogger

/**
 * Preferovaná navigační aplikace.
 * SYSTEM = žádná preference, nechá rozhodnout Android.
 */
enum class NavigationApp(
    val id: String,
    val displayName: String,
    val packageName: String?,
    val iconRes: Int
) {
    SYSTEM("system", "Systém", null, R.drawable.ic_nav_system),
    GOOGLE_MAPS("google_maps", "Google Maps", "com.google.android.apps.maps", R.drawable.ic_nav_google_maps),
    MAPY_CZ("mapy_cz", "Mapy.com", "cz.seznam.mapy", R.drawable.ic_nav_mapy_cz),
    WAZE("waze", "Waze", "com.waze", R.drawable.ic_nav_waze);

    companion object {
        fun fromId(id: String?): NavigationApp =
            entries.find { it.id == id } ?: SYSTEM

        /**
         * Vrátí seznam nainstalovaných navigačních aplikací (bez SYSTEM).
         */
        fun installed(context: Context): List<NavigationApp> {
            return entries
                .filter { it != SYSTEM && it.packageName != null }
                .filter { isInstalled(context, it.packageName!!) }
        }

        private fun isInstalled(context: Context, pkg: String): Boolean {
            return try {
                context.packageManager.getPackageInfo(pkg, 0)
                true
            } catch (e: PackageManager.NameNotFoundException) {
                false
            }
        }
    }

    /**
     * Spustí navigaci do zadaného bodu.
     * Pokud je vybraná appka odinstalovaná, fallback na systémové chování.
     */
    fun navigate(context: Context, lat: Double, lng: Double, label: String) {
        AppLogger.i("NavigationApp: navigate() do $lat,$lng (preference=$this)")

        val target = if (packageName != null && !isInstalledSafe(context, packageName)) {
            AppLogger.w("NavigationApp: $displayName není nainstalovaná, fallback na SYSTEM")
            SYSTEM
        } else this

        val intent = when (target) {
            SYSTEM -> {
                val uri = Uri.parse("geo:$lat,$lng?q=$lat,$lng(${Uri.encode(label)})")
                Intent(Intent.ACTION_VIEW, uri)
            }
            GOOGLE_MAPS -> {
                val uri = Uri.parse("google.navigation:q=$lat,$lng")
                Intent(Intent.ACTION_VIEW, uri).apply {
                    setPackage(packageName)
                }
            }
            MAPY_CZ -> {
                val uri = Uri.parse("geo:$lat,$lng?q=$lat,$lng(${Uri.encode(label)})")
                Intent(Intent.ACTION_VIEW, uri).apply {
                    setPackage(packageName)
                }
            }
            WAZE -> {
                val uri = Uri.parse("waze://?ll=$lat,$lng&navigate=yes")
                Intent(Intent.ACTION_VIEW, uri).apply {
                    setPackage(packageName)
                }
            }
        }

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        try {
            context.startActivity(intent)
            AppLogger.i("NavigationApp: spuštěno přes $target")
        } catch (t: Throwable) {
            AppLogger.e("NavigationApp: selhalo ($target), zkouším fallback na geo:", t)

            try {
                val fallback = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("geo:$lat,$lng?q=$lat,$lng(${Uri.encode(label)})")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallback)
            } catch (t2: Throwable) {
                AppLogger.e("NavigationApp: i fallback selhal", t2)
            }
        }
    }

    private fun isInstalledSafe(context: Context, pkg: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(pkg, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
}