package com.gising.data.model.firebase

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * A user's private profile document at `users/{uid}` in Firestore.
 *
 * Firestore Security Rules restrict this document so it is readable/writable ONLY by its
 * owner — so the email lives here safely. Anything other family members need to see
 * (e.g. [displayName]) is denormalized into the per-circle member document instead, so
 * joining a circle never exposes a person's email.
 *
 * All fields have defaults so Firestore can deserialize the document reflectively.
 */
data class UserProfile(
    @DocumentId val uid: String = "",
    val displayName: String = "",
    val email: String = "",
    val photoUrl: String = "",
    /** FCM registration token for push (invites / SOS). */
    val fcmToken: String = "",
    @ServerTimestamp val createdAt: Date? = null,
    @ServerTimestamp val updatedAt: Date? = null,
)
