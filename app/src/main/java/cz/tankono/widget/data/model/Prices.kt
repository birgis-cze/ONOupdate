package cz.tankono.widget.data.model

/** Jedna cenová položka produktu. */
data class PriceEntry(
    val product: Product,
    /** Cena v Kč v haléřích (4250 = 42,50 Kč). */
    val czk: Int?,
    /** Cena v EUR v tisícinách (1763 = 1,763 €). */
    val eur: Int?
)

/** Snímek ceníku v jednom okamžiku. */
data class PriceSnapshot(
    val entries: Map<Product, PriceEntry>,
    /** Datum a čas zveřejnění (řetězec ze stránky aktualit), např. "10.9.2026 (14:42:44)". */
    val publishedAt: String?,
    /** Čas, kdy náš widget data stáhl (System.currentTimeMillis()). */
    val fetchedAt: Long
)

/** Stav – aktuální snímek + předchozí (pro zobrazení trendu). */
data class PriceState(
    val current: PriceSnapshot?,
    val previous: PriceSnapshot?
)