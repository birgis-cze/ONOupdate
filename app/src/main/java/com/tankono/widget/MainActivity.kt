package com.tankono.widget

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    private lateinit var tvPeakStart: TextView
    private lateinit var tvPeakEnd: TextView
    private lateinit var tvPeakInterval: TextView
    private lateinit var tvOffPeakInterval: TextView
    private lateinit var tvWidgetTextSize: TextView

    private var peakStartMinutes = 14 * 60 + 30
    private var peakEndMinutes = 16 * 60 + 0
    private var peakInterval = 10
    private var offPeakInterval = 60
    private var widgetTextSize = 12

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("tankono_prefs", Context.MODE_PRIVATE)

        initViews()
        loadSettings()
        setupListeners()
        updateAllTexts()
        loadAndDisplayData()

        TankONOWidgetScheduler.scheduleUpdates(this)

        // Inicializace
        CoroutineScope(Dispatchers.IO).launch {
            try {
                DebugHelper.log(this@MainActivity, "MainActivity", "=== START ===")
                val workRequest = OneTimeWorkRequestBuilder<UpdateWorker>().build()
                WorkManager.getInstance(this@MainActivity).enqueue(workRequest)
                delay(3000)

                // ✅ POUŽIJEME updateAllWidgetsState (Glance state)
                withContext(Dispatchers.Main) {
                    updateAllWidgetsState(this@MainActivity)
                }
                DebugHelper.log(this@MainActivity, "MainActivity", "✅ Widgety aktualizovány po startu")
            } catch (e: Exception) {
                DebugHelper.log(this@MainActivity, "MainActivity", "Chyba: ${e.message}")
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

        tvPeakStart = findViewById(R.id.tv_peak_start)
        tvPeakEnd = findViewById(R.id.tv_peak_end)
        tvPeakInterval = findViewById(R.id.tv_peak_interval)
        tvOffPeakInterval = findViewById(R.id.tv_off_peak_interval)
        tvWidgetTextSize = findViewById(R.id.tv_widget_text_size)

        findViewById<Button>(R.id.btn_save).setOnClickListener {
            saveSettings()
            Toast.makeText(this, "Nastavení uloženo", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btn_update_now).setOnClickListener {
            val workRequest = OneTimeWorkRequestBuilder<UpdateWorker>().build()
            WorkManager.getInstance(this).enqueue(workRequest)
            Toast.makeText(this, "Aktualizace spuštěna", Toast.LENGTH_SHORT).show()
            CoroutineScope(Dispatchers.Main).launch {
                delay(3000)
                loadAndDisplayData()
            }
        }

        findViewById<Button>(R.id.btn_show_log).setOnClickListener {
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

        findViewById<Button>(R.id.btn_export_log).setOnClickListener {
            val result = DebugHelper.exportLog(this)
            Toast.makeText(this, result, Toast.LENGTH_LONG).show()
        }

        findViewById<Button>(R.id.btn_clear_log).setOnClickListener {
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

    private fun setupListeners() {
        findViewById<Button>(R.id.btn_peak_start_minus).setOnClickListener {
            peakStartMinutes = (peakStartMinutes - 15 + 24 * 60) % (24 * 60)
            updateAllTexts()
        }
        findViewById<Button>(R.id.btn_peak_start_plus).setOnClickListener {
            peakStartMinutes = (peakStartMinutes + 15) % (24 * 60)
            updateAllTexts()
        }
        findViewById<Button>(R.id.btn_peak_end_minus).setOnClickListener {
            peakEndMinutes = (peakEndMinutes - 15 + 24 * 60) % (24 * 60)
            updateAllTexts()
        }
        findViewById<Button>(R.id.btn_peak_end_plus).setOnClickListener {
            peakEndMinutes = (peakEndMinutes + 15) % (24 * 60)
            updateAllTexts()
        }

        findViewById<Button>(R.id.btn_peak_interval_minus).setOnClickListener {
            if (peakInterval > 5) peakInterval -= 5
            updateAllTexts()
        }
        findViewById<Button>(R.id.btn_peak_interval_plus).setOnClickListener {
            if (peakInterval < 120) peakInterval += 5
            updateAllTexts()
        }
        findViewById<Button>(R.id.btn_off_peak_interval_minus).setOnClickListener {
            if (offPeakInterval > 5) offPeakInterval -= 5
            updateAllTexts()
        }
        findViewById<Button>(R.id.btn_off_peak_interval_plus).setOnClickListener {
            if (offPeakInterval < 240) offPeakInterval += 5
            updateAllTexts()
        }

        findViewById<Button>(R.id.btn_widget_text_minus).setOnClickListener {
            if (widgetTextSize > 10) widgetTextSize -= 2
            updateAllTexts()
        }
        findViewById<Button>(R.id.btn_widget_text_plus).setOnClickListener {
            if (widgetTextSize < 20) widgetTextSize += 2
            updateAllTexts()
        }
    }

    private fun updateAllTexts() {
        tvPeakStart.text = formatMinutes(peakStartMinutes)
        tvPeakEnd.text = formatMinutes(peakEndMinutes)
        tvPeakInterval.text = peakInterval.toString()
        tvOffPeakInterval.text = offPeakInterval.toString()
        tvWidgetTextSize.text = "${widgetTextSize} sp"
    }

    private fun formatMinutes(totalMinutes: Int): String {
        val h = (totalMinutes / 60) % 24
        val m = totalMinutes % 60
        return String.format(Locale.getDefault(), "%02d:%02d", h, m)
    }

    private fun parseTimeToMinutes(time: String): Int {
        return try {
            val parts = time.split(":")
            parts[0].toInt() * 60 + parts[1].toInt()
        } catch (e: Exception) {
            0
        }
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

        peakStartMinutes = parseTimeToMinutes(prefs.getString("peak_start", "14:30") ?: "14:30")
        peakEndMinutes = parseTimeToMinutes(prefs.getString("peak_end", "16:00") ?: "16:00")
        peakInterval = prefs.getInt("peak_interval_int", 10)
        offPeakInterval = prefs.getInt("off_peak_interval_int", 60)
        widgetTextSize = prefs.getInt("widget_text_size", 12)
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

            putString("peak_start", formatMinutes(peakStartMinutes))
            putString("peak_end", formatMinutes(peakEndMinutes))
            putInt("peak_interval_int", peakInterval)
            putInt("off_peak_interval_int", offPeakInterval)
            putInt("widget_text_size", widgetTextSize)

            apply()
        }

        DebugHelper.log(this, "MainActivity", "=== NASTAVENÍ ULOŽENO, textSize=$widgetTextSize ===")
        DebugHelper.log(this, "MainActivity", "show: n95=${switchN95.isChecked}, nm=${switchNm.isChecked}, euro=${switchEuro.isChecked}")

        // ✅ POUŽIJEME updateAllWidgetsState (Glance state)
        CoroutineScope(Dispatchers.Main).launch {
            try {
                delay(500)
                updateAllWidgetsState(this@MainActivity)
                DebugHelper.log(this@MainActivity, "MainActivity", "✅ Widgety aktualizovány")
            } catch (e: Exception) {
                DebugHelper.log(this@MainActivity, "MainActivity", "Chyba: ${e.message}")
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

        findViewById<TextView>(R.id.tv_last_update).text = text.toString()
    }

    companion object {
        fun getVisibleItems(context: Context): List<Pair<String, String>> {
            val prefs = context.getSharedPreferences("tankono_prefs", Context.MODE_PRIVATE)
            val items = mutableListOf<Pair<String, String>>()

            if (prefs.getBoolean("show_n95", true)) items.add("n95" to "Natural 95")
            if (prefs.getBoolean("show_n95p", true)) items.add("n95p" to "Natural 95+")
            if (prefs.getBoolean("show_n98", true)) items.add("n98" to "Natural 98")
            if (prefs.getBoolean("show_diesel", true)) items.add("diesel" to "Diesel")
            if (prefs.getBoolean("show_diesel_plus", true)) items.add("dieselPlus" to "Diesel+")
            if (prefs.getBoolean("show_lpg", true)) items.add("lpg" to "LPG")
            if (prefs.getBoolean("show_adblue", true)) items.add("adBlue" to "AdBlue")
            if (prefs.getBoolean("show_om", true)) items.add("om" to "Osobní myčka")
            if (prefs.getBoolean("show_nm", true)) items.add("nm" to "Nákladní myčka")
            if (prefs.getBoolean("show_euro", true)) items.add("euro" to "EUR")

            return items
        }

        fun getWidgetTextSize(context: Context): Int {
            val prefs = context.getSharedPreferences("tankono_prefs", Context.MODE_PRIVATE)
            return prefs.getInt("widget_text_size", 12)
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
            return prefs.getInt("peak_interval_int", 10)
        }

        fun getOffPeakInterval(context: Context): Int {
            val prefs = context.getSharedPreferences("tankono_prefs", Context.MODE_PRIVATE)
            return prefs.getInt("off_peak_interval_int", 60)
        }
    }
}