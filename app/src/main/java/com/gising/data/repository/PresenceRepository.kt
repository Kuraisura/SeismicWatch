package com.gising.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** A peer's live presence: whether they currently have the app open, and when they were last active. */
data class Presence(
    val online: Boolean = false,
    val lastActiveAtMs: Long? = null,
) {
    /**
     * Messenger-style label. "Active now" ONLY when the peer is genuinely foregrounded (a recent
     * heartbeat), otherwise a relative "active X ago" — never a permanent "Active now".
     */
    fun label(now: Long = System.currentTimeMillis()): String {
        val last = lastActiveAtMs
        return when {
            online && last != null && now - last <= ACTIVE_WINDOW_MS -> "Active now"
            last == null -> "Offline"
            else -> "Active ${relative(now - last)} ago"
        }
    }

    /** True only while the peer is foregrounded and heartbeating — drives the green "active" dot. */
    fun isActiveNow(now: Long = System.currentTimeMillis()): Boolean =
        online && lastActiveAtMs != null && now - lastActiveAtMs <= ACTIVE_WINDOW_MS

    private fun relative(deltaMs: Long): String = when {
        deltaMs < 60_000 -> "moments"
        deltaMs < 3_600_000 -> "${deltaMs / 60_000}m"
        deltaMs < 86_400_000 -> "${deltaMs / 3_600_000}h"
        else -> "${deltaMs / 86_400_000}d"
    }

    companion object {
        /** How fresh a heartbeat must be to still count as "active now" (heartbeat cadence is ~60s). */
        const val ACTIVE_WINDOW_MS = 2L * 60_000
    }
}

/**
 * Real presence for the People/Chat surfaces, replacing the old always-on "Active now" placeholder.
 *
 * Presence lives in a dedicated `presence/{uid}` collection — deliberately NOT on `users/{uid}`,
 * which is owner-only-readable and holds the private profile (FCM token, phone, address). The
 * presence doc carries only low-sensitivity fields (`online`, `lastActiveAt`) and is readable by any
 * signed-in user so peers can see it. The app writes `online=true` + a fresh `lastActiveAt` heartbeat
 * whenever it is foregrounded (see the lifecycle hook in `SeismicWatchApp`) and flips `online=false`
 * when it goes to the background. No new collection or Cloud Function is needed.
 */
class PresenceRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {
    val myUid: String? get() = auth.currentUser?.uid

    private fun userDoc(uid: String) = firestore.collection("presence").document(uid)

    /**
     * Mark the signed-in user active now (called on foreground + on each heartbeat tick). Fire-and-
     * forget: the write persists to Firestore's local cache and syncs when possible, so it's safe to
     * call from a lifecycle callback without a coroutine.
     */
    fun markActive() {
        val uid = myUid ?: return
        runCatching {
            userDoc(uid).set(
                mapOf("online" to true, "lastActiveAt" to FieldValue.serverTimestamp()),
                SetOptions.merge(),
            )
        }
    }

    /** Mark the signed-in user offline, stamping the moment they left (called on background). */
    fun markInactive() {
        val uid = myUid ?: return
        runCatching {
            userDoc(uid).set(
                mapOf("online" to false, "lastActiveAt" to FieldValue.serverTimestamp()),
                SetOptions.merge(),
            )
        }
    }

    /** Live presence for [uid]. Emits on every change to that user's presence fields. */
    fun observe(uid: String): Flow<Presence> = callbackFlow {
        if (uid.isBlank()) { trySend(Presence()); close(); return@callbackFlow }
        val reg = userDoc(uid).addSnapshotListener { snap, err ->
            if (err != null || snap == null) { trySend(Presence()); return@addSnapshotListener }
            val online = snap.getBoolean("online") ?: false
            val last = (snap.get("lastActiveAt") as? Timestamp)?.toDate()?.time
            trySend(Presence(online = online, lastActiveAtMs = last))
        }
        awaitClose { reg.remove() }
    }
}
