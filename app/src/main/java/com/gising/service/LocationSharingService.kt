package com.gising.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.gising.MainActivity
import com.gising.R
import com.gising.data.model.firebase.LiveLocation
import com.gising.data.repository.CircleRepository
import com.gising.data.repository.LocationShareRepository
import com.gising.data.repository.SettingsStore
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Foreground service that shares THIS device's location with the user's circles.
 *
 * Privacy guarantees baked in here:
 *  • Runs only while the master switch ([SettingsStore.Snapshot.locationSharing]) is on AND
 *    the user is signed in. It self-stops the moment sharing is turned off or all circles
 *    are paused — so the foreground-service notification is an honest "I am sharing now" cue.
 *  • Writes only to circles whose per-circle sharing flag is enabled (ghost mode respected).
 *  • Uses balanced-power accuracy and a modest interval to limit battery + data exposure.
 */
class LocationSharingService : Service() {

    companion object {
        const val CHANNEL_SHARING = "gising_location_sharing"
        const val NOTIF_ID = 2002
        const val ACTION_START = "START_SHARING"
        const val ACTION_STOP = "STOP_SHARING"

        private const val UPDATE_INTERVAL_MS = 60_000L      // 1 min target
        private const val MIN_UPDATE_INTERVAL_MS = 30_000L  // accept fixes as fast as 30s

        fun start(context: Context) {
            val intent = Intent(context, LocationSharingService::class.java).setAction(ACTION_START)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, LocationSharingService::class.java).setAction(ACTION_STOP)
            )
        }

        private val immutableFlag =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val circleRepo = CircleRepository()
    private val shareRepo = LocationShareRepository()
    private val fusedClient by lazy { LocationServices.getFusedLocationProviderClient(this) }

    /** Circle ids we may currently write to (global switch ∧ per-circle flag). */
    @Volatile private var activeCircleIds: List<String> = emptyList()

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return
            val targets = activeCircleIds
            if (targets.isEmpty()) return
            val snapshot = LiveLocation(
                lat = loc.latitude,
                lng = loc.longitude,
                accuracy = loc.accuracy,
                batteryPct = batteryPercent(),
                isCharging = isCharging(),
                updatedAt = System.currentTimeMillis(),
            )
            scope.launch { shareRepo.pushLocation(targets, snapshot) }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()

        // Keep the live set of shareable circles in sync; stop ourselves when nothing is
        // shareable or the user signs out, so we never run (or notify) without reason.
        scope.launch {
            combine(
                SettingsStore(this@LocationSharingService).settings,
                circleRepo.observeActiveSharingCircleIds(),
            ) { settings, ids ->
                if (settings.locationSharing && FirebaseAuth.getInstance().currentUser != null) ids
                else emptyList()
            }.collect { ids ->
                activeCircleIds = ids
                if (ids.isEmpty()) stopSelf()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotification())
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        requestUpdates()
        return START_STICKY
    }

    private fun requestUpdates() {
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        if (!granted) { stopSelf(); return }

        val request = LocationRequest.Builder(
            Priority.PRIORITY_BALANCED_POWER_ACCURACY, UPDATE_INTERVAL_MS
        ).setMinUpdateIntervalMillis(MIN_UPDATE_INTERVAL_MS).build()

        try {
            fusedClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            stopSelf()
        }
    }

    /**
     * Live battery level, 0–100 (or -1 if unknown). Reads the sticky ACTION_BATTERY_CHANGED intent
     * (EXTRA_LEVEL / EXTRA_SCALE) rather than BatteryManager.BATTERY_PROPERTY_CAPACITY: the property
     * API returns a fixed/incorrect value (often a flat 100%) on many devices and emulators, whereas
     * the sticky broadcast is the authoritative, device-accurate reading refreshed by the framework.
     */
    private fun batteryPercent(): Int =
        runCatching {
            val status = registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
            val level = status?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = status?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            if (level >= 0 && scale > 0) (level * 100) / scale else -1
        }.getOrDefault(-1)

    private fun isCharging(): Boolean =
        runCatching {
            val status = registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
            when (status?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1) {
                BatteryManager.BATTERY_STATUS_CHARGING, BatteryManager.BATTERY_STATUS_FULL -> true
                else -> false
            }
        }.getOrDefault(false)

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_SHARING, "Location Sharing", NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shown while you are sharing your live location with family"
            setShowBadge(false)
        }
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val tap = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), immutableFlag
        )
        return NotificationCompat.Builder(this, CHANNEL_SHARING)
            .setSmallIcon(R.drawable.ic_seismic_wave)
            .setContentTitle("Sharing your location")
            .setContentText("Your family circle can see where you are. Tap to manage.")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(tap)
            .build()
    }

    override fun onDestroy() {
        runCatching { fusedClient.removeLocationUpdates(locationCallback) }
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null
}
