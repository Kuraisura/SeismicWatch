package com.gising.emergency

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import com.gising.service.LocationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The single brain of the SOS subsystem. Every trigger — the in-app button, the Quick Settings
 * tile, the home-screen widget, the power-button gesture — calls [arm]; every surface observes
 * [snapshot]. Because this is a process-wide `object`, the panic UI launched from a lock-screen
 * notification re-attaches to the SAME in-flight episode rather than starting a new one.
 *
 * Design principle — SEND FIRST, ASK LATER:
 *  - [arm] captures the last-known GPS *immediately* (synchronously), starts a short countdown,
 *    and posts a full-screen panic notification. Nothing about the threat type is required.
 *  - When the countdown elapses (or [sendNow] is called) we broadcast a GENERIC alert.
 *  - [setCategory] lets the user refine the threat AFTER it is already sent; we re-broadcast the
 *    classification so recipients get the upgrade, but the help was already on its way.
 */
object SosController {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _snapshot = MutableStateFlow(SosSnapshot())
    val snapshot: StateFlow<SosSnapshot> = _snapshot.asStateFlow()

    private var countdownJob: Job? = null

    /**
     * Begin an SOS. Idempotent while already active: a second trigger during ARMING/SENT just
     * re-surfaces the UI instead of restarting. [countdownSec] of 0 sends instantly.
     */
    fun arm(context: Context, source: SosSource, countdownSec: Int = 5) {
        val app = context.applicationContext
        EmergencyNotifications.ensureChannels(app)

        if (_snapshot.value.isActive) {
            // Already running — just make sure the UI is up; don't reset the timer.
            postFullScreen(app, source)
            return
        }

        // Capture location synchronously RIGHT NOW, before anything can go wrong.
        val loc = LocationProvider.lastKnown(app)

        _snapshot.value = SosSnapshot(
            phase = if (countdownSec <= 0) SosPhase.BROADCASTING else SosPhase.ARMING,
            secondsLeft = countdownSec,
            category = SosCategory.UNSPECIFIED,
            location = loc,
            source = source,
            channels = listOf(
                ChannelResult(SosSnapshot.CHANNEL_CLOUD, ChannelStatus.PENDING),
                ChannelResult(SosSnapshot.CHANNEL_SMS, ChannelStatus.PENDING),
                ChannelResult(SosSnapshot.CHANNEL_MESH, ChannelStatus.PENDING),
            ),
            startedAt = System.currentTimeMillis(),
        )

        postFullScreen(app, source)

        countdownJob?.cancel()
        countdownJob = scope.launch {
            var remaining = countdownSec
            while (remaining > 0) {
                if (_snapshot.value.phase != SosPhase.ARMING) return@launch
                delay(1000)
                remaining--
                _snapshot.update { it.copy(secondsLeft = remaining) }
            }
            if (_snapshot.value.phase == SosPhase.ARMING) doBroadcast(app)
        }
    }

    /** Skip the rest of the countdown and transmit immediately. */
    fun sendNow(context: Context) {
        if (_snapshot.value.phase != SosPhase.ARMING) return
        countdownJob?.cancel()
        doBroadcast(context.applicationContext)
    }

    /** Cancel — only meaningful during the ARMING grace period. */
    fun cancel(context: Context) {
        if (_snapshot.value.phase != SosPhase.ARMING) return
        countdownJob?.cancel()
        _snapshot.update { it.copy(phase = SosPhase.CANCELLED, secondsLeft = 0) }
        clearNotification(context.applicationContext)
    }

    /** Delayed classification: refine the threat type after the alert is already out. */
    fun setCategory(context: Context, category: SosCategory) {
        val snap = _snapshot.value
        if (!snap.isActive) return
        _snapshot.update { it.copy(category = category) }
        // Re-broadcast the upgrade if we've already sent the generic alert.
        if (snap.phase == SosPhase.SENT || snap.phase == SosPhase.BROADCASTING) {
            scope.launch { SosBroadcaster.broadcastClassificationUpdate(context.applicationContext, _snapshot.value) }
        }
    }

    /** User marks themselves safe; tears the episode down. */
    fun resolve(context: Context) {
        countdownJob?.cancel()
        _snapshot.value = SosSnapshot(phase = SosPhase.IDLE)
        clearNotification(context.applicationContext)
    }

    private fun doBroadcast(app: Context) {
        _snapshot.update { it.copy(phase = SosPhase.BROADCASTING, secondsLeft = 0) }
        scope.launch {
            SosBroadcaster.broadcast(app, _snapshot.value) { result ->
                _snapshot.update { snap ->
                    snap.copy(channels = snap.channels.map { if (it.channel == result.channel) result else it })
                }
            }
            _snapshot.update { it.copy(phase = SosPhase.SENT) }
        }
    }

    private fun postFullScreen(app: Context, source: SosSource) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !NotificationManagerCompat.from(app).areNotificationsEnabled()
        ) return // can't post; the launching surface will start the Activity directly instead
        NotificationManagerCompat.from(app)
            .notify(EmergencyNotifications.ID_SOS_ACTIVE, EmergencyNotifications.buildFullScreenSos(app, source))
    }

    private fun clearNotification(app: Context) {
        NotificationManagerCompat.from(app).cancel(EmergencyNotifications.ID_SOS_ACTIVE)
    }

    /** Convenience for triggers that also need to bring the Activity up directly (foreground case). */
    fun launchActivity(context: Context, source: SosSource) {
        val intent = Intent(context, SosActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(SosActivity.EXTRA_SOURCE, source.name)
        }
        context.startActivity(intent)
    }
}
