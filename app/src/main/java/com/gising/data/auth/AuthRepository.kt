package com.gising.data.auth

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/** Auth state the UI reacts to. */
sealed interface AuthState {
    data object Loading : AuthState
    data object SignedOut : AuthState
    data class SignedIn(val user: FirebaseUser, val emailVerified: Boolean) : AuthState
}

/**
 * Single source of truth for authentication, wrapping [FirebaseAuth].
 *
 * Security posture:
 *  • We never store passwords ourselves — Firebase Auth handles hashing/verification.
 *  • Email/password sign-ups must verify their email before they can share location
 *    (enforced in the UI + Security Rules via `request.auth.token.email_verified`).
 *  • Destructive actions (delete account) require a fresh re-authentication.
 *  • On first sign-in we create the user's private `users/{uid}` profile document.
 */
class AuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {

    val currentUser: FirebaseUser? get() = auth.currentUser

    /** Emits the live auth state; backed by Firebase's auth-state listener. */
    val authState: Flow<AuthState> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { fb ->
            val user = fb.currentUser
            trySend(
                if (user == null) AuthState.SignedOut
                else AuthState.SignedIn(user, user.isEmailVerified)
            )
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    // ── Email / password ─────────────────────────────────────────────────────

    suspend fun signUpWithEmail(email: String, password: String, displayName: String): Result<Unit> =
        runCatching {
            val result = auth.createUserWithEmailAndPassword(email.trim(), password).await()
            val user = result.user ?: error("Sign-up succeeded but no user was returned")
            user.updateProfile(
                UserProfileChangeRequest.Builder().setDisplayName(displayName.trim()).build()
            ).await()
            user.sendEmailVerification().await()
            upsertUserProfile(user)
        }

    suspend fun signInWithEmail(email: String, password: String): Result<Unit> =
        runCatching {
            val result = auth.signInWithEmailAndPassword(email.trim(), password).await()
            result.user?.let { upsertUserProfile(it) }
            Unit
        }

    suspend fun sendEmailVerification(): Result<Unit> =
        runCatching { auth.currentUser?.sendEmailVerification()?.await(); Unit }

    suspend fun reloadUser(): Result<Boolean> =
        runCatching {
            auth.currentUser?.reload()?.await()
            auth.currentUser?.isEmailVerified == true
        }

    suspend fun sendPasswordReset(email: String): Result<Unit> =
        runCatching { auth.sendPasswordResetEmail(email.trim()).await() }

    // ── Google Sign-In ────────────────────────────────────────────────────────

    /** Completes Google Sign-In with an ID token obtained from the Credential Manager. */
    suspend fun signInWithGoogle(idToken: String): Result<Unit> =
        runCatching {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val result = auth.signInWithCredential(credential).await()
            result.user?.let { upsertUserProfile(it) }
            Unit
        }

    // ── Session / account lifecycle ─────────────────────────────────────────────

    fun signOut() = auth.signOut()

    /** Removes the auth account. Caller must wipe Firestore data first (see PrivacyRepository). */
    suspend fun deleteAccount(): Result<Unit> =
        runCatching { auth.currentUser?.delete()?.await(); Unit }

    // ── Profile document ────────────────────────────────────────────────────────

    /** Creates or updates the caller's own private profile doc. [createdAt] is set once. */
    private suspend fun upsertUserProfile(user: FirebaseUser) {
        val ref = firestore.collection("users").document(user.uid)
        firestore.runTransaction { txn ->
            val snap = txn.get(ref)
            val data = mutableMapOf<String, Any>(
                "uid" to user.uid,
                "displayName" to (user.displayName ?: ""),
                "email" to (user.email ?: ""),
                "photoUrl" to (user.photoUrl?.toString() ?: ""),
                "updatedAt" to FieldValue.serverTimestamp(),
            )
            if (!snap.exists()) data["createdAt"] = FieldValue.serverTimestamp()
            txn.set(ref, data, SetOptions.merge())
        }.await()

        // Persist the current FCM token now that we know who the user is. (onNewToken often
        // fires before sign-in, so it can't write the token itself.) Powers circle pushes.
        runCatching {
            val token = FirebaseMessaging.getInstance().token.await()
            ref.update("fcmToken", token).await()
        }
    }
}
