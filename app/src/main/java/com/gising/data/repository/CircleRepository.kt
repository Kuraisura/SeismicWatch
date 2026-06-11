package com.gising.data.repository

import com.gising.data.model.firebase.Circle
import com.gising.data.model.firebase.CircleMember
import com.gising.data.model.firebase.Invite
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.security.SecureRandom

/**
 * All circle / membership / invite operations against Firestore.
 *
 * Every method is written so that the Security Rules (see `firestore.rules`) are the real
 * enforcement layer — this client never assumes it is trusted. A member can only ever:
 *  • read circles they belong to and those circles' members,
 *  • write their OWN member document (incl. their location + sharing flag),
 *  • add only their own uid when joining via a valid invite.
 */
class CircleRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {
    private val uid: String? get() = auth.currentUser?.uid

    private fun circles() = firestore.collection("circles")
    private fun members(circleId: String) = circles().document(circleId).collection("members")
    private fun invites() = firestore.collection("invites")

    // ── Reads (live) ─────────────────────────────────────────────────────────

    /** The circles the current user belongs to, kept live. */
    fun observeMyCircles(): Flow<List<Circle>> = callbackFlow {
        val me = uid ?: run { trySend(emptyList()); close(); return@callbackFlow }
        // No orderBy here: arrayContains + orderBy(createdAt) would require a composite index.
        // We sort client-side instead so no manual index setup is needed.
        val reg = circles()
            .whereArrayContains("memberUids", me)
            .addSnapshotListener { snap, err ->
                if (err != null) { trySend(emptyList()); return@addSnapshotListener }
                val list = snap?.toObjects(Circle::class.java).orEmpty()
                    .sortedBy { it.createdAt?.time ?: Long.MAX_VALUE }
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    /** All members (with their last known location) of a circle, kept live. */
    fun observeMembers(circleId: String): Flow<List<CircleMember>> = callbackFlow {
        val reg = members(circleId).addSnapshotListener { snap, err ->
            if (err != null) { trySend(emptyList()); return@addSnapshotListener }
            trySend(snap?.toObjects(CircleMember::class.java).orEmpty())
        }
        awaitClose { reg.remove() }
    }

    /** The caller's own membership document within a circle, kept live. */
    fun observeMyMembership(circleId: String): Flow<CircleMember?> = callbackFlow {
        val me = uid ?: run { trySend(null); close(); return@callbackFlow }
        val reg = members(circleId).document(me).addSnapshotListener { snap, err ->
            if (err != null) { trySend(null); return@addSnapshotListener }
            trySend(snap?.toObject(CircleMember::class.java))
        }
        awaitClose { reg.remove() }
    }

    /**
     * Live list of circle ids the caller is currently sharing location with (their per-circle
     * [CircleMember.sharingEnabled] is true). The global master switch is applied by the
     * caller (the location service) on top of this.
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun observeActiveSharingCircleIds(): Flow<List<String>> =
        observeMyCircles().flatMapLatest { circles ->
            if (circles.isEmpty()) {
                flowOf(emptyList())
            } else {
                combine(
                    circles.map { c ->
                        observeMyMembership(c.id).map { m -> c.id to (m?.sharingEnabled ?: false) }
                    }
                ) { pairs -> pairs.filter { it.second }.map { it.first } }
            }
        }

    /** Every member across all of the caller's circles (deduped), kept live — for the map. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun observeAllMembers(): Flow<List<CircleMember>> =
        observeMyCircles().flatMapLatest { circles ->
            if (circles.isEmpty()) {
                flowOf(emptyList())
            } else {
                combine(circles.map { observeMembers(it.id) }) { lists ->
                    lists.toList().flatten().distinctBy { it.uid }
                }
            }
        }

    // ── Circle lifecycle ──────────────────────────────────────────────────────

    suspend fun createCircle(name: String): Result<String> = runCatchingFirestore("create this circle") {
        val me = uid ?: error("Not signed in")
        val displayName = auth.currentUser?.displayName ?: "Me"
        val ref = circles().document()
        val circle = mapOf(
            "name" to name.trim(),
            "ownerUid" to me,
            "memberUids" to listOf(me),
            "createdAt" to FieldValue.serverTimestamp(),
        )
        ref.set(circle).await()
        members(ref.id).document(me).set(
            mapOf(
                "displayName" to displayName,
                "photoUrl" to (auth.currentUser?.photoUrl?.toString() ?: ""),
                "role" to CircleMember.ROLE_OWNER,
                "sharingEnabled" to true,
                "joinedAt" to FieldValue.serverTimestamp(),
            )
        ).await()
        ref.id
    }

    suspend fun renameCircle(circleId: String, name: String): Result<Unit> = runCatching {
        circles().document(circleId).update("name", name.trim()).await()
    }

    /** Owner deletes a circle (removes members subcollection first). */
    suspend fun deleteCircle(circleId: String): Result<Unit> = runCatching {
        val memberDocs = members(circleId).get().await()
        val batch = firestore.batch()
        memberDocs.forEach { batch.delete(it.reference) }
        batch.delete(circles().document(circleId))
        batch.commit().await()
    }

    /** Current user leaves a circle: delete own member doc + pull uid from memberUids. */
    suspend fun leaveCircle(circleId: String): Result<Unit> = runCatching {
        val me = uid ?: error("Not signed in")
        val batch = firestore.batch()
        batch.delete(members(circleId).document(me))
        batch.update(circles().document(circleId), "memberUids", FieldValue.arrayRemove(me))
        batch.commit().await()
    }

    /**
     * Best-effort: propagate a new display name / photo to the caller's own member document in
     * every circle they belong to, so family see the updated name. Rules allow a member to update
     * their own member doc, and the merge keeps role/sharingEnabled intact.
     */
    suspend fun updateMyDisplayName(name: String, photoUrl: String): Result<Unit> = runCatching {
        val me = uid ?: error("Not signed in")
        val myCircles = circles().whereArrayContains("memberUids", me).get().await()
        if (myCircles.isEmpty) return@runCatching
        val batch = firestore.batch()
        myCircles.documents.forEach { doc ->
            batch.update(
                members(doc.id).document(me),
                mapOf("displayName" to name.trim(), "photoUrl" to photoUrl),
            )
        }
        batch.commit().await()
    }

    /** Owner removes another member. */
    suspend fun removeMember(circleId: String, targetUid: String): Result<Unit> = runCatching {
        val batch = firestore.batch()
        batch.delete(members(circleId).document(targetUid))
        batch.update(circles().document(circleId), "memberUids", FieldValue.arrayRemove(targetUid))
        batch.commit().await()
    }

    /** Toggle whether the current user shares location in this circle ("ghost mode"). */
    suspend fun setSharingEnabled(circleId: String, enabled: Boolean): Result<Unit> = runCatching {
        val me = uid ?: error("Not signed in")
        val ref = members(circleId).document(me)
        if (enabled) {
            ref.update("sharingEnabled", true).await()
        } else {
            // Pausing also clears the stale fix so nobody sees an old position.
            ref.update("sharingEnabled", false, "lastLocation", FieldValue.delete()).await()
        }
    }

    // ── Invites (consent-based join) ────────────────────────────────────────────

    suspend fun createInvite(circleId: String, ttlMillis: Long = DEFAULT_INVITE_TTL): Result<Invite> =
        runCatchingFirestore("create an invite") {
            val me = uid ?: error("Not signed in")
            val circle = circles().document(circleId).get().await().toObject(Circle::class.java)
                ?: error("Circle not found")
            val code = newInviteCode()
            val invite = Invite(
                code = code,
                circleId = circleId,
                circleName = circle.name,
                createdBy = me,
                createdByName = auth.currentUser?.displayName ?: "A family member",
                status = Invite.STATUS_ACTIVE,
                expiresAt = System.currentTimeMillis() + ttlMillis,
            )
            invites().document(code).set(invite).await()
            invite
        }

    suspend fun previewInvite(code: String): Result<Invite> = runCatching {
        invites().document(code.trim().uppercase()).get().await()
            .toObject(Invite::class.java) ?: error("Invite not found")
    }

    suspend fun revokeInvite(code: String): Result<Unit> = runCatching {
        invites().document(code).update("status", Invite.STATUS_REVOKED).await()
    }

    /** Join the circle referenced by [code] after validating it. Adds only the caller. */
    suspend fun joinByCode(code: String): Result<String> = runCatchingFirestore("join this circle") {
        val me = uid ?: error("Not signed in")
        val normalized = code.trim().uppercase()
        val invite = invites().document(normalized).get().await()
            .toObject(Invite::class.java) ?: error("That invite code doesn't exist.")
        require(invite.isUsable(System.currentTimeMillis())) { "This invite has expired or was revoked." }

        // Two sequential writes (NOT a batch): the Security Rules evaluate each write against
        // committed state, so the circle's memberUids must already contain me before my
        // member document is allowed to be created.
        circles().document(invite.circleId)
            .update("memberUids", FieldValue.arrayUnion(me)).await()
        members(invite.circleId).document(me).set(
            mapOf(
                "displayName" to (auth.currentUser?.displayName ?: "New member"),
                "photoUrl" to (auth.currentUser?.photoUrl?.toString() ?: ""),
                "role" to CircleMember.ROLE_MEMBER,
                "sharingEnabled" to true,
                "joinedAt" to FieldValue.serverTimestamp(),
            )
        ).await()
        invite.circleId
    }

    private fun newInviteCode(): String {
        // 7 unambiguous chars (no 0/O/1/I) → ~34^7 space, easy to read aloud / type.
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val rnd = SecureRandom()
        return buildString { repeat(7) { append(alphabet[rnd.nextInt(alphabet.length)]) } }
    }

    /**
     * Like [runCatching], but turns a raw [FirebaseFirestoreException] into a message that
     * actually tells the user (and us) WHY it failed — instead of a generic "try again later".
     * Non-Firestore failures (our own `error()`/`require()` messages) pass through unchanged.
     */
    private suspend fun <T> runCatchingFirestore(action: String, block: suspend () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (e: FirebaseFirestoreException) {
            Result.failure(Exception(mapFirestoreError(e, action), e))
        } catch (e: Exception) {
            Result.failure(e)
        }

    private fun mapFirestoreError(e: FirebaseFirestoreException, action: String): String = when (e.code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED ->
            "Couldn't $action: the server blocked the request. This usually means App Check is " +
                "rejecting a non–Play Store install, or the security rules haven't been deployed yet."
        FirebaseFirestoreException.Code.UNAVAILABLE,
        FirebaseFirestoreException.Code.DEADLINE_EXCEEDED ->
            "Couldn't $action: no connection to the server. Check your internet and try again."
        FirebaseFirestoreException.Code.UNAUTHENTICATED ->
            "Couldn't $action: you're signed out. Sign in again and retry."
        else ->
            "Couldn't $action: ${e.message ?: "unexpected error"}."
    }

    companion object {
        const val DEFAULT_INVITE_TTL = 7L * 24 * 60 * 60 * 1000 // 7 days
    }
}
