package com.gising.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Implements the user's right to erasure (Google Play "Account deletion" requirement).
 *
 * Wipes every piece of the user's data from Firestore, then deletes the auth account:
 *  • circles they OWN are deleted (members + the circle doc),
 *  • circles they merely belong to lose their membership doc + uid,
 *  • invites they created are removed,
 *  • their private profile doc is removed.
 *
 * Best-effort client cascade. For guaranteed server-side cleanup (e.g. if the app is
 * killed mid-delete), deploy the optional `onUserDeleted` Cloud Function — see
 * FIREBASE_SETUP.md. If the auth delete needs a fresh login it surfaces a clear error.
 */
class PrivacyRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {

    sealed interface DeleteResult {
        data object Success : DeleteResult
        data object NeedsRecentLogin : DeleteResult
        data class Failed(val message: String) : DeleteResult
    }

    suspend fun deleteAccountAndData(): DeleteResult {
        val me = auth.currentUser?.uid ?: return DeleteResult.Failed("You're not signed in.")
        return try {
            val myCircles = firestore.collection("circles")
                .whereArrayContains("memberUids", me).get().await()

            for (doc in myCircles.documents) {
                val isOwner = doc.getString("ownerUid") == me
                if (isOwner) {
                    val members = firestore.collection("circles").document(doc.id)
                        .collection("members").get().await()
                    val batch = firestore.batch()
                    members.forEach { batch.delete(it.reference) }
                    batch.delete(doc.reference)
                    batch.commit().await()
                } else {
                    val batch = firestore.batch()
                    batch.delete(
                        firestore.collection("circles").document(doc.id)
                            .collection("members").document(me)
                    )
                    batch.update(doc.reference, "memberUids", FieldValue.arrayRemove(me))
                    batch.commit().await()
                }
            }

            firestore.collection("invites").whereEqualTo("createdBy", me).get().await()
                .forEach { it.reference.delete().await() }

            firestore.collection("users").document(me).delete().await()

            auth.currentUser?.delete()?.await()
            DeleteResult.Success
        } catch (e: Exception) {
            if (e.message?.contains("recent", ignoreCase = true) == true) {
                DeleteResult.NeedsRecentLogin
            } else {
                DeleteResult.Failed("Couldn't finish deleting your account. Please try again.")
            }
        }
    }
}
