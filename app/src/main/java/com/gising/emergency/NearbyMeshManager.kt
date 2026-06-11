package com.gising.emergency

import android.content.Context
import android.util.Log
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import java.util.Collections

/**
 * Offline peer-to-peer SOS relay built on Google's Nearby Connections API (BLE + Wi-Fi Aware/
 * hotspot). When cell towers are gone, devices running SeismicWatch form ~100 m clusters and bounce
 * survival beacons to one another.
 *
 * Nearby has NO native multi-hop, so we implement a flood-relay ourselves:
 *  - [P2P_CLUSTER] lets every device connect to many peers at once (an M-to-N mesh).
 *  - Connections auto-accept — in a real emergency there is no time to confirm a pairing code,
 *    and the payload only contains coordinates the user is deliberately broadcasting.
 *  - Every received [SosPacket] is de-duplicated by id (LRU set). First sight → notify the user
 *    locally AND, if the packet still has TTL, decrement + re-broadcast to all OTHER peers. That
 *    forwarding is what turns 100 m clusters into a daisy-chain that can cross a whole barangay.
 *
 * Real-device only: Nearby does not run on emulators.
 */
class NearbyMeshManager(private val appContext: Context) {

    private val client: ConnectionsClient = Nearby.getConnectionsClient(appContext)
    private val strategy = Strategy.P2P_CLUSTER

    private val connectedEndpoints = Collections.synchronizedSet(mutableSetOf<String>())

    // Bounded LRU of packet ids we've already handled, to stop relay storms.
    private val seen = Collections.synchronizedMap(object : LinkedHashMap<String, Boolean>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?) = size > 256
    })

    @Volatile private var running = false

    fun start() {
        if (running) return
        running = true
        startAdvertising()
        startDiscovery()
    }

    fun stop() {
        running = false
        runCatching { client.stopAllEndpoints() }
        runCatching { client.stopAdvertising() }
        runCatching { client.stopDiscovery() }
        connectedEndpoints.clear()
    }

    /** Inject a locally-originated SOS into the mesh. */
    fun broadcast(packet: SosPacket) {
        seen[packet.id] = true // never re-handle our own packet as if inbound
        sendToAll(packet)
    }

    private fun sendToAll(packet: SosPacket, except: String? = null) {
        val targets = synchronized(connectedEndpoints) { connectedEndpoints.toList() }
            .filter { it != except }
        if (targets.isEmpty()) return
        val payload = Payload.fromBytes(packet.toBytes())
        runCatching { client.sendPayload(targets, payload) }
            .onFailure { Log.w(TAG, "sendPayload failed", it) }
    }

    private fun startAdvertising() {
        val options = AdvertisingOptions.Builder().setStrategy(strategy).build()
        client.startAdvertising(deviceName(), SERVICE_ID, connectionCallback, options)
            .addOnFailureListener { Log.w(TAG, "advertise failed", it) }
    }

    private fun startDiscovery() {
        val options = DiscoveryOptions.Builder().setStrategy(strategy).build()
        client.startDiscovery(SERVICE_ID, discoveryCallback, options)
            .addOnFailureListener { Log.w(TAG, "discover failed", it) }
    }

    private val discoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            // Connect to anyone advertising our SOS service.
            runCatching { client.requestConnection(deviceName(), endpointId, connectionCallback) }
        }
        override fun onEndpointLost(endpointId: String) {
            connectedEndpoints.remove(endpointId)
        }
    }

    private val connectionCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            // Auto-accept: no pairing dialog during an emergency.
            client.acceptConnection(endpointId, payloadCallback)
        }
        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.isSuccess) connectedEndpoints.add(endpointId)
        }
        override fun onDisconnected(endpointId: String) {
            connectedEndpoints.remove(endpointId)
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            val bytes = payload.asBytes() ?: return
            val packet = SosPacket.fromBytes(bytes) ?: return

            // De-dupe: only the FIRST sighting of a packet id does anything.
            if (seen.put(packet.id, true) != null) return

            // Surface to this device's user.
            EmergencyNotifications.showIncomingMesh(appContext, packet)

            // Relay onward (multi-hop) if it still has life, never back to the sender.
            packet.forwarded()?.let { sendToAll(it, except = endpointId) }
        }
        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {}
    }

    private fun deviceName(): String = "gising-${android.os.Build.MODEL}".take(64)

    companion object {
        private const val TAG = "NearbyMesh"
        private const val SERVICE_ID = "com.gising.SOS_MESH"
    }
}
