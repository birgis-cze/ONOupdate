package com.tankono.widget

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.material.switchmaterial.SwitchMaterial

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences

    // Komponenty nastavení
    private lateinit var switchN95: SwitchMaterial
    private lateinit var switchN95p: SwitchMaterial
    private lateinit var switchNafta: SwitchMaterial
    private lateinit var switchLpg: SwitchMaterial
    private lateinit var switchHideIcon: SwitchMaterial

    private lateinit var etPeakStart: EditText
    private lateinit var etPeakEnd: EditText
    private lateinit var etPeakInterval: EditText
    private lateinit var etOffPeakInterval: EditText

    private lateinit var btnSave: Button
    private lateinit var btnUpdateNow: Button
    private lateinit var tvLastUpdate: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = PreferenceManager.getDefaultSharedPreferences(this)

        initViews()
        loadSettings()
        setupListeners()
        updateLastUpdateTime()
    }

    private fun initViews() {
        // Přepínače zobrazení položek
        switchN95 = findViewById(R.id.switch_n95)
        switchN95p = findViewById(R.id.switch_n95p)
        switchNafta = findViewById(R.id.switch_nafta)
        switchLpg = findViewById(R.id.switch_lpg)
        switchHideIcon = findViewById(R.id.switch_hide_icon)

        // EditText pro časy
        etPeakStart = findViewById(R.id.et_peak_start)
        etPeakEnd = findViewById(R.id.et_peak_end)
        etPeakInterval = findViewById(R.id.et_peak_interval)
        etOffPeakInterval = findViewById(R.id.et_off_peak_interval)

        // Tlačítka
        btnSave = findViewById(R.id.btn_save)
        btnUpdateNow = findViewById(R.id.btn_update_now)
        tvLastUpdate = findViewById(R.id.tv_last_update)
    }

    private fun loadSettings() {
        // Zobrazené položky
        switchN95.isChecked = prefs.getBoolean("show_n95", true)
        switchN95p.isChecked = prefs.getBoolean("show_n95p", true)
        switchNafta.isChecked = prefs.getBoolean("show_nafta", true)
        switchLpg.isChecked = prefs.getBoolean("show_lpg", true)

        // Skrytí ikony
        switchHideIcon.isChecked = prefs.getBoolean("hide_app_icon", false)

        // Časy
        etPeakStart.setText(prefs.getString("peak_start", "14:30"))
        etPeakEnd.setText(prefs.getString("peak_end", "16:00"))
        etPeakInterval.setText(prefs.getString("peak_interval", "10"))
        etOffPeakInterval.setText(prefs.getString("off_peak_interval", "60"))
    }

    private fun setupListeners() {
        btnSave.setOnClickListener {
            saveSettings()
            Toast.makeText(this, "Nastavení uloženo", Toast.LENGTH_SHORT).show()
        }

        btnUpdateNow.setOnClickListener {
            // Okamžitá aktualizace
            val workRequest = OneTimeWorkRequestBuilder<UpdateWorker>()
                .build()
            WorkManager.getInstance(this).enqueue(workRequest)
            Toast.makeText(this, "Aktualizace spuštěna", Toast.LENGTH_SHORT).show()
        }

        // Skrytí ikony – okamžitá aplikace
        switchHideIcon.setOnCheckedChangeListener { _, isChecked ->
            val state = if (isChecked) {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            }
            packageManager.setComponentEnabledSetting(
                componentName,
                state,
                PackageManager.DONT_KILL_APP
            )
        }
    }

    private fun saveSettings() {
        prefs.edit().apply {
            putBoolean("show_n95", switchN95.isChecked)
            putBoolean("show_n95p", switchN95p.isChecked)
            putBoolean("show_nafta", switchNafta.isChecked)
            putBoolean("show_lpg", switchLpg.isChecked)
            putBoolean("hide_app_icon", switchHideIcon.isChecked)

            putString("peak_start", etPeakStart.text.toString())
            putString("peak_end", etPeakEnd.text.toString())
            putString("peak_interval", etPeakInterval.text.toString())
            putString("off_peak_interval", etOffPeakInterval.text.toString())

            apply()
        }

        // Aktualizace widgetu po změně nastavení
        TankONOWidget.updateAllWidgets(this)

        // Restart scheduleru s novými časy
        TankONOWidgetScheduler.scheduleUpdates(this)
    }

    private fun updateLastUpdateTime() {
        val lastUpdate = DataManager.getLastUpdate(this)
        if (lastUpdate > 0) {
            val date = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            tvLastUpdate.text = "Poslední aktualizace: ${date.format(java.util.Date(lastUpdate))}"
        } else {
            tvLastUpdate.text = "Poslední aktualizace: --:--:--"
        }
    }

    companion object {
        // POMOCNÉ FUNKCE PRO ZÍSKÁNÍ NASTAVENÍ (POUŽÍVANÉ VE WIDGETU)
        fun getVisibleItems(context: Context): List<String> {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            val items = mutableListOf<String>()
            if (prefs.getBoolean("show_n95", true)) items.add("N95")
            if (prefs.getBoolean("show_n95p", true)) items.add("N95+")
            if (prefs.getBoolean("show_nafta", true)) items.add("Nafta")
            if (prefs.getBoolean("show_lpg", true)) items.add("LPG")
            return items
        }

        fun getPeakStart(context: Context): String {
            return PreferenceManager.getDefaultSharedPreferences(context)
                .getString("peak_start", "14:30") ?: "14:30"
        }

        fun getPeakEnd(context: Context): String {
            return PreferenceManager.getDefaultSharedPreferences(context)
                .getString("peak_end", "16:00") ?: "16:00"
        }

        fun getPeakInterval(context: Context): Int {
            val value = PreferenceManager.getDefaultSharedPreferences(context)
                .getString("peak_interval", "10") ?: "10"
            return value.toIntOrNull() ?: 10
        }

        fun getOffPeakInterval(context: Context): Int {
            val value = PreferenceManager.getDefaultSharedPreferences(context)
                .getString("off_peak_interval", "60") ?: "60"
            return value.toIntOrNull() ?: 60
        }
    }
}