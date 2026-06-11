package com.gising.data.model.firebase

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * A consent-based join token, stored at `invites/{code}`.
 *
 * Knowing the [code] is a capability: anyone signed in can read the invite (to preview the
 * circle name) and, if it is [STATUS_ACTIVE] and not past [expiresAt], add THEMSELVES to
 * the circle. They can never add anyone else, and the inviter can [STATUS_REVOKED] it.
 * This is the "both parties consent" half of the connection: the invitee chooses to join.
 */
data class Invite(
    @DocumentId val code: String = "",
    val circleId: String = "",
    val circleName: String = "",
    val createdBy: String = "",
    val createdByName: String = "",
    val status: String = STATUS_ACTIVE,
    /** Epoch millis after which the code is no longer valid. */
    val expiresAt: Long = 0L,
    @ServerTimestamp val createdAt: Date? = null,
) {
    fun isUsable(nowMs: Long): Boolean = status == STATUS_ACTIVE && nowMs < expiresAt

    companion object {
        const val STATUS_ACTIVE = "active"
        const val STATUS_REVOKED = "revoked"
    }
}
