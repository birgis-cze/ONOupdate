package com.tankono.widget

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object DataManager {
    private const val PREFS_NAME = "tankono_prefs"
    private const val KEY_PRICES = "prices"
    private const val KEY_LAST_UPDATE = "last_update"
    private const val KEY_LAST_CHANGE_DATE = "last_change_date"
    private const val KEY_CHANGE_NOTIFIED = "change_notified"

    private val gson = Gson()
    
    var oldPrices: PriceData? = null
        private set

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun savePrices(context: Context, data: PriceData) {
        oldPrices = data
        val json = gson.toJson(data)
        getPrefs(context).edit().putString(KEY_PRICES, json).apply()
        android.util.Log.d("DataManager", "Ceny uloženy: N95=${data.n95}, Diesel=${data.diesel}")
    }

    fun getPrices(context: Context): PriceData? {
        val json = getPrefs(context).getString(KEY_PRICES, null)
        android.util.Log.d("DataManager", "Načítání cen, JSON: $json")
        return try {
            gson.fromJson(json, PriceData::class.java)
        } catch (e: Exception) {
            android.util.Log.e("DataManager", "Chyba načítání cen: ${e.message}")
            null
        }
    }

    fun saveLastUpdate(context: Context, timestamp: Long) {
        getPrefs(context).edit().putLong(KEY_LAST_UPDATE, timestamp).apply()
    }

    fun getLastUpdate(context: Context): Long {
        return getPrefs(context).getLong(KEY_LAST_UPDATE, 0)
    }

    fun saveLastChangeDate(context: Context, timestamp: Long) {
        getPrefs(context).edit().putLong(KEY_LAST_CHANGE_DATE, timestamp).apply()
        android.util.Log.d("DataManager", "Datum změny uloženo: $timestamp")
    }

    fun getLastChangeDate(context: Context): Long {
        return getPrefs(context).getLong(KEY_LAST_CHANGE_DATE, 0)
    }

    fun saveChangeNotified(context: Context, notified: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_CHANGE_NOTIFIED, notified).apply()
    }

    fun getChangeNotified(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_CHANGE_NOTIFIED, false)
    }
}