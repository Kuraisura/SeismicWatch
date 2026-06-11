package com.gising.emergency

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder

/**
 * Opt-in "panic gesture": rapidly press the power button ~5 times to fire an SOS, mirroring the
 * native Android emergency gesture — but reachable even when the native one is disabled.
 *
 * IMPORTANT PLATFORM REALITY: third-party apps CANNOT intercept the physical power key
 * (`KEYCODE_POWER` is consumed by the system before any app sees it). The only Play-compliant
 * approximation is to count rapid screen on/off transitions — each power press toggles the
 * display, so 5 quick presses produce ~5 `ACTION_SCREEN_ON`/`ACTION_SCREEN_OFF` events in a short
 * window. It is imperfect (it won't fire if the screen state doesn't actually change, e.g. an
 * always-on display), which is why this is an explicit opt-in with honest UX copy, not a promise.
 *
 * These broadcasts can only be received by a runtime-registered receiver (manifest registration of
 * SCREEN_ON/OFF has been disallowed since API 26), so we host them in this foreground service.
 */
class PowerButtonService : Service() {

    private val timestamps = ArrayDeque<Long>()

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON, Intent.ACTION_SCREEN_OFF -> registerToggle()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        EmergencyNotifications.ensureChannels(this)
        startInForeground()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        registerReceiver(screenReceiver, filter)
    }

    private fun registerToggle() {
        val now = System.currentTimeMillis()
        timestamps.addLast(now)
        // Keep only events within the detection window.
        while (timestamps.isNotEmpty() && now - timestamps.first() > WINDOW_MS) {
            timestamps.removeFirst()
        }
        if (timestamps.size >= TOGGLE_THRESHOLD) {
            timestamps.clear()
            SosController.arm(applicationContext, SosSource.POWER)
        }
    }

    private fun startInForeground() {
        val n = EmergencyNotifications.buildServiceNotification(
            this, "Panic gesture armed", "Press power rapidly 5× to send an SOS.",
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                EmergencyNotifications.ID_POWER_SERVICE, n,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(EmergencyNotifications.ID_POWER_SERVICE, n)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        runCatching { unregisterReceiver(screenReceiver) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        // 5 power presses ≈ at least 5 screen-state toggles within the window.
        private const val TOGGLE_THRESHOLD = 5
        private const val WINDOW_MS = 3000L

        fun start(context: Context) {
            val intent = Intent(context, PowerButtonService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, PowerButtonService::class.java))
        }
    }
}
