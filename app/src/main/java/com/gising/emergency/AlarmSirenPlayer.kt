package com.gising.emergency

import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings

/**
 * The offline "SoS-style" fallback siren for genuinely SEVERE hazards — designed to be unmissable
 * even when the phone is on silent / vibrate / Do-Not-Disturb.
 *
 * When [start] is called it:
 *  1. Breaks total-silence DND — only if the user granted "Do Not Disturb access" — by flipping the
 *     interruption filter to ALARMS (remembering the prior filter to restore it).
 *  2. Forces `STREAM_ALARM` to its maximum (remembering the user's level to restore it afterwards).
 *  3. Loops the device alarm tone on a [MediaPlayer] tagged `USAGE_ALARM`, which is exempt from the
 *     ringer-silent switch at the audio layer, so it sounds through silent/vibrate.
 *  4. Holds a partial wake lock so the siren keeps blaring through Doze.
 *
 * [stop] tears all of that back down and restores the user's volume + DND filter. A safety timeout
 * ([MAX_DURATION_MS]) auto-stops the siren even if the alert UI is never dismissed, so the phone is
 * never left permanently at max volume with DND disabled.
 *
 * Everything here is a local OS API — no network — matching the app's offline-first emergency design.
 * It is a process-wide singleton guarded by `@Synchronized`; calling [start] while already playing is
 * a no-op, and [stop] is always safe to call.
 */
object AlarmSirenPlayer {

    /** Hard cap so a missed dismiss can't leave the siren (or the volume/DND override) running forever. */
    private const val MAX_DURATION_MS = 90_000L

    private var player: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var savedAlarmVolume: Int? = null
    private var savedFilter: Int? = null
    private var appContext: Context? = null

    private val handler = Handler(Looper.getMainLooper())
    private val autoStop = Runnable { stop(null) }

    val isPlaying: Boolean get() = player != null

    @Synchronized
    fun start(context: Context) {
        if (player != null) return // already blaring
        val ctx = context.applicationContext
        appContext = ctx

        val audio = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        // 1) Break total-silence DND (only possible with granted policy access).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.isNotificationPolicyAccessGranted) {
                runCatching {
                    savedFilter = nm.currentInterruptionFilter
                    if (nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL) {
                        nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALARMS)
                    }
                }
            }
        }

        // 2) Force STREAM_ALARM to max, remembering the prior level.
        runCatching {
            savedAlarmVolume = audio.getStreamVolume(AudioManager.STREAM_ALARM)
            val max = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            audio.setStreamVolume(AudioManager.STREAM_ALARM, max, 0)
        }

        // 3) Loop the alarm tone on the ALARM usage (bypasses the ringer-silent switch).
        runCatching {
            val uri = RingtoneManager.getActualDefaultRingtoneUri(ctx, RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getActualDefaultRingtoneUri(ctx, RingtoneManager.TYPE_NOTIFICATION)
                ?: Settings.System.DEFAULT_ALARM_ALERT_URI
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                setDataSource(ctx, uri)
                isLooping = true
                setOnPreparedListener { it.start() }
                setOnErrorListener { _, _, _ -> true }
                prepareAsync()
            }
        }

        // 4) Keep the CPU awake so the siren survives Doze.
        runCatching {
            val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SeismicWatch:siren").apply {
                setReferenceCounted(false)
                acquire(MAX_DURATION_MS)
            }
        }

        handler.postDelayed(autoStop, MAX_DURATION_MS)
    }

    /** Stops the siren and restores volume + DND. [context] may be null; the cached app context is used. */
    @Synchronized
    fun stop(context: Context?) {
        handler.removeCallbacks(autoStop)
        val ctx = (context ?: appContext)?.applicationContext

        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null

        if (ctx != null) {
            savedAlarmVolume?.let { level ->
                val audio = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                runCatching { audio.setStreamVolume(AudioManager.STREAM_ALARM, level, 0) }
            }
            savedAlarmVolume = null

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (nm.isNotificationPolicyAccessGranted) {
                    savedFilter?.let { f -> runCatching { nm.setInterruptionFilter(f) } }
                }
            }
            savedFilter = null
        }

        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        wakeLock = null
    }
}
