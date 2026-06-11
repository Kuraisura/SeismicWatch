package com.gising.emergency

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder

/**
 * Foreground service that hosts the [NearbyMeshManager]. It runs in two situations:
 *  - briefly, while an outgoing SOS is being beaconed, and
 *  - continuously (opt-in "offline relay") so this phone keeps forwarding neighbours' SOS packets
 *    even when the user isn't in an emergency themselves — turning the neighbourhood into a mesh.
 *
 * A foreground service is mandatory because Nearby advertising/discovery must survive the app
 * being backgrounded, and Android kills background radios aggressively.
 */
class MeshService : Service() {

    private var manager: NearbyMeshManager? = null

    override fun onCreate() {
        super.onCreate()
        EmergencyNotifications.ensureChannels(this)
        startInForeground()
        manager = NearbyMeshManager(applicationContext).also { it.start() }
        instance = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // A packet handed in at start time (the outgoing SOS path) gets broadcast immediately.
        intent?.getStringExtra(EXTRA_PACKET)?.let { json ->
            SosPacket.fromBytes(json.toByteArray(Charsets.UTF_8))?.let { manager?.broadcast(it) }
        }
        return START_STICKY
    }

    private fun startInForeground() {
        val n = EmergencyNotifications.buildServiceNotification(
            this, "Offline SOS relay active", "Listening for and forwarding nearby emergency signals.",
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                EmergencyNotifications.ID_MESH_SERVICE, n,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
            )
        } else {
            startForeground(EmergencyNotifications.ID_MESH_SERVICE, n)
        }
    }

    override fun onDestroy() {
        manager?.stop()
        manager = null
        instance = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val EXTRA_PACKET = "packet_json"

        @Volatile private var instance: MeshService? = null

        /** Start (if needed) the mesh and inject an outgoing [packet]. */
        fun broadcast(context: Context, packet: SosPacket) {
            val live = instance
            if (live?.manager != null) {
                live.manager?.broadcast(packet)
                return
            }
            val intent = Intent(context, MeshService::class.java)
                .putExtra(EXTRA_PACKET, packet.toJson())
            startService(context, intent)
        }

        /** Start the relay for continuous neighbourhood forwarding (opt-in setting). */
        fun startRelay(context: Context) {
            startService(context, Intent(context, MeshService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MeshService::class.java))
        }

        private fun startService(context: Context, intent: Intent) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
