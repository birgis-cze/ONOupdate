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
    val n95Trend: Int get() = getTrend(n95, "n95")
    val n95pTrend: Int get() = getTrend(n95p, "n95p")
    val n98Trend: Int get() = getTrend(n98, "n98")
    val dieselTrend: Int get() = getTrend(diesel, "diesel")
    val dieselPlusTrend: Int get() = getTrend(dieselPlus, "dieselPlus")
    val lpgTrend: Int get() = getTrend(lpg, "lpg")
    val adBlueTrend: Int get() = getTrend(adBlue, "adBlue")
    val omTrend: Int get() = getTrend(om, "om")
    val nmTrend: Int get() = getTrend(nm, "nm")
    val euroTrend: Int get() = getTrend(euro, "euro")

    private fun getTrend(current: Double, key: String): Int {
        val oldData = DataManager.oldPrices
        val old = when (key) {
            "n95" -> oldData?.n95 ?: current
            "n95p" -> oldData?.n95p ?: current
            "n98" -> oldData?.n98 ?: current
            "diesel" -> oldData?.diesel ?: current
            "dieselPlus" -> oldData?.dieselPlus ?: current
            "lpg" -> oldData?.lpg ?: current
            "adBlue" -> oldData?.adBlue ?: current
            "om" -> oldData?.om ?: current
            "nm" -> oldData?.nm ?: current
            "euro" -> oldData?.euro ?: current
            else -> current
        }
        
        return when {
            old == 0.0 -> 0
            current > old + 0.01 -> 1
            current < old - 0.01 -> -1
            else -> 0
        }
    }
}