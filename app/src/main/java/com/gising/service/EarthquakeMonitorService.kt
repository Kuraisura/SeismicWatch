package com.gising.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.*
import androidx.core.app.NotificationCompat
import com.gising.MainActivity
import com.gising.R
import com.gising.data.model.*
import com.gising.data.repository.EarthquakeRepository
import com.gising.data.repository.SeismicDatabase
import com.gising.data.repository.SettingsStore
import com.gising.data.supabase.SupabaseEarthquake
import com.gising.data.supabase.SupabaseEarthquakeRepository
import com.gising.data.supabase.expectsDamage
import com.gising.data.supabase.toDomain
import com.gising.ui.screens.AlertActivity
import kotlinx.coroutines.*

class EarthquakeMonitorService : Service() {

    companion object {
        const val CHANNEL_MONITOR = "seismic_monitor"
        const val CHANNEL_ALERT = "seismic_alert"
        const val NOTIF_MONITOR_ID = 1001
        const val ACTION_START = "START_MONITOR"
        const val ACTION_STOP = "STOP_MONITOR"

        fun start(context: Context) {
            val intent = Intent(context, EarthquakeMonitorService::class.java)
                .setAction(ACTION_START)
            // startForegroundService only exists on Android 8.0+ (Oreo).
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, EarthquakeMonitorService::class.java)
                .setAction(ACTION_STOP)
            context.startService(intent)
        }

