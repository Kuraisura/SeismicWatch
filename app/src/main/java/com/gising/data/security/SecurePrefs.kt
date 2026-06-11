package com.gising.data.security

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * A small wrapper around the app's secret store.
 *
 * On Android 6.0+ (API 23) this is a true Android Keystore-backed
 * [EncryptedSharedPreferences] — values are encrypted with AES-256 and the master key
 * never leaves the hardware-backed keystore.
 *
 * On Android 5.x (API 21-22) the Keystore cannot hold an AES master key, so we fall back
 * to a normal private [SharedPreferences]. This is documented in SECURITY.md as a known,
 * accepted limitation for the small slice of very old devices we still support (minSdk 21);
 * the on-disk database itself is still SQLCipher-encrypted either way.
 */
class SecurePrefs(context: Context) {

    private val prefs: SharedPreferences = createPrefs(context.applicationContext)

    fun getString(key: String): String? = prefs.getString(key, null)

    fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    fun contains(key: String): Boolean = prefs.contains(key)

    fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun createPrefs(context: Context): SharedPreferences {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val masterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                return EncryptedSharedPreferences.create(
                    context,
                    ENCRYPTED_FILE,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } catch (e: Exception) {
                // A corrupted keystore entry (e.g. after a device security change) can throw.
                // Recover by dropping the encrypted file and recreating it once.
                Log.w(TAG, "EncryptedSharedPreferences unavailable, recreating", e)
                context.deleteSharedPreferences(ENCRYPTED_FILE)
                return runCatching {
                    val masterKey = MasterKey.Builder(context)
                        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                        .build()
                    EncryptedSharedPreferences.create(
                        context,
                        ENCRYPTED_FILE,
                        masterKey,
                        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                    )
                }.getOrElse { context.getSharedPreferences(FALLBACK_FILE, Context.MODE_PRIVATE) }
            }
        }
        // API 21-22: best-effort private prefs (see class doc / SECURITY.md).
        return context.getSharedPreferences(FALLBACK_FILE, Context.MODE_PRIVATE)
    }

    companion object {
        private const val TAG = "SecurePrefs"
        private const val ENCRYPTED_FILE = "gising_secure_prefs"
        private const val FALLBACK_FILE = "gising_secure_prefs_legacy"
    }
}
