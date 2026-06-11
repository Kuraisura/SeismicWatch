package com.gising.ui.screens

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gising.data.model.SafetyAction
import com.gising.ui.theme.*

/**
 * Full-screen earthquake alert — shows BEFORE the phone's built-in system
 * because we poll USGS every 30 seconds and compute arrival time proactively.
 * Appears over the lock screen via SHOW_WHEN_LOCKED + TURN_SCREEN_ON flags.
 */
class AlertActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Turn on screen, show over lock screen
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )

        val magnitude = intent.getDoubleExtra("magnitude", 0.0)
        val place = intent.getStringExtra("place") ?: "Unknown"
        val depthKm = intent.getDoubleExtra("depth_km", 0.0)
        val distanceKm = intent.getDoubleExtra("distance_km", 0.0)
        val mmi = intent.getDoubleExtra("mmi", 0.0)
        val safetyActionName = intent.getStringExtra("safety_action") ?: "NO_ACTION"
        val tsunami = intent.getBooleanExtra("tsunami", false)
        val userLat = intent.getDoubleExtra("user_lat", 14.5995)
        val userLon = intent.getDoubleExtra("user_lon", 120.9842)
        val action = runCatching { SafetyAction.valueOf(safetyActionName) }
            .getOrDefault(SafetyAction.NO_ACTION)

        setContent {
            SeismicWatchTheme(themeMode = ThemeMode.DARK) {
                AlertScreen(
                    magnitude = magnitude,
                    place = place,
                    depthKm = depthKm,
                    distanceKm = distanceKm,
                    mmi = mmi,
                    safetyAction = action,
                    isTsunami = tsunami,
                    userLat = userLat,
                    userLon = userLon,
                    onDismiss = {
                        com.gising.emergency.AlarmSirenPlayer.stop(this)
                        finish()
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        // Backstop: whatever path closes the alert (dismiss, back, system), silence the siren.
        com.gising.emergency.AlarmSirenPlayer.stop(this)
        super.onDestroy()
    }
}

@Composable
private fun AlertScreen(
    magnitude: Double,
    place: String,
    depthKm: Double,
    distanceKm: Double,
    mmi: Double,
    safetyAction: SafetyAction,
    isTsunami: Boolean,
    userLat: Double,
    userLon: Double,
    onDismiss: () -> Unit
) {
    val alertColor = when {
        isTsunami || magnitude >= 7.0 -> SeismicColors.MagnitudeMajor
        magnitude >= 6.0 -> SeismicColors.MagnitudeStrong
        magnitude >= 5.0 -> SeismicColors.MagnitudeModerate
        else -> SeismicColors.MagnitudeLight
    }

    val pulse by rememberInfiniteTransition(label = "alert").animateFloat(
        initialValue = 0.92f, targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            tween(600, easing = EaseInOutSine), RepeatMode.Reverse
        ), label = "p"
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = SeismicColors.Obsidian
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .navigationBarsPadding()
                .statusBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {

            // ── Header ──────────────────────────────────────────────────────
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = if (isTsunami) "⚠ TSUNAMI WARNING" else "⚠ EARTHQUAKE ALERT",
                    style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 3.sp),
                    color = alertColor
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "SeismicWatch detected this event — keep your family informed",
                    style = MaterialTheme.typography.labelSmall,
                    color = SeismicColors.Mist,
                    textAlign = TextAlign.Center
                )
            }

            // ── Magnitude pulse ──────────────────────────────────────────────
            Box(contentAlignment = Alignment.Center) {
                // Outer ring
                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .scale(pulse)
                        .clip(CircleShape)
                        .background(alertColor.copy(0.06f))
                        .border(1.dp, alertColor.copy(0.2f), CircleShape)
                )
                // Mid ring
                Box(
                    modifier = Modifier
                        .size(160.dp)
                        .clip(CircleShape)
                        .background(alertColor.copy(0.10f))
                        .border(1.dp, alertColor.copy(0.3f), CircleShape)
                )
                // Core
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(alertColor.copy(0.18f))
                        .border(2.dp, alertColor.copy(0.6f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = String.format("%.1f", magnitude),
                            style = MaterialTheme.typography.displayMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 48.sp
                            ),
                            color = alertColor
                        )
                        Text("Magnitude", style = MaterialTheme.typography.labelSmall,
                            color = SeismicColors.Mist)
                    }
                }
            }

            // ── Event location ──────────────────────────────────────────────
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = place,
                    style = MaterialTheme.typography.headlineSmall,
                    color = SeismicColors.Chalk,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${String.format("%.0f", distanceKm)} km from you · ${String.format("%.0f", depthKm)} km deep",
                    style = MaterialTheme.typography.labelMedium,
                    color = SeismicColors.Mist
                )
            }

            // ── Action card ──────────────────────────────────────────────────
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = alertColor.copy(0.12f),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, alertColor.copy(0.3f))
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(safetyAction.icon, fontSize = 32.sp)
                    Column {
                        Text(
                            text = "Do this now",
                            style = MaterialTheme.typography.labelMedium,
                            color = alertColor.copy(0.8f)
                        )
                        Text(
                            text = safetyAction.instruction,
                            style = MaterialTheme.typography.headlineSmall,
                            color = SeismicColors.Chalk
                        )
                    }
                }
            }

            // ── Tell my family (one-tap channels) ────────────────────────────
            FamilyAlertButtons(
                magnitude = magnitude,
                place = place,
                userLat = userLat,
                userLon = userLon
            )

            // ── Dismiss ──────────────────────────────────────────────────────
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Text(
                    "Dismiss alert",
                    style = MaterialTheme.typography.labelLarge,
                    color = SeismicColors.Mist
                )
            }
        }
    }
}

@Composable
private fun FamilyAlertButtons(
    magnitude: Double,
    place: String,
    userLat: Double,
    userLon: Double
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val message = remember(magnitude, place, userLat, userLon) {
        com.gising.service.AlertDispatcher.buildMessage(magnitude, place, userLat, userLon)
    }

    // Load saved contacts so the SMS button can pre-fill recipients.
    var contacts by remember {
        mutableStateOf<List<com.gising.data.model.EmergencyContact>>(emptyList())
    }
    LaunchedEffect(Unit) {
        val app = context.applicationContext as com.gising.SeismicApplication
        contacts = app.database.contactDao().getAll()
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "TELL MY FAMILY",
            style = MaterialTheme.typography.labelSmall,
            color = SeismicColors.Mist
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AlertActionButton(Icons.Outlined.Sms, "SMS") {
                com.gising.service.AlertDispatcher.composeSms(context, contacts, message)
            }
            AlertActionButton(Icons.Outlined.Forum, "Messenger") {
                com.gising.service.AlertDispatcher.shareToMessenger(context, message)
            }
            AlertActionButton(Icons.Outlined.Share, "Share") {
                com.gising.service.AlertDispatcher.shareGeneric(context, message)
            }
        }
    }
}

@Composable
private fun AlertActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        border = BorderStroke(1.dp, SeismicColors.Mist.copy(0.5f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Icon(icon, null, modifier = Modifier.size(18.dp), tint = SeismicColors.Chalk)
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = SeismicColors.Chalk)
    }
}
