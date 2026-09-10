package cz.tankono.widget.data.model

/**
 * Seznam všech produktů, které Tank ONO zveřejňuje na ceníku.
 * Pořadí v enumu = pořadí zobrazení ve widgetu (seskupené dle [Kind]).
 */
enum class Product(
    val id: String,
    val displayName: String,
    val kind: Kind
) {
    // Paliva
    NATURAL_95      ("natural_95",       "Natural 95",     Kind.FUEL),
    NATURAL_95_PLUS ("natural_95_plus",  "Natural 95+",    Kind.FUEL),
    NATURAL_98      ("natural_98",       "Natural 98",     Kind.FUEL),
    DIESEL          ("diesel",           "Diesel",         Kind.FUEL),
    DIESEL_PLUS     ("diesel_plus",      "Diesel+",        Kind.FUEL),
    LPG             ("lpg",              "LPG",            Kind.FUEL),
    AD_BLUE         ("ad_blue",          "Ad Blue",        Kind.FUEL),

    // Ostatní (mytí)
    OM              ("om",               "Osobní myčka",   Kind.OTHER),
    NM              ("nm",               "Nákladní myčka", Kind.OTHER),

    // Směnárna
    EUR_BUY         ("eur_buy",          "EUR – nákup",    Kind.EXCHANGE),
    EUR_SELL        ("eur_sell",         "EUR – prodej",   Kind.EXCHANGE);

    enum class Kind { FUEL, OTHER, EXCHANGE }
}

/** Volitelná měna zobrazení. */
enum class Currency { CZK, EUR }