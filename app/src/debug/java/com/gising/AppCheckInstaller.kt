package com.gising

import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/**
 * DEBUG builds: attest with the App Check **debug** provider. On first run, Logcat prints a
 * debug secret — register it under Firebase Console → App Check → Manage debug tokens.
 *
 * This file exists ONLY in the debug source set, so the debug provider class (which ships in
 * the debug-only `firebase-appcheck-debug` dependency) is never referenced by release builds.
 */
object AppCheckInstaller {
    fun install() {
        FirebaseAppCheck.getInstance()
            .installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
    }
}
