package com.gising.emergency

import android.content.Context
import android.util.Log
import com.gising.data.repository.SeismicDatabase
import com.gising.data.repository.SettingsStore
import com.gising.service.AlertDispatcher
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

/**
 * Fans a single SOS out across every available channel, newest-survivable-first:
 *
 *   1. Cloud (Firestore)  — richest payload, reaches the whole circle instantly IF online.
 *   2. SMS                — infrastructure-light fallback that works with only a cell signal,
 *                            no data. Fires automatically when the cloud write fails, or always
 *                            when the user has enabled belt-and-suspenders SMS failback.
 *   3. Mesh (Nearby)      — last-ditch P2P relay for when towers themselves are down.
 *
 * Each channel reports its outcome through [onChannel] so the panic UI updates live.
 */
object SosBroadcaster {

    private const val TAG = "SosBroadcaster"
    private const val CLOUD_TIMEOUT_MS = 6000L

    suspend fun broadcast(
        context: Context,
        snapshot: SosSnapshot,
        onChannel: (ChannelResult) -> Unit,
    ) {
        val app = context.applicationContext
        val settings = runCatching { SettingsStore(app).current() }.getOrNull()
        val eventId = UUID.randomUUID().toString()

        // ── 1. Cloud ────────────────────────────────────────────────────────────
        val online = ConnectivityProbe.isOnline(app)
        var cloudOk = false
        if (online) {
            onChannel(ChannelResult(SosSnapshot.CHANNEL_CLOUD, ChannelStatus.SENDING))
            cloudOk = writeCloudSos(eventId, snapshot)
            onChannel(
                ChannelResult(
                    SosSnapshot.CHANNEL_CLOUD,
                    if (cloudOk) ChannelStatus.SENT else ChannelStatus.FAILED,
                    if (cloudOk) "Circle notified" else "Write failed",
                )
            )
        } else {
            onChannel(ChannelResult(SosSnapshot.CHANNEL_CLOUD, ChannelStatus.SKIPPED, "Offline"))
        }

        // ── 2. SMS failback ──────────────────────────────────────────────────────
        // Send SMS when the cloud didn't get through, OR when the user always wants SMS as backup.
        val wantSmsAlways = settings?.smsFailback ?: true
        if (!cloudOk || wantSmsAlways) {
            onChannel(ChannelResult(SosSnapshot.CHANNEL_SMS, ChannelStatus.SENDING))
            val sent = sendSms(app, snapshot)
            onChannel(
                when {
                    sent > 0 -> ChannelResult(SosSnapshot.CHANNEL_SMS, ChannelStatus.SENT, "$sent contact(s)")
                    else -> ChannelResult(SosSnapshot.CHANNEL_SMS, ChannelStatus.FAILED, "No SMS sent")
                }
            )
        } else {
            onChannel(ChannelResult(SosSnapshot.CHANNEL_SMS, ChannelStatus.SKIPPED, "Cloud OK"))
        }

        // ── 3. Mesh (P2P) ────────────────────────────────────────────────────────
        // Starting the Nearby mesh service uses the Nearby permission group + a special-use
        // foreground service, so Play policy requires the user to have accepted the prominent
        // disclosure first. Without consent we skip the channel rather than silently start it.
        if (settings?.meshConsent != true) {
            onChannel(ChannelResult(SosSnapshot.CHANNEL_MESH, ChannelStatus.SKIPPED, "Mesh off"))
        } else {
            onChannel(ChannelResult(SosSnapshot.CHANNEL_MESH, ChannelStatus.SENDING))
            val meshOk = runCatching {
                val packet = SosPacket.origin(
                    originUid = FirebaseAuth.getInstance().currentUser?.uid ?: "anon",
                    lat = snapshot.location?.first ?: 0.0,
                    lon = snapshot.location?.second ?: 0.0,
                    category = snapshot.category.smsTag,
                )
                MeshService.broadcast(app, packet)
                true
            }.getOrElse { false }
            onChannel(
                ChannelResult(
                    SosSnapshot.CHANNEL_MESH,
                    if (meshOk) ChannelStatus.SENT else ChannelStatus.FAILED,
                    if (meshOk) "Beaconing to nearby devices" else "Mesh unavailable",
                )
            )
        }
    }

    /** Re-write the category onto an already-sent cloud event when the user classifies late. */
    suspend fun broadcastClassificationUpdate(context: Context, snapshot: SosSnapshot) {
        if (!ConnectivityProbe.isOnline(context)) return
        runCatching {
            FirebaseFirestore.getInstance().collection("sos_events")
                .whereEqualTo("ownerUid", FirebaseAuth.getInstance().currentUser?.uid)
                .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(1).get().await().documents.firstOrNull()
                ?.reference?.update("category", snapshot.category.name)?.await()
        }.onFailure { Log.w(TAG, "Classification update failed", it) }
    }

    private suspend fun writeCloudSos(eventId: String, snapshot: SosSnapshot): Boolean {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return false
        val db = FirebaseFirestore.getInstance()
        return withTimeoutOrNull(CLOUD_TIMEOUT_MS) {
            runCatching {
                // The read audience = every uid across all of my circles (always incl. me).
                // The Security Rules gate reads on membership in this array.
                val audience = gatherAudience(db, uid)
                val payload = mapOf(
                    "ownerUid" to uid,
                    "audienceUids" to audience,
                    "lat" to (snapshot.location?.first ?: 0.0),
                    "lon" to (snapshot.location?.second ?: 0.0),
                    "hasLocation" to (snapshot.location != null),
                    "category" to snapshot.category.name,
                    "source" to snapshot.source.name,
                    "status" to "active",
                    "createdAt" to FieldValue.serverTimestamp(),
                )
                db.collection("sos_events").document(eventId).set(payload).await()
                true
            }.getOrElse { false }
        } ?: false
    }

    /** Union of memberUids across every circle the user belongs to (plus the user). */
    private suspend fun gatherAudience(db: FirebaseFirestore, uid: String): List<String> {
        val audience = linkedSetOf(uid)
        runCatching {
            db.collection("circles").whereArrayContains("memberUids", uid).get().await()
                .documents.forEach { doc ->
                    (doc.get("memberUids") as? List<*>)?.forEach { m -> (m as? String)?.let(audience::add) }
                }
        }
        return audience.toList()
    }

    private suspend fun sendSms(context: Context, snapshot: SosSnapshot): Int {
        val contacts = runCatching {
            SeismicDatabase.getInstance(context).contactDao().getAll()
        }.getOrElse { emptyList() }
        if (contacts.isEmpty()) return 0
        val msg = AlertDispatcher.buildCompactSms(
            category = snapshot.category,
            lat = snapshot.location?.first,
            lon = snapshot.location?.second,
            timeMs = snapshot.startedAt,
        )
        return AlertDispatcher.sendAutoSms(context, contacts, msg)
    }
}
