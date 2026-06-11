package com.gising.data.model.firebase

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * A "circle" — a family/friends group, stored at `circles/{circleId}`.
 *
 * [memberUids] is denormalized onto the circle document so Security Rules can authorize
 * reads/writes with a single `request.auth.uid in resource.data.memberUids` check, and so
 * a member can query "the circles I belong to" with `whereArrayContains`.
 */
data class Circle(
    @DocumentId val id: String = "",
    val name: String = "",
    val ownerUid: String = "",
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Date? = null,
)

/**
 * One person's membership + most-recent location within a circle, stored at
 * `circles/{circleId}/members/{uid}`.
 *
 * Location lives INSIDE the membership (rather than a global per-user location doc) so the
 * rules are trivially safe: you can only read a member doc of a circle you also belong to,
 * and you can only write your OWN member doc. There is no way to read someone's location
 * without sharing a circle with them, and they can cut it instantly via [sharingEnabled].
 */
data class CircleMember(
    @DocumentId val uid: String = "",
    val displayName: String = "",
    val photoUrl: String = "",
    val role: String = ROLE_MEMBER, // ROLE_OWNER | ROLE_MEMBER
    /** When false, this member's [lastLocation] is not written/updated ("ghost mode"). */
    val sharingEnabled: Boolean = true,
    val lastLocation: LiveLocation? = null,
    @ServerTimestamp val joinedAt: Date? = null,
) {
    val isOwner: Boolean get() = role == ROLE_OWNER

    companion object {
        const val ROLE_OWNER = "owner"
        const val ROLE_MEMBER = "member"
    }
}

/**
 * A point-in-time location snapshot embedded in [CircleMember.lastLocation].
 * [updatedAt] is client epoch millis (kept simple for high-frequency writes).
 */
data class LiveLocation(
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val accuracy: Float = 0f,
    val batteryPct: Int = -1,
    val isCharging: Boolean = false,
    val updatedAt: Long = 0L,
)
