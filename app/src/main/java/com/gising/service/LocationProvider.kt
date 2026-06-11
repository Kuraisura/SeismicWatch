package com.gising.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Lightweight location lookup using the framework [LocationManager] — no Google Play Services
 * required, so it works on every device down to Lollipop.
 *
 * Prefer [awaitCurrent], which requests a FRESH fix and only falls back to the (often stale or null)
 * last-known position — that stale/null fallback is what previously left the user's weather stuck on
 * the Manila default.
 */
object LocationProvider {

    /**
     * A fresh location fix if one can be obtained within [timeoutMs]; otherwise the best last-known
     * fix; otherwise null (no permission / no provider). Suspends — call from a coroutine.
     */
    suspend fun awaitCurrent(context: Context, timeoutMs: Long = 8000): Pair<Double, Double>? {
        if (!hasLocationPermission(context)) return null
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val fresh = withTimeoutOrNull(timeoutMs) { requestSingleFix(context, lm) }
        return fresh ?: lastKnown(context)
    }

    private suspend fun requestSingleFix(
        context: Context,
        lm: LocationManager,
    ): Pair<Double, Double>? = suspendCancellableCoroutine { cont ->
        val provider = when {
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> {
                if (cont.isActive) cont.resume(null)
                return@suspendCancellableCoroutine
            }
        }
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                lm.removeUpdates(this)
                if (cont.isActive) cont.resume(location.latitude to location.longitude)
            }

            override fun onProviderDisabled(provider: String) {}
            override fun onProviderEnabled(provider: String) {}
            @Deprecated("Required by the interface on old APIs")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        }
        try {
            @Suppress("MissingPermission")
            lm.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
        } catch (e: SecurityException) {
            if (cont.isActive) cont.resume(null)
        }
        cont.invokeOnCancellation { runCatching { lm.removeUpdates(listener) } }
    }

    /** Returns (lat, lon) of the best recent fix, or null if unavailable / no permission. */
    fun lastKnown(context: Context): Pair<Double, Double>? {
        if (!hasLocationPermission(context)) return null
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null

        return try {
            val providers = lm.getProviders(true)
            var best: Location? = null
            for (provider in providers) {
                @Suppress("MissingPermission")
                val loc = lm.getLastKnownLocation(provider) ?: continue
                if (best == null || loc.time > best!!.time) best = loc
            }
            best?.let { it.latitude to it.longitude }
        } catch (e: SecurityException) {
            null
        }
    }

    fun hasLocationPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }
}
