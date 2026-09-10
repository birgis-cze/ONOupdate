package com.tankono.widget

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.glance.appwidget.updateAll
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences

    private lateinit var switchN95: SwitchMaterial
    private lateinit var switchN95p: SwitchMaterial
    private lateinit var switchN98: SwitchMaterial
    private lateinit var switchDiesel: SwitchMaterial
    private lateinit var switchDieselPlus: SwitchMaterial
    private lateinit var switchLpg: SwitchMaterial
    private lateinit var switchAdBlue: SwitchMaterial
    private lateinit var switchOm: SwitchMaterial
    private lateinit var switchNm: SwitchMaterial
    private lateinit var switchEuro: SwitchMaterial
    private lateinit var switchHideIcon: SwitchMaterial

    private lateinit var etPeakStart: EditText
    private lateinit var etPeakEnd: EditText
    private lateinit var etPeakInterval: EditText
    private lateinit var etOffPeakInterval: EditText

    private lateinit var btnSave: Button
    private lateinit var btnUpdateNow: Button
    private lateinit var btnShowLog: Button
    private lateinit var btnExportLog: Button
    private lateinit var btnClearLog: Button
    private lateinit var tvLastUpdate: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("tankono_prefs", Context.MODE_PRIVATE)

        initViews()
        loadSettings()
        setupListeners()
        loadAndDisplayData()

        TankONOWidgetScheduler.scheduleUpdates(this)

        val workRequest = OneTimeWorkRequestBuilder<UpdateWorker>().build()
        WorkManager.getInstance(this).enqueue(workRequest)

        // FORCE WIDGET UPDATE
        CoroutineScope(Dispatchers.IO).launch {
            try {
                TankONOWidget().updateAll(this@MainActivity)
                DebugHelper.log(this@MainActivity, "MainActivity", "Widget updateAll volán")
            } catch (e: Exception) {
                DebugHelper.log(this@MainActivity, "MainActivity", "Chyba updateAll: ${e.message}")
            }
        }
    }

    private fun initViews() {
        switchN95 = findViewById(R.id.switch_n95)
        switchN95p = findViewById(R.id.switch_n95p)
        switchN98 = findViewById(R.id.switch_n98)
        switchDiesel = findViewById(R.id.switch_diesel)
        switchDieselPlus = findViewById(R.id.switch_diesel_plus)
        switchLpg = findViewById(R.id.switch_lpg)
        switchAdBlue = findViewById(R.id.switch_adblue)
        switchOm = findViewById(R.id.switch_om)
        switchNm = findViewById(R.id.switch_nm)
        switchEuro = findViewById(R.id.switch_euro)
        switchHideIcon = findViewById(R.id.switch_hide_icon)

        etPeakStart = findViewById(R.id.et_peak_start)
        etPeakEnd = findViewById(R.id.et_peak_end)
        etPeakInterval = findViewById(R.id.et_peak_interval)
        etOffPeakInterval = findViewById(R.id.et_off_peak_interval)

        btnSave = findViewById(R.id.btn_save)
        btnUpdateNow = findViewById(R.id.btn_update_now)
        btnShowLog = findViewById(R.id.btn_show_log)
        btnExportLog = findViewById(R.id.btn_export_log)
        btnClearLog = findViewById(R.id.btn_clear_log)
        tvLastUpdate = findViewById(R.id.tv_last_update)
    }

    private fun loadSettings() {
        switchN95.isChecked = prefs.getBoolean("show_n95", true)
        switchN95p.isChecked = prefs.getBoolean("show_n95p", true)
        switchN98.isChecked = prefs.getBoolean("show_n98", true)
        switchDiesel.isChecked = prefs.getBoolean("show_diesel", true)
        switchDieselPlus.isChecked = prefs.getBoolean("show_diesel_plus", true)
        switchLpg.isChecked = prefs.getBoolean("show_lpg", true)
        switchAdBlue.isChecked = prefs.getBoolean("show_adblue", true)
        switchOm.isChecked = prefs.getBoolean("show_om", true)
        switchNm.isChecked = prefs.getBoolean("show_nm", true)
        switchEuro.isChecked = prefs.getBoolean("show_euro", true)
        switchHideIcon.isChecked = prefs.getBoolean("hide_app_icon", false)

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
            val workRequest = OneTimeWorkRequestBuilder<UpdateWorker>().build()
            WorkManager.getInstance(this).enqueue(workRequest)
            Toast.makeText(this, "Aktualizace spuštěna", Toast.LENGTH_SHORT).show()
            loadAndDisplayData()
        }

        btnShowLog.setOnClickListener {
            val logContent = DebugHelper.getLogContent(this)
            AlertDialog.Builder(this)
                .setTitle("📋 Debug log")
                .setMessage(logContent)
                .setPositiveButton("OK", null)
                .setNeutralButton("Smazat log") { _, _ ->
                    DebugHelper.clearLog(this)
                    Toast.makeText(this, "Log smazán", Toast.LENGTH_SHORT).show()
                }
                .show()
        }

        btnExportLog.setOnClickListener {
            val result = DebugHelper.exportLog(this)
            Toast.makeText(this, result, Toast.LENGTH_LONG).show()
        }

        btnClearLog.setOnClickListener {
            DebugHelper.clearLog(this)
            Toast.makeText(this, "Log smazán", Toast.LENGTH_SHORT).show()
        }

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
            putBoolean("show_n98", switchN98.isChecked)
            putBoolean("show_diesel", switchDiesel.isChecked)
            putBoolean("show_diesel_plus", switchDieselPlus.isChecked)
            putBoolean("show_lpg", switchLpg.isChecked)
            putBoolean("show_adblue", switchAdBlue.isChecked)
            putBoolean("show_om", switchOm.isChecked)
            putBoolean("show_nm", switchNm.isChecked)
            putBoolean("show_euro", switchEuro.isChecked)
            putBoolean("hide_app_icon", switchHideIcon.isChecked)

            putString("peak_start", etPeakStart.text.toString())
            putString("peak_end", etPeakEnd.text.toString())
            putString("peak_interval", etPeakInterval.text.toString())
            putString("off_peak_interval", etOffPeakInterval.text.toString())

            apply()
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                TankONOWidget().updateAll(this@MainActivity)
            } catch (e: Exception) {
                DebugHelper.log(this@MainActivity, "MainActivity", "Chyba updateAll: ${e.message}")
            }
        }
        TankONOWidgetScheduler.scheduleUpdates(this)
    }

    private fun loadAndDisplayData() {
        val lastUpdate = DataManager.getLastUpdate(this)
        val lastChange = DataManager.getLastChangeDate(this)

        val text = StringBuilder()
        text.append("Poslední změna cen: ")
        if (lastChange > 0) {
            val date = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
            text.append(date.format(Date(lastChange)))
        } else {
            text.append("--")
        }

        text.append("\nPoslední aktualizace: ")
        if (lastUpdate > 0) {
            val date = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            text.append(date.format(Date(lastUpdate)))
        } else {
            text.append("--")
        }

        tvLastUpdate.text = text.toString()
    }

    companion object {
        fun getVisibleItems(context: Context): List<Pair<String, String>> {
            val prefs = context.getSharedPreferences("tankono_prefs", Context.MODE_PRIVATE)
            val items = mutableListOf<Pair<String, String>>()

            if (prefs.getBoolean("show_n95", true)) items.add("n95" to "N95")
            if (prefs.getBoolean("show_n95p", true)) items.add("n95p" to "N95+")
            if (prefs.getBoolean("show_n98", true)) items.add("n98" to "NATURAL 98")
            if (prefs.getBoolean("show_diesel", true)) items.add("diesel" to "DIESEL")
            if (prefs.getBoolean("show_diesel_plus", true)) items.add("dieselPlus" to "DIESEL+")
            if (prefs.getBoolean("show_lpg", true)) items.add("lpg" to "LPG")
            if (prefs.getBoolean("show_adblue", true)) items.add("adBlue" to "AD BLUE")
            if (prefs.getBoolean("show_om", true)) items.add("om" to "OM (osobní)")
            if (prefs.getBoolean("show_nm", true)) items.add("nm" to "NM (nákladní)")
            if (prefs.getBoolean("show_euro", true)) items.add("euro" to "EUR")

            return items
        }

        fun getPeakStart(context: Context): String {
            val prefs = context.getSharedPreferences("tankono_prefs", Context.MODE_PRIVATE)
            return prefs.getString("peak_start", "14:30") ?: "14:30"
        }

        fun getPeakEnd(context: Context): String {
            val prefs = context.getSharedPreferences("tankono_prefs", Context.MODE_PRIVATE)
            return prefs.getString("peak_end", "16:00") ?: "16:00"
        }

        fun getPeakInterval(context: Context): Int {
            val prefs = context.getSharedPreferences("tankono_prefs", Context.MODE_PRIVATE)
            val value = prefs.getString("peak_interval", "10") ?: "10"
            return value.toIntOrNull() ?: 10
        }

        fun getOffPeakInterval(context: Context): Int {
            val prefs = context.getSharedPreferences("tankono_prefs", Context.MODE_PRIVATE)
            val value = prefs.getString("off_peak_interval", "60") ?: "60"
            return value.toIntOrNull() ?: 60
        }
    }
}