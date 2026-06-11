package com.gising

import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

/**
 * RELEASE builds: attest with **Play Integrity** (hardware-backed). Only genuine, untampered
 * installs from Google Play can obtain a valid App Check token and reach Firebase.
 *
 * This file exists ONLY in the release source set.
 */
object AppCheckInstaller {
    fun install() {
        FirebaseAppCheck.getInstance()
            .installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance())
    }
}
