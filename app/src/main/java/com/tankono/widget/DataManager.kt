package com.tankono.widget

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson   // ← PŘIDAT IMPORT

object DataManager {
    private const val PREFS_NAME = "tankono_prefs"
    private const val KEY_PRICES = "prices"
    private const val KEY_LAST_UPDATE = "last_update"
    private const val KEY_CHANGE_NOTIFIED = "change_notified"

    private val gson = Gson()   // ← TOTO TEĎ FUNGUJE

    fun getPrefs(context: Context?): SharedPreferences? {
        return context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun savePrices(context: Context, data: PriceData) {
        getPrefs(context)?.edit()?.putString(KEY_PRICES, gson.toJson(data))?.apply()
    }

    fun getPrices(context: Context?): PriceData? {
        val json = getPrefs(context)?.getString(KEY_PRICES, null) ?: return null
        return try {
            gson.fromJson(json, PriceData::class.java)
        } catch (e: Exception) {
            null
        }
    }

    fun saveLastUpdate(context: Context, timestamp: Long) {
        getPrefs(context)?.edit()?.putLong(KEY_LAST_UPDATE, timestamp)?.apply()
    }

    fun getLastUpdate(context: Context?): Long {
        return getPrefs(context)?.getLong(KEY_LAST_UPDATE, 0) ?: 0
    }

    fun saveChangeNotified(context: Context, notified: Boolean) {
        getPrefs(context)?.edit()?.putBoolean(KEY_CHANGE_NOTIFIED, notified)?.apply()
    }

    fun getChangeNotified(context: Context?): Boolean {
        return getPrefs(context)?.getBoolean(KEY_CHANGE_NOTIFIED, false) ?: false
    }
}