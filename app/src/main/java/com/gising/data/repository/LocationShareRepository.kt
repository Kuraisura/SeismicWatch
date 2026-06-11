package com.gising.data.repository

import com.gising.data.model.firebase.LiveLocation
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Writes THIS device's location into the caller's own member document in each circle they
 * are actively sharing with. Reads of other people's locations happen through
 * [CircleRepository.observeMembers] — this class only ever writes the caller's own doc,
 * which is exactly what the Security Rules permit.
 */
class LocationShareRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {

    /**
     * Pushes [location] into the caller's member doc for each circle id in [circleIds].
     * Callers pass only circles where sharing is currently enabled (global + per-circle).
     */
    suspend fun pushLocation(circleIds: Collection<String>, location: LiveLocation): Result<Unit> =
        runCatching {
            val me = auth.currentUser?.uid ?: return@runCatching
            if (circleIds.isEmpty()) return@runCatching
            val batch = firestore.batch()
            val payload = mapOf(
                "lat" to location.lat,
                "lng" to location.lng,
                "accuracy" to location.accuracy,
                "batteryPct" to location.batteryPct,
                "isCharging" to location.isCharging,
                "updatedAt" to location.updatedAt,
            )
            circleIds.forEach { circleId ->
                val ref = firestore.collection("circles").document(circleId)
                    .collection("members").document(me)
                batch.update(ref, mapOf("lastLocation" to payload))
            }
            batch.commit().await()
        }
}
