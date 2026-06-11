package com.gising.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** A freshly-arrived message from a peer, used to raise the in-app Messenger-style banner. */
data class IncomingChat(
    val chatId: String,
    val peerUid: String,
    val text: String,
    val atMs: Long,
)

/**
 * Watches every conversation the signed-in user is part of and emits an [IncomingChat] the instant a
 * peer's message lands — this is what drives the heads-up chat banner that pops over the app (like
 * Messenger). It listens to the lightweight `chats/{chatId}` summary docs (one per conversation), so
 * it's a single cheap query rather than a listener per thread.
 *
 * The query uses only `array-contains` (no `orderBy`) so no composite index is required. The first
 * snapshot is treated as a baseline and never replayed as "new"; only genuinely newer messages from
 * someone other than the current user are emitted.
 */
class ChatInboxRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {
    val myUid: String? get() = auth.currentUser?.uid

    fun observeIncoming(): Flow<IncomingChat> = callbackFlow {
        val me = myUid ?: run { close(); return@callbackFlow }

        val seen = HashMap<String, Long>() // chatId → last seen lastMessageAt (ms)
        var primed = false

        val reg = firestore.collection("chats")
            .whereArrayContains("participants", me)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) return@addSnapshotListener
                for (doc in snap.documents) {
                    val ts = (doc.get("lastMessageAt") as? Timestamp)?.toDate()?.time ?: continue
                    val chatId = doc.id
                    val prev = seen.put(chatId, ts)

                    if (!primed) continue                       // baseline pass — don't replay history
                    if (prev != null && ts <= prev) continue    // nothing newer on this thread
                    if ((doc.getString("lastSenderUid") ?: "") == me) continue // my own message

                    @Suppress("UNCHECKED_CAST")
                    val participants = doc.get("participants") as? List<String> ?: continue
                    val peer = participants.firstOrNull { it != me } ?: continue
                    trySend(IncomingChat(chatId, peer, doc.getString("lastMessage").orEmpty(), ts))
                }
                primed = true
            }

        awaitClose { reg.remove() }
    }
}
