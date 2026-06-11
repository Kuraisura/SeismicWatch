package com.gising.emergency

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.gising.R

/**
 * Central notification plumbing for the emergency subsystem.
 *
 * The most important method here is [buildFullScreenSos]: triggers (tile, widget, power button)
 * usually fire while the app is in the background or the device is locked, where Android forbids
 * a direct `startActivity`. A high-priority notification carrying a `setFullScreenIntent` is the
 * one sanctioned way to slam the panic UI onto the screen over the lock screen.
 *
 * Caveat (Android 14+): `USE_FULL_SCREEN_INTENT` is auto-granted only to apps whose core function
 * is calling/alarms; for others the system may downgrade the FSI to a heads-up notification unless
 * the user grants it in settings. We set CATEGORY_CALL to maximise eligibility and degrade safely.
 */
object EmergencyNotifications {

    const val CHANNEL_SOS = "sos_active"
    const val CHANNEL_INCOMING = "sos_incoming"
    const val CHANNEL_SERVICE = "emergency_service"

    const val ID_SOS_ACTIVE = 4201
    const val ID_MESH_SERVICE = 4301
    const val ID_POWER_SERVICE = 4302

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return

        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_SOS, "Active SOS", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Your outgoing emergency alert."
                setBypassDnd(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_INCOMING, "Nearby SOS", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Emergency signals relayed from people near you."
                setBypassDnd(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_SERVICE, "Emergency standby", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Keeps panic gestures and the offline relay running."
            }
        )
    }

    /**
     * The full-screen-intent panic notification. Launching [SosActivity] which (re)binds to the
     * already-armed [SosController] snapshot.
     */
    fun buildFullScreenSos(context: Context, source: SosSource): Notification {
        ensureChannels(context)
        val activity = Intent(context, SosActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(SosActivity.EXTRA_SOURCE, source.name)
            putExtra(SosActivity.EXTRA_FROM_NOTIFICATION, true)
        }
        val pi = PendingIntent.getActivity(
            context, source.ordinal, activity,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, CHANNEL_SOS)
            .setSmallIcon(R.drawable.ic_seismic_wave)
            .setContentTitle("SOS arming…")
            .setContentText("Tap to open. Your emergency alert is being sent.")
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(pi, true)
            .setContentIntent(pi)
            .build()
    }

    /** Low-importance notification used to keep the mesh / power-button services foregrounded. */
    fun buildServiceNotification(context: Context, title: String, text: String): Notification {
        ensureChannels(context)
        return NotificationCompat.Builder(context, CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_seismic_wave)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    /** Heads-up alert when a neighbour's SOS reaches us over the mesh. */
    fun showIncomingMesh(context: Context, packet: SosPacket) {
        ensureChannels(context)
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val maps = "https://maps.google.com/?q=${packet.lat},${packet.lon}"
        val open = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(maps))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pi = PendingIntent.getActivity(
            context, packet.id.hashCode(), open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CHANNEL_INCOMING)
            .setSmallIcon(R.drawable.ic_seismic_wave)
            .setContentTitle("Nearby SOS — ${packet.category}")
            .setContentText("Someone ${if (packet.hopCount > 0) "(${packet.hopCount} hops away) " else ""}needs help. Tap for location.")
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        nm.notify(packet.id.hashCode(), n)
    }
}