        /** FLAG_IMMUTABLE only exists from API 23; combine safely below it. */
        private val immutableFlag =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
    }

    private val repo = EarthquakeRepository()                 // shaking prediction / attenuation math
    private val supabaseRepo = SupabaseEarthquakeRepository()  // primary data source (PHIVOLCS via Supabase)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var pollingJob: Job? = null
    private var realtimeJob: Job? = null

    // User location (last known — fetched from LocationProvider)
    private var userLat = 14.5995  // default: Manila
    private var userLon = 120.9842

    // Settings (loaded from DataStore each cycle)
    private var minMagnitude = 4.0
    private var pollingIntervalMs = 30_000L   // 30 seconds — far faster than OS

    private fun notificationManager(): NotificationManager =
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        // Best-effort: use the device's last known location for distance/shaking estimates.
        LocationProvider.lastKnown(this)?.let { (lat, lon) ->
            userLat = lat
            userLon = lon
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_MONITOR_ID, buildMonitorNotification())
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        startPolling()
        startRealtime()
        return START_STICKY
    }

    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = scope.launch {
            while (isActive) {
                checkForEarthquakes()
                delay(pollingIntervalMs)
            }
        }
    }

    /**
     * Live WebSocket listener: alerts the instant the PHIVOLCS Edge Function inserts a new
     * bulletin into Supabase, instead of waiting for the next poll. The poll remains as a
     * catch-up/fallback. The collector auto-reconnects on transient errors.
     */
    private fun startRealtime() {
        realtimeJob?.cancel()
        realtimeJob = scope.launch {
            while (isActive) {
                try {
                    supabaseRepo.observeInserts().collect { row -> handleRealtimeInsert(row) }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Connection dropped — back off briefly, then re-subscribe.
                    delay(5_000L)
                }
            }
        }
    }

    private suspend fun handleRealtimeInsert(row: SupabaseEarthquake) {
        val dao = SeismicDatabase.getInstance(this).earthquakeDao()
        LocationProvider.lastKnown(this)?.let { (lat, lon) ->
            userLat = lat
            userLon = lon
        }
        val settings = SettingsStore(this).current()
        val eq = row.toDomain()
        dao.upsertAll(listOf(eq))

        val prediction = repo.computeShakingPrediction(eq, userLat, userLon)
        // Fire on an explicit PHIVOLCS damage flag OR the computed magnitude/MMI threshold.
        if (row.expectsDamage() || shouldAlert(prediction)) {
            fireAlert(eq, prediction)
            dispatchFamilyAlerts(eq, settings)
            dao.markAlerted(eq.id) // keep the poll from re-alerting the same row
        }
    }

    private suspend fun checkForEarthquakes() {
        val dao = SeismicDatabase.getInstance(this).earthquakeDao()

        // Refresh the user's location and settings each cycle.
        LocationProvider.lastKnown(this)?.let { (lat, lon) ->
            userLat = lat
            userLon = lon
        }
        val settings = SettingsStore(this).current()
        minMagnitude = settings.minMagnitude
        pollingIntervalMs = (settings.pollingSeconds * 1000L).coerceAtLeast(15_000L)

        // Catch-up poll against Supabase (the realtime listener handles instant alerts).
        val allEvents = supabaseRepo.fetchLatestQuakes()
            .distinctBy { it.id }
            .sortedByDescending { it.timeMs }

        if (allEvents.isEmpty()) return

        dao.upsertAll(allEvents)

        // Purge events older than 7 days
        val cutoff = System.currentTimeMillis() - (7 * 24 * 3600_000L)
        dao.purgeOlderThan(cutoff)

        // Check for events not yet alerted
        val unalerted = dao.getUnalerted()
            .filter { it.magnitude >= minMagnitude }
            .filter { it.timeMs > System.currentTimeMillis() - 3_600_000 } // last hour

        for (event in unalerted) {
            val prediction = repo.computeShakingPrediction(event, userLat, userLon)
            if (shouldAlert(prediction)) {
                fireAlert(event, prediction)
                dispatchFamilyAlerts(event, settings)
                dao.markAlerted(event.id)
            }
        }
    }

    /**
     * Auto-notify the user's saved family/friends. SMS + the auto-call are the only
     * truly automatic channels Android/Facebook permit; Messenger/WhatsApp are one-tap
     * from the alert screen.
     */
    private suspend fun dispatchFamilyAlerts(eq: Earthquake, settings: SettingsStore.Snapshot) {
        if (!settings.familyAlerts) return
        val contactDao = SeismicDatabase.getInstance(this).contactDao()
        val contacts = contactDao.getAll()
        if (contacts.isEmpty()) return

        val message = AlertDispatcher.buildMessage(eq.magnitude, eq.place, userLat, userLon)

        if (settings.autoSms) {
            AlertDispatcher.sendAutoSms(this, contacts, message)
        }
        // Only auto-call for genuinely major events to avoid nuisance calls.
        if (settings.autoCall && eq.magnitude >= 6.0) {
            AlertDispatcher.autoCallPrimary(this, contactDao.getPrimary())
        }
    }

    private fun shouldAlert(prediction: ShakingPrediction): Boolean {
        // Alert if shaking will be felt locally OR it's a major event globally
        return prediction.estimatedMMI >= 3.0 ||
                prediction.earthquake.magnitude >= 6.0 ||
                prediction.earthquake.tsunami == 1
    }

    /**
     * A genuinely SEVERE / imminent event — the only tier that triggers the forced max-volume,
     * DND-breaking siren. Ordinary "felt" alerts keep the normal heads-up + vibration.
     */
    private fun isSevere(earthquake: Earthquake, prediction: ShakingPrediction): Boolean =
        prediction.estimatedMMI >= 6.0 ||
                earthquake.magnitude >= 6.5 ||
                earthquake.tsunami == 1

    private fun fireAlert(earthquake: Earthquake, prediction: ShakingPrediction) {
        val severe = isSevere(earthquake, prediction)

        // 1. Full-screen alert activity (overrides lock screen)
        val alertIntent = Intent(this, AlertActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("earthquake_id", earthquake.id)
            putExtra("magnitude", earthquake.magnitude)
            putExtra("place", earthquake.place)
            putExtra("depth_km", earthquake.depth)
            putExtra("distance_km", prediction.distanceKm)
            putExtra("mmi", prediction.estimatedMMI)
            putExtra("arrival_sec", prediction.arrivalEstimateSec)
            putExtra("safety_action", prediction.safetyAction.name)
            putExtra("tsunami", earthquake.tsunami == 1)
            putExtra("user_lat", userLat)
            putExtra("user_lon", userLon)
        }
        runCatching { startActivity(alertIntent) }

        // 2. For severe events, blare the forced-loud siren (max alarm volume, breaks silent/DND).
        //    AlertActivity stops it when the user dismisses; a 90s safety timeout stops it otherwise.
        if (severe) com.gising.emergency.AlarmSirenPlayer.start(this)

        // 3. Heads-up notification (visible even if activity can't show). A full-screen intent makes
        //    the alert reliably launch from the background / lock screen on Android 10+.
        fireHeadsUpNotification(earthquake, prediction, alertIntent)

        // 4. Vibration pattern
        fireVibration(earthquake.magnitude)
    }

    private fun fireHeadsUpNotification(
        earthquake: Earthquake,
        prediction: ShakingPrediction,
        alertIntent: Intent,
    ) {
        val tier = MagnitudeTier.from(earthquake.magnitude)
        val tapIntent = PendingIntent.getActivity(
            this, earthquake.id.hashCode(),
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag
        )
        val fullScreenIntent = PendingIntent.getActivity(
            this, ("fs" + earthquake.id).hashCode(),
            alertIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ALERT)
            .setSmallIcon(R.drawable.ic_seismic_wave)
            .setContentTitle("⚠ M${String.format("%.1f", earthquake.magnitude)} ${tier.label} Earthquake")
            .setContentText("${earthquake.place} · ${
                if (prediction.arrivalEstimateSec > 0)
                    "~${prediction.arrivalEstimateSec}s until shaking"
                else "Shaking may be occurring"
            }")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("${earthquake.place}\n" +
                        "Depth: ${String.format("%.0f", earthquake.depth)} km · " +
                        "${String.format("%.0f", prediction.distanceKm)} km from you\n" +
                        prediction.safetyAction.instruction))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(tapIntent)
            .setFullScreenIntent(fullScreenIntent, true)
            .setVibrate(longArrayOf(0, 300, 100, 300, 100, 600))
            .build()

        notificationManager().notify(earthquake.id.hashCode(), notification)
    }

    private fun fireVibration(magnitude: Double) {
        val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        // Pattern encodes severity: stronger quake = longer, more intense bursts
        val pattern = when {
            magnitude >= 7.0 -> longArrayOf(0, 500, 100, 500, 100, 1000, 100, 1000)
            magnitude >= 6.0 -> longArrayOf(0, 400, 100, 400, 100, 800)
            magnitude >= 5.0 -> longArrayOf(0, 300, 100, 300, 100, 600)
            else -> longArrayOf(0, 200, 100, 200)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val amps = pattern.map { if (it == 0L) 0 else 255 }.toIntArray()
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, amps, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, -1)
        }
    }

    private fun createNotificationChannels() {
        // Notification channels only exist on Android 8.0+ (Oreo). No-op below that.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = notificationManager()

        val monitorChannel = NotificationChannel(
            CHANNEL_MONITOR,
            "Earthquake Monitor",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Background seismic monitoring"
            setShowBadge(false)
        }

        val alertChannel = NotificationChannel(
            CHANNEL_ALERT,
            "Earthquake Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Urgent earthquake notifications"
            enableVibration(true)
            enableLights(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setBypassDnd(true)
        }

        nm.createNotificationChannel(monitorChannel)
        nm.createNotificationChannel(alertChannel)
    }

    private fun buildMonitorNotification(): Notification {
        val intent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            immutableFlag
        )
        return NotificationCompat.Builder(this, CHANNEL_MONITOR)
            .setSmallIcon(R.drawable.ic_seismic_wave)
            .setContentTitle("SeismicWatch is watching")
            .setContentText("Monitoring Philippine seismic activity")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(intent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null
}

// ─── Boot Receiver ───────────────────────────────────────────────────────────
class BootReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            EarthquakeMonitorService.start(context)
        }
    }
}
