package cz.tankono.widget.data.model

import android.text.SpannableString
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.text.style.SuperscriptSpan

/**
 * Formátování ceny se superscriptem (desetinná část menší, nahoře).
 * Používá se pro AKTUÁLNÍ cenu ve widgetu.
 *
 *   CZK: 4250  → "42" + sup("50")  → 42⁵⁰
 *   EUR: 1763  → "1"  + sup("763") → 1⁷⁶³
 */
object PriceSpannable {

    fun format(value: Int?, decimals: Int): CharSequence {
        if (value == null) return "--"
        if (decimals <= 0) return value.toString()

        // Doplníme zleva nulami, aby byla celá část vždy alespoň 1 znak
        val s = value.toString().padStart(decimals + 1, '0')
        val whole = s.dropLast(decimals)
        val frac = s.takeLast(decimals)
        val full = whole + frac

        val sp = SpannableString(full)
        val start = whole.length
        val end = full.length

        sp.setSpan(SuperscriptSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        sp.setSpan(RelativeSizeSpan(0.65f), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        return sp
    }

    /** CZK: 2 desetinná místa (haléře). */
    fun czk(value: Int?): CharSequence = format(value, decimals = 2)

    /** EUR: 3 desetinná místa (tisíciny). */
    fun eur(value: Int?): CharSequence = format(value, decimals = 3)

    /** Naformátuje dle měny a druhu produktu. U směnárny vždy CZK. */
    fun forEntry(entry: PriceEntry?, currency: Currency): CharSequence {
        if (entry == null) return "--"
        return if (entry.product.kind == Product.Kind.EXCHANGE || currency == Currency.CZK)
            czk(entry.czk)
        else
            eur(entry.eur)
    }
}