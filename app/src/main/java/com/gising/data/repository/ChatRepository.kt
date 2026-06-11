package com.gising.data.repository

import com.gising.data.model.firebase.ChatMessage
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.Date

/**
 * Lightweight 1:1 chat against Firestore — free-tier friendly (one small doc per message, no Cloud
 * Functions, no extra backend). Conversations only exist between real signed-in users (circle
 * members); phone-only emergency contacts keep using SMS.
 *
 * Layout:
 *   chats/{chatId}                       summary (participants[], lastMessage, lastMessageAt)
 *   chats/{chatId}/messages/{messageId}  append-only message log
 *
 * [chatIdFor] makes the chatId deterministic from the two uids, so either participant resolves the
 * same conversation without a lookup. The Security Rules (see `firestore.rules`) are the real
 * authorization boundary; this client never assumes it is trusted.
 */
class ChatRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {
    val myUid: String? get() = auth.currentUser?.uid

    private fun chats() = firestore.collection("chats")
    private fun messages(chatId: String) = chats().document(chatId).collection("messages")

    /** Deterministic conversation id for the current user and [peerUid] (order-independent). */
    fun chatIdFor(peerUid: String): String {
        val me = myUid ?: ""
        return listOf(me, peerUid).sorted().joinToString("_")
    }

    /** Live message stream for the conversation with [peerUid], oldest → newest. */
    fun observeMessages(peerUid: String): Flow<List<ChatMessage>> = callbackFlow {
        if (myUid == null) { trySend(emptyList()); close(); return@callbackFlow }
        val reg = messages(chatIdFor(peerUid))
            .orderBy("sentAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null) { trySend(emptyList()); return@addSnapshotListener }
                trySend(snap?.toObjects(ChatMessage::class.java).orEmpty())
            }
        awaitClose { reg.remove() }
    }

    /**
     * Appends [text] to the conversation with [peerUid] and refreshes the chat summary doc in one
     * batched write. The summary's `participants` array is what the rules authorize reads against.
     *
     * Both writes carry an [expireAt] 7 days out (kept only as a convenience marker — it lets you flip
     * on Firestore's server-side TTL later if you ever move to the Blaze plan). Retention itself is
     * handled for FREE by [purgeExpiredConversations], which the app runs on launch on the Spark plan.
     *
     * NOTE: this is a single atomic batch. The message-create Security Rule authorizes the sender by
     * deriving the two participant uids straight from `chatId` (`chatId.split('_')`) — it does NOT
     * `get()` the summary doc — so the very first message of a brand-new conversation is no longer
     * rejected for referencing a summary that the same batch is still creating. (That read-ordering
     * dependency is the original "message sends but stays blank" bug.)
     */
    suspend fun sendMessage(peerUid: String, text: String) {
        val me = myUid ?: return
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        val chatId = chatIdFor(peerUid)
        val chatDoc = chats().document(chatId)
        val msgDoc = messages(chatId).document()
        val expireAt = Timestamp(Date(System.currentTimeMillis() + RETENTION_MS))

        val batch = firestore.batch()
        batch.set(
            msgDoc,
            mapOf(
                "senderUid" to me,
                "text" to trimmed,
                "sentAt" to FieldValue.serverTimestamp(),
                "expireAt" to expireAt,
            ),
        )
        batch.set(
            chatDoc,
            mapOf(
                "participants" to listOf(me, peerUid).sorted(),
                "lastMessage" to trimmed,
                "lastMessageAt" to FieldValue.serverTimestamp(),
                "lastSenderUid" to me,
                "expireAt" to expireAt,
            ),
            SetOptions.merge(),
        )
        batch.commit().await()
    }

    /**
     * Free, client-side 7-day retention. Firestore's server-side TTL requires the paid Blaze plan;
     * this project runs on the free Spark plan, so the app itself deletes conversations that have had
     * no new message for [RETENTION_MS]. Call this on launch / when the chat list opens — it's cheap
     * (one array-contains query) and quietly no-ops when there's nothing to purge or the user is
     * offline.
     *
     * For each stale thread it deletes the whole `messages` subcollection (in ≤400-doc batches, the
     * Firestore batch limit) and then the summary doc, so the conversation disappears from the inbox.
     * The `lastMessageAt` cutoff is filtered client-side to avoid needing a composite index.
     */
    suspend fun purgeExpiredConversations() {
        val me = myUid ?: return
        val cutoffMs = System.currentTimeMillis() - RETENTION_MS

        val threads = runCatching {
            chats().whereArrayContains("participants", me).get().await()
        }.getOrNull() ?: return

        for (thread in threads.documents) {
            val last = (thread.get("lastMessageAt") as? Timestamp)?.toDate()?.time ?: continue
            if (last >= cutoffMs) continue // still active — keep it

            runCatching {
                val msgs = messages(thread.id).get().await().documents
                msgs.chunked(400).forEach { chunk ->
                    val batch = firestore.batch()
                    chunk.forEach { batch.delete(it.reference) }
                    batch.commit().await()
                }
                thread.reference.delete().await()
            }
        }
    }

    companion object {
        /** Conversations with no new message for this long are purged (client-side) on next launch. */
        const val RETENTION_MS = 7L * 24 * 60 * 60 * 1000
    }
}
