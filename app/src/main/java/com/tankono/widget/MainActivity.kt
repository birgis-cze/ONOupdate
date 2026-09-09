package com.tankono.widget

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Na pozadí se spustí aktualizace
        TankONOWidgetScheduler.scheduleUpdates(this)
    }
}