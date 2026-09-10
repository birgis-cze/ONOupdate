package cz.tankono.widget.data.model

/**
 * Formátování cen na String (bez superscriptu).
 * Používá se pro starou cenu v závorce.
 */
object PriceFormatter {

    /** CZK: 4250 → "42,50"; 64900 → "649,00"; null → "--" */
    fun czk(value: Int?): String {
        if (value == null) return "--"
        val whole = value / 100
        val frac = value % 100
        return "%d,%02d".format(whole, frac)
    }

    /** EUR: 1763 → "1,763"; 26929 → "26,929"; null → "--" */
    fun eur(value: Int?): String {
        if (value == null) return "--"
        val whole = value / 1000
        val frac = value % 1000
        return "%d,%03d".format(whole, frac)
    }

    /**
     * Vrátí hodnotu odpovídající zvolené měně.
     * U směnárny (EXCHANGE) vždy CZK (kurz).
     */
    fun valueFor(entry: PriceEntry?, currency: Currency): Int? {
        if (entry == null) return null
        return if (entry.product.kind == Product.Kind.EXCHANGE || currency == Currency.CZK)
            entry.czk
        else
            entry.eur
    }

    /** Naformátuje cenu dle měny a druhu produktu. */
    fun format(entry: PriceEntry?, currency: Currency): String {
        if (entry == null) return "--"
        return if (entry.product.kind == Product.Kind.EXCHANGE || currency == Currency.CZK)
            czk(entry.czk)
        else
            eur(entry.eur)
    }
}
