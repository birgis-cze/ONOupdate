package com.tankono.widget

data class PriceData(
    // PHM
    val n95: Double = 0.0,
    val n95p: Double = 0.0,
    val n98: Double = 0.0,
    val diesel: Double = 0.0,
    val dieselPlus: Double = 0.0,
    val lpg: Double = 0.0,
    val adBlue: Double = 0.0,
    
    // MYČKY
    val om: Double = 0.0,      // Osobní myčka
    val nm: Double = 0.0,      // Nákladní myčka
    
    // MĚNA
    val euro: Double = 0.0,    // Kurz EUR (nákup)
    
    val lastUpdate: Long = System.currentTimeMillis()
) {
    // Trendy pro všechny položky
    val n95Trend: Int get() = getTrend(n95, getOld("n95"))
    val n95pTrend: Int get() = getTrend(n95p, getOld("n95p"))
    val n98Trend: Int get() = getTrend(n98, getOld("n98"))
    val dieselTrend: Int get() = getTrend(diesel, getOld("diesel"))
    val dieselPlusTrend: Int get() = getTrend(dieselPlus, getOld("dieselPlus"))
    val lpgTrend: Int get() = getTrend(lpg, getOld("lpg"))
    val adBlueTrend: Int get() = getTrend(adBlue, getOld("adBlue"))
    val omTrend: Int get() = getTrend(om, getOld("om"))
    val nmTrend: Int get() = getTrend(nm, getOld("nm"))
    val euroTrend: Int get() = getTrend(euro, getOld("euro"))

    private fun getTrend(current: Double, old: Double): Int {
        return when {
            old == 0.0 -> 0
            current > old + 0.01 -> 1
            current < old - 0.01 -> -1
            else -> 0
        }
    }

    private fun getOld(key: String): Double {
        val oldData = DataManager.getPrices(null) ?: return 0.0
        return when (key) {
            "n95" -> oldData.n95
            "n95p" -> oldData.n95p
            "n98" -> oldData.n98
            "diesel" -> oldData.diesel
            "dieselPlus" -> oldData.dieselPlus
            "lpg" -> oldData.lpg
            "adBlue" -> oldData.adBlue
            "om" -> oldData.om
            "nm" -> oldData.nm
            "euro" -> oldData.euro
            else -> 0.0
        }
    }
}