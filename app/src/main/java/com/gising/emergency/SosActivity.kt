package com.gising.emergency

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat

/**
 * The dedicated panic surface. It is deliberately its OWN Activity (not a tab in MainActivity) so
 * it can be slammed onto the screen over the lock screen from a trigger's full-screen-intent
 * notification, with the keyguard dismissed and the screen woken.
 *
 * It never starts its own episode blindly: if a trigger already armed [SosController], it simply
 * attaches to that snapshot; only an in-app/foreground entry with no active episode arms a fresh
 * one. That keeps the "one episode, many surfaces" contract intact.
 */
class SosActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val source = runCatching {
            SosSource.valueOf(intent.getStringExtra(EXTRA_SOURCE) ?: SosSource.IN_APP.name)
        }.getOrDefault(SosSource.IN_APP)

        // If nothing is in flight yet (e.g. opened straight from the app FAB), arm now.
        if (!SosController.snapshot.value.isActive) {
            SosController.arm(applicationContext, source)
        }

        setContent {
            val snapshot by SosController.snapshot.collectAsState()
            androidx.compose.foundation.layout.Box(
                Modifier.fillMaxSize().background(Color(0xFF0D0F12))
            ) {
                PanicScreen(
                    snapshot = snapshot,
                    onSendNow = { SosController.sendNow(applicationContext) },
                    onCancel = { SosController.cancel(applicationContext); finish() },
                    onClassify = { SosController.setCategory(applicationContext, it) },
                    onResolve = { SosController.resolve(applicationContext); finish() },
                )
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(
                android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
    }

    companion object {
        const val EXTRA_SOURCE = "sos_source"
        const val EXTRA_FROM_NOTIFICATION = "from_notification"
    }
}
