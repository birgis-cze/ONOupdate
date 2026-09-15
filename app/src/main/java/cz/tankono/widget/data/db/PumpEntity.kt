package cz.tankono.widget.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entita čerpací stanice Tank ONO.
 *
 * Data se stahují ze stránky:
 * https://www.tank-ono.cz/cz/index.php?page=pumpy
 *
 * GPS souřadnice se získávají z detailu každé pumpy:
 * https://www.tank-ono.cz/cz/index.php?page=pumpcard&pump=X
 * (přes zkrácený goo.gl odkaz na Google Maps)
 */
@Entity(tableName = "pumps")
data class PumpEntity(
    /** ID pumpy ze serveru (1–46) */
    @PrimaryKey
    val id: Int,

    /** Název stanice, např. "ČS Plzeň, Domažlická" */
    val name: String,

    /** URL detailu pumpy na tank-ono.cz */
    val detailUrl: String,

    /** GPS – zeměpisná šířka (null, dokud se nestáhne) */
    val lat: Double? = null,

    /** GPS – zeměpisná délka (null, dokud se nestáhne) */
    val lng: Double? = null,

    /** Čas poslední aktualizace GPS (System.currentTimeMillis()) */
    val lastUpdated: Long = 0L
)