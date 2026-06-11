package com.gising.data.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.gising.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

/**
 * Obtains a Google ID token via the modern Credential Manager API (the replacement for
 * the deprecated GoogleSignInClient). The token is then exchanged for a Firebase session
 * by [AuthRepository.signInWithGoogle].
 *
 * Requires `R.string.default_web_client_id`, which the google-services Gradle plugin
 * generates from the OAuth *web* client in `google-services.json`. See FIREBASE_SETUP.md.
 */
class GoogleAuthClient(appContext: Context) {

    private val credentialManager = CredentialManager.create(appContext)
    private val serverClientId = appContext.getString(R.string.default_web_client_id)

    /**
     * Launches the Google account chooser and returns the selected account's ID token.
     * [activityContext] must be an Activity so the system can show the picker.
     */
    suspend fun getIdToken(activityContext: Context): Result<String> = runCatching {
        val googleIdOption = GetGoogleIdOption.Builder()
            .setServerClientId(serverClientId)
            // false = also offer accounts that haven't authorized this app yet (first run).
            .setFilterByAuthorizedAccounts(false)
            .setAutoSelectEnabled(false)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        val response = credentialManager.getCredential(activityContext, request)
        val credential = GoogleIdTokenCredential.createFrom(response.credential.data)
        credential.idToken
    }
}
