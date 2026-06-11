package com.gising.emergency

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Reactive companion to [ConnectivityProbe]: emits `true` whenever the device has a validated
 * internet path and `false` when it doesn't, updating live as the network comes and goes.
 *
 * Used by the UI layer to flip into "Offline Mode" the moment connectivity drops (and back out when
 * it returns) so the app can fall back to locally-cached earthquakes/typhoons without any polling.
 */
object ConnectivityObserver {

    /** Live online/offline state. Emits the current value immediately, then on every change. */
    fun observe(context: Context): Flow<Boolean> = callbackFlow {
        val cm = context.applicationContext
            .getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (cm == null) {
            trySend(false)
            close()
            return@callbackFlow
        }

        // Track the set of networks that are currently usable; online = at least one is validated.
        val validated = mutableSetOf<Network>()

        fun emit() { trySend(validated.isNotEmpty()) }

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                val ok = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                if (ok) validated.add(network) else validated.remove(network)
                emit()
            }

            override fun onLost(network: Network) {
                validated.remove(network)
                emit()
            }

            override fun onUnavailable() {
                emit()
            }
        }

        // Seed with the current state so collectors don't wait for the first change event.
        trySend(ConnectivityProbe.isOnline(context))

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        runCatching { cm.registerNetworkCallback(request, callback) }

        awaitClose { runCatching { cm.unregisterNetworkCallback(callback) } }
    }.distinctUntilChanged()
}
