package cz.tankono.widget.util

import android.annotation.SuppressLint
import android.content.Context
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Pomocník pro získání aktuální polohy.
 * Ošetřuje omezení Androidu pro běh na pozadí.
 */
object LocationProvider {

    /** Timeout pro fresh fix. Po 5 s se použije cache. */
    private const val FRESH_FIX_TIMEOUT_MS = 5_000L

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(context: Context): Pair<Double, Double>? {
        val client = LocationServices.getFusedLocationProviderClient(context)

        // 1. Zkusit poslední známou polohu (je okamžitě dostupná a nezatěžuje baterku)
        val lastLoc = suspendCancellableCoroutine<android.location.Location?> { cont ->
            client.lastLocation
                .addOnSuccessListener { if (cont.isActive) cont.resume(it) }
                .addOnFailureListener { if (cont.isActive) cont.resume(null) }
        }

        if (lastLoc != null) {
            // Pokud je poloha mladší než 15 minut, považujeme ji za aktuální
            val ageMs = System.currentTimeMillis() - lastLoc.time
            if (ageMs < 15 * 60 * 1000) {
                AppLogger.d("LocationProvider: lastLocation = ${lastLoc.latitude}, ${lastLoc.longitude} (stáří: ${ageMs / 1000}s)")
                return lastLoc.latitude to lastLoc.longitude
            } else {
                AppLogger.d("LocationProvider: lastLocation je stará (${ageMs / 1000 / 60} min), zkouším fresh fix")
            }
        } else {
            AppLogger.d("LocationProvider: lastLocation null, zkouším fresh fix")
        }

        // 2. Zkusit čerstvý fix s timeoutem 5 sekund
        val freshLoc = withTimeoutOrNull(FRESH_FIX_TIMEOUT_MS) {
            suspendCancellableCoroutine<android.location.Location?> { cont ->
                // Android 12+ striktně vyžaduje CancellationToken
                val cancellationTokenSource = CancellationTokenSource()
                cont.invokeOnCancellation { cancellationTokenSource.cancel() }

                client.getCurrentLocation(
                    Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                    cancellationTokenSource.token
                )
                    .addOnSuccessListener { loc ->
                        if (cont.isActive) {
                            if (loc != null) AppLogger.d("LocationProvider: fresh = ${loc.latitude}, ${loc.longitude}")
                            else AppLogger.w("LocationProvider: fresh location null")
                            cont.resume(loc)
                        }
                    }
                    .addOnFailureListener { t ->
                        // Selhání fresh fixu není chyba aplikace – je to normální stav
                        // (uvnitř budovy, GPS vypnutá, chybí background permission).
                        AppLogger.w("LocationProvider: fresh location selhalo: ${t.message}")
                        if (cont.isActive) cont.resume(null)
                    }
            }
        }

        if (freshLoc != null) {
            return freshLoc.latitude to freshLoc.longitude
        }

        // 3. Fallback: Když fresh selže (nebo timeout), vezmeme si alespoň tu starou
        if (lastLoc != null) {
            AppLogger.w("LocationProvider: fresh fix selhal, používám starou lastLocation")
            return lastLoc.latitude to lastLoc.longitude
        }

        return null
    }
}