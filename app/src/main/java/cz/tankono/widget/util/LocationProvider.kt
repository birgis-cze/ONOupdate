package cz.tankono.widget.util

import android.annotation.SuppressLint
import android.content.Context
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Pomocník pro získání aktuální polohy pomocí FusedLocationProviderClient.
 *
 * POZOR: Vyžaduje runtime oprávnění ACCESS_FINE_LOCATION nebo ACCESS_COARSE_LOCATION.
 * Pokud oprávnění není uděleno, vrátí null (nikdy nevyhodí SecurityException).
 */
object LocationProvider {

    /**
     * Získá aktuální polohu.
     *
     * Postup:
     * 1. Zkusí poslední známou polohu (rychlé, může být starší).
     * 2. Pokud null, vyžádá fresh fix (pomalejší, přesnější).
     *
     * @return Pair(lat, lng) nebo null při selhání / chybějícím oprávnění.
     */
    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(context: Context): Pair<Double, Double>? =
        suspendCancellableCoroutine { cont ->
            val client = LocationServices.getFusedLocationProviderClient(context)

            try {
                client.lastLocation
                    .addOnSuccessListener { loc ->
                        if (loc != null) {
                            AppLogger.d("LocationProvider: lastLocation = ${loc.latitude}, ${loc.longitude}")
                            if (cont.isActive) cont.resume(loc.latitude to loc.longitude)
                        } else {
                            // Fallback: vyžádat fresh fix
                            AppLogger.d("LocationProvider: lastLocation null, zkouším getCurrentLocation")
                            client.getCurrentLocation(
                                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                                null
                            )
                                .addOnSuccessListener { fresh ->
                                    if (fresh != null) {
                                        AppLogger.d("LocationProvider: fresh = ${fresh.latitude}, ${fresh.longitude}")
                                    } else {
                                        AppLogger.w("LocationProvider: fresh location null")
                                    }
                                    if (cont.isActive) {
                                        cont.resume(fresh?.let { it.latitude to it.longitude })
                                    }
                                }
                                .addOnFailureListener { t ->
                                    AppLogger.e("LocationProvider: fresh location selhalo", t)
                                    if (cont.isActive) cont.resume(null)
                                }
                        }
                    }
                    .addOnFailureListener { t ->
                        AppLogger.e("LocationProvider: lastLocation selhalo", t)
                        if (cont.isActive) cont.resume(null)
                    }
            } catch (t: Throwable) {
                AppLogger.e("LocationProvider: výjimka", t)
                if (cont.isActive) cont.resume(null)
            }
        }
}