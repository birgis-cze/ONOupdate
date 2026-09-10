package com.tankono.widget

data class PriceData(
    val n95: Double = 0.0,
    val n95p: Double = 0.0,
    val n98: Double = 0.0,
    val diesel: Double = 0.0,
    val dieselPlus: Double = 0.0,
    val lpg: Double = 0.0,
    val adBlue: Double = 0.0,
    val om: Double = 0.0,
    val nm: Double = 0.0,
    val euro: Double = 0.0,
    val lastUpdate: Long = System.currentTimeMillis()
) {
    // Metody pro získání trendu vyžadují objekt starých cen
    fun getN95Trend(oldData: PriceData?): Int = getTrend(n95, oldData?.n95)
    fun getN95pTrend(oldData: PriceData?): Int = getTrend(n95p, oldData?.n95p)
    fun getN98Trend(oldData: PriceData?): Int = getTrend(n98, oldData?.n98)
    fun getDieselTrend(oldData: PriceData?): Int = getTrend(diesel, oldData?.diesel)
    fun getDieselPlusTrend(oldData: PriceData?): Int = getTrend(dieselPlus, oldData?.dieselPlus)
    fun getLpgTrend(oldData: PriceData?): Int = getTrend(lpg, oldData?.lpg)
    fun getAdBlueTrend(oldData: PriceData?): Int = getTrend(adBlue, oldData?.adBlue)
    fun getOmTrend(oldData: PriceData?): Int = getTrend(om, oldData?.om)
    fun getNmTrend(oldData: PriceData?): Int = getTrend(nm, oldData?.nm)
    fun getEuroTrend(oldData: PriceData?): Int = getTrend(euro, oldData?.euro)

    private fun getTrend(current: Double, old: Double?): Int {
        val previous = old ?: return 0
        return when {
            previous == 0.0 -> 0
            current > previous + 0.01 -> 1
            current < previous - 0.01 -> -1
            else -> 0
        }
    }
}