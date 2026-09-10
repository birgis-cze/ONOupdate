package com.tankono.widget

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson

object DataManager {
    private const val PREFS_NAME = "tankono_prefs"
    private const val KEY_PRICES = "prices"
    private const val KEY_OLD_PRICES = "old_prices" // Nové trvalé úložiště pro staré ceny
    private const val KEY_LAST_UPDATE = "last_update"
    private const val KEY_LAST_CHANGE_DATE = "last_change_date"
    private const val KEY_CHANGE_NOTIFIED = "change_notified"

    private val gson = Gson()

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun savePrices(context: Context, newData: PriceData) {
        val prefs = getPrefs(context)
        
        // 1. Stávající aktuální ceny přesuneme do old_prices
        val currentJson = prefs.getString(KEY_PRICES, null)
        if (currentJson != null) {
            prefs.edit().putString(KEY_OLD_PRICES, currentJson).apply()
        }

        // 2. Uložíme nové ceny jako hlavní aktuální ceny
        val newJson = gson.toJson(newData)
        prefs.edit().putString(KEY_PRICES, newJson).apply()

        android.util.Log.d("DataManager", "Ceny uloženy: N95=${newData.n95}, NM=${newData.nm}, EUR=${newData.euro}")
    }

    fun getPrices(context: Context): PriceData? {
        val json = getPrefs(context).getString(KEY_PRICES, null) ?: return null
        return try {
            gson.fromJson(json, PriceData::class.java)
        } catch (e: Exception) {
            null
        }
    }

    // Nová metoda pro načtení předchozích cen z úložiště
    fun getOldPrices(context: Context): PriceData? {
        val json = getPrefs(context).getString(KEY_OLD_PRICES, null) ?: return null
        return try {
            gson.fromJson(json, PriceData::class.java)
        } catch (e: Exception) {
            null
        }
    }

    fun saveLastUpdate(context: Context, timestamp: Long) {
        getPrefs(context).edit().putLong(KEY_LAST_UPDATE, timestamp).apply()
    }

    fun getLastUpdate(context: Context): Long {
        return getPrefs(context).getLong(KEY_LAST_UPDATE, 0L)
    }

    fun saveLastChangeDate(context: Context, dateStr: String) {
        getPrefs(context).edit().putString(KEY_LAST_CHANGE_DATE, dateStr).apply()
    }

    fun getLastChangeDate(context: Context): String {
        return getPrefs(context).getString(KEY_LAST_CHANGE_DATE, "") ?: ""
    }

    fun setChangeNotified(context: Context, notified: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_CHANGE_NOTIFIED, notified).apply()
    }

    fun isChangeNotified(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_CHANGE_NOTIFIED, false)
    }
}