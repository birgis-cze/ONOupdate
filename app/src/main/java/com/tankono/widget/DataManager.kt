package com.tankono.widget

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object DataManager {
    private const val PREFS_NAME = "tankono_prefs"
    private const val KEY_PRICES = "prices"
    private const val KEY_LAST_UPDATE = "last_update"
    private const val KEY_CHANGE_NOTIFIED = "change_notified"
    private const val KEY_VISIBLE_ITEMS = "visible_items"

    private val gson = Gson()

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // ULOŽENÍ CEN
    fun savePrices(context: Context, data: PriceData) {
        val json = gson.toJson(data)
        getPrefs(context).edit().putString(KEY_PRICES, json).apply()
    }

    fun getPrices(context: Context): PriceData? {
        val json = getPrefs(context).getString(KEY_PRICES, null) ?: return null
        return try {
            gson.fromJson(json, PriceData::class.java)
        } catch (e: Exception) {
            null
        }
    }

    // ČAS POSLEDNÍ AKTUALIZACE
    fun saveLastUpdate(context: Context, timestamp: Long) {
        getPrefs(context).edit().putLong(KEY_LAST_UPDATE, timestamp).apply()
    }

    fun getLastUpdate(context: Context): Long {
        return getPrefs(context).getLong(KEY_LAST_UPDATE, 0)
    }

    // NOTIFIKACE O ZMĚNĚ
    fun saveChangeNotified(context: Context, notified: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_CHANGE_NOTIFIED, notified).apply()
    }

    fun getChangeNotified(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_CHANGE_NOTIFIED, false)
    }

    // VIDITELNÉ POLOŽKY - NOVÉ
    fun saveVisibleItems(context: Context, items: List<String>) {
        val json = gson.toJson(items)
        getPrefs(context).edit().putString(KEY_VISIBLE_ITEMS, json).apply()
    }

    fun getVisibleItems(context: Context): List<String> {
        val json = getPrefs(context).getString(KEY_VISIBLE_ITEMS, null) ?: return listOf(
            "n95", "n95p", "n98", "diesel", "dieselPlus", "lpg", "adBlue", "om", "nm", "euro"
        )
        return try {
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            listOf("n95", "n95p", "n98", "diesel", "dieselPlus", "lpg", "adBlue", "om", "nm", "euro")
        }
    }
}