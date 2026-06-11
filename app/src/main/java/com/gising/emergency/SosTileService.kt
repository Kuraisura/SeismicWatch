package com.gising.emergency

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.gising.R

/**
 * Quick Settings tile: slide down the shade — even over the lock screen — and tap "SOS" once.
 * No unlock, no app launch, no menus. This is the single fastest software trigger Android offers.
 *
 * The tap arms [SosController] immediately (so GPS is captured and the countdown starts the moment
 * the finger lands) and then brings the panic UI forward. On API 34+ `startActivityAndCollapse`
 * requires a PendingIntent; older versions take the Intent directly.
 */
class SosTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = if (SosController.snapshot.value.isActive) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            label = "SOS"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                subtitle = if (SosController.snapshot.value.isActive) "Sending…" else "Tap for help"
            }
            icon = Icon.createWithResource(this@SosTileService, R.drawable.ic_seismic_wave)
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        // Arm first — this captures location and starts the countdown without waiting for the UI.
        SosController.arm(applicationContext, SosSource.TILE)
        unlockAndOpen()
    }

    private fun unlockAndOpen() {
        val intent = Intent(this, SosActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(SosActivity.EXTRA_SOURCE, SosSource.TILE.name)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(
                    this, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            )
        } else {
            @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
            startActivityAndCollapseCompat(intent)
        }
    }

    @Suppress("DEPRECATION")
    private fun startActivityAndCollapseCompat(intent: Intent) {
        startActivityAndCollapse(intent)
    }
}
