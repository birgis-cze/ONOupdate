package com.tankono.widget

data class PriceData(
    val n95: Double = 0.0,
    val n95p: Double = 0.0,
    val nafta: Double = 0.0,
    val lpg: Double = 0.0,
    val lastUpdate: Long = System.currentTimeMillis()
) {
    val n95Trend: Int
        get() = getTrend(n95, getOldN95())
    val n95pTrend: Int
        get() = getTrend(n95p, getOldN95p())
    val naftaTrend: Int
        get() = getTrend(nafta, getOldNafta())
    val lpgTrend: Int
        get() = getTrend(lpg, getOldLpg())

    private fun getTrend(current: Double, old: Double): Int {
        return when {
            old == 0.0 -> 0
            current > old -> 1
            current < old -> -1
            else -> 0
        }
    }

    private fun getOldN95(): Double = DataManager.getPrices(null)?.n95 ?: n95
    private fun getOldN95p(): Double = DataManager.getPrices(null)?.n95p ?: n95p
    private fun getOldNafta(): Double = DataManager.getPrices(null)?.nafta ?: nafta
    private fun getOldLpg(): Double = DataManager.getPrices(null)?.lpg ?: lpg
}