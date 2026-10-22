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

        // 2. Zkusit čerstvý fix s timeoutem 10 sekund
        val freshLoc = withTimeoutOrNull(10_000L) {
            suspendCancellableCoroutine<android.location.Location?> { cont ->
                // OPRAVA: Android 12+ striktně vyžaduje CancellationToken
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
                        AppLogger.e("LocationProvider: fresh location selhalo", t)
                        if (cont.isActive) cont.resume(null)
                    }
            }
        }

        if (freshLoc != null) {
            return freshLoc.latitude to freshLoc.longitude
        }

        // 3. Fallback: Když fresh selže, vezmeme si alespoň tu starou
        if (lastLoc != null) {
            AppLogger.w("LocationProvider: fresh fix selhal, používám starou lastLocation")
            return lastLoc.latitude to lastLoc.longitude
        }

        return null
    }
}