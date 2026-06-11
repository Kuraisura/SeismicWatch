package com.gising.data.model.firebase

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * One message in a 1:1 conversation, stored at `chats/{chatId}/messages/{messageId}`.
 *
 * `chatId` is the deterministic pairing of the two participants' uids, sorted and joined with `_`
 * (see [com.gising.data.repository.ChatRepository.chatIdFor]), so both sides resolve the same
 * document without a lookup. History is append-only — messages are never edited or deleted, which
 * keeps the Security Rules trivial and the write cost minimal (one doc per message on the free tier).
 */
data class ChatMessage(
    @DocumentId val id: String = "",
    val senderUid: String = "",
    val text: String = "",
    @ServerTimestamp val sentAt: Date? = null,
)
