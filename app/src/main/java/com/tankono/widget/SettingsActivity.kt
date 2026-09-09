package com.tankono.widget

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.*

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportFragmentManager
            .beginTransaction()
            .replace(android.R.id.content, SettingsFragment())
            .commit()
    }

    class SettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences, rootKey)
        }

        // SKRYTÍ IKONY APLIKACE ZE SEZNAMU
        val hideIconPref = findPreference<SwitchPreferenceCompat>("hide_app_icon")
        hideIconPref?.setOnPreferenceChangeListener { _, newValue ->
            val hide = newValue as Boolean
            if (hide) {
                packageManager.setComponentEnabledSetting(
                    ComponentName(requireContext(), MainActivity::class.java),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
            } else {
                packageManager.setComponentEnabledSetting(
                    ComponentName(requireContext(), MainActivity::class.java),
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
            true
        }
    }

    companion object {
        // NASTAVENÍ – KTERÉ POLOŽKY SE ZOBRAZUJÍ
        fun getVisibleItems(context: Context): List<String> {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            val items = mutableListOf<String>()
            if (prefs.getBoolean("show_n95", true)) items.add("N95")
            if (prefs.getBoolean("show_n95p", true)) items.add("N95+")
            if (prefs.getBoolean("show_nafta", true)) items.add("Nafta")
            if (prefs.getBoolean("show_lpg", true)) items.add("LPG")
            return items
        }

        // ČASY OBNOVY
        fun getPeakStart(context: Context): String {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            return prefs.getString("peak_start", "14:30") ?: "14:30"
        }

        fun getPeakEnd(context: Context): String {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            return prefs.getString("peak_end", "16:00") ?: "16:00"
        }

        fun getPeakInterval(context: Context): Int {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            return prefs.getString("peak_interval", "10")?.toIntOrNull() ?: 10
        }

        fun getOffPeakInterval(context: Context): Int {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            return prefs.getString("off_peak_interval", "60")?.toIntOrNull() ?: 60
        }
    }
}