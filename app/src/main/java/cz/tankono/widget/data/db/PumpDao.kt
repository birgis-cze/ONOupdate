package cz.tankono.widget.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PumpDao {

    /** Vrátí všechny pumpy. */
    @Query("SELECT * FROM pumps ORDER BY name ASC")
    suspend fun getAll(): List<PumpEntity>

    /** Vrátí jen pumpy, které mají GPS. */
    @Query("SELECT * FROM pumps WHERE lat IS NOT NULL AND lng IS NOT NULL ORDER BY name ASC")
    suspend fun getAllWithGps(): List<PumpEntity>

    /** Vrátí pumpy, které ještě nemají GPS. */
    @Query("SELECT * FROM pumps WHERE lat IS NULL OR lng IS NULL")
    suspend fun getPumpsWithoutGps(): List<PumpEntity>

    /** Vloží nebo přepíše pumpy. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(pumps: List<PumpEntity>)

    /** Aktualizuje GPS u konkrétní pumpy. */
    @Query("UPDATE pumps SET lat = :lat, lng = :lng, lastUpdated = :time WHERE id = :id")
    suspend fun updateGps(id: Int, lat: Double, lng: Double, time: Long)

    /** Smaže všechny pumpy. */
    @Query("DELETE FROM pumps")
    suspend fun deleteAll()

    /** Počet pump v DB. */
    @Query("SELECT COUNT(*) FROM pumps")
    suspend fun count(): Int
}