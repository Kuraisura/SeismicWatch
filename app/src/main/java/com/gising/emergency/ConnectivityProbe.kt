package com.gising.emergency

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * Cheap, synchronous check for whether the device has a *validated* internet path right now.
 * Used to decide whether the SOS cloud write is even worth attempting before we fall back to SMS.
 *
 * Note: `NET_CAPABILITY_VALIDATED` means Android has confirmed real connectivity (capport probe),
 * not merely that an interface is up — important during a disaster when a tower may be associated
 * but carrying no traffic.
 */
object ConnectivityProbe {

    fun isOnline(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
