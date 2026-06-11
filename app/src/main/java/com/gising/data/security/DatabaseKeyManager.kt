package com.gising.data.security

import android.content.Context
import android.util.Base64
import java.security.SecureRandom

/**
 * Owns the 256-bit passphrase that encrypts the local Room/SQLCipher database.
 *
 * The passphrase is generated once with a CSPRNG on first launch and then persisted in
 * [SecurePrefs] (Android Keystore-backed on API 23+). It is never hard-coded, never
 * shipped in the APK, and never logged — so even someone with full read access to the
 * device's app-private storage cannot open `seismic_watch.db` without first defeating the
 * Keystore.
 */
object DatabaseKeyManager {

    private const val KEY_PASSPHRASE = "db_passphrase_b64"
    private const val PASSPHRASE_BYTES = 32 // 256-bit

    /**
     * Returns the raw passphrase bytes for SQLCipher's `SupportOpenHelperFactory`, generating and
     * storing a fresh random key the first time it is called.
     *
     * A fresh copy is decoded on every call because SQLCipher zeroes the array it is given.
     */
    fun getOrCreatePassphrase(context: Context): ByteArray {
        val prefs = SecurePrefs(context)
        val existing = prefs.getString(KEY_PASSPHRASE)
        if (existing != null) {
            return Base64.decode(existing, Base64.NO_WRAP)
        }
        val fresh = ByteArray(PASSPHRASE_BYTES).also { SecureRandom().nextBytes(it) }
        prefs.putString(KEY_PASSPHRASE, Base64.encodeToString(fresh, Base64.NO_WRAP))
        return fresh
    }
}
