package com.gising.emergency

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.min

private val PanicRed = Color(0xFFFF3B30)
private val PanicRedDeep = Color(0xFF7A1418)
private val Verdant = Color(0xFF00C7BE)
private val Slate = Color(0xFF1E2230)

/**
 * The send-first panic UI. Layout adapts to screen size via [BoxWithConstraints] — the giant
 * button is a fraction of the smaller screen dimension, so it stays huge and thumb-reachable on a
 * tiny phone yet doesn't become absurd on a tablet/foldable (content is also width-capped).
 *
 * Flow:
 *  - ARMING  → one enormous button + countdown ring. The ONLY decision offered is "cancel".
 *  - SENT    → status + per-channel delivery + OPTIONAL "what's the threat?" classification chips
 *              (the send already happened; this only refines it) + survival actions.
 */
@Composable
fun PanicScreen(
    snapshot: SosSnapshot,
    onSendNow: () -> Unit,
    onCancel: () -> Unit,
    onClassify: (SosCategory) -> Unit,
    onResolve: () -> Unit,
) {
    var showFirstAid by remember { mutableStateOf(false) }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0F12)),
        contentAlignment = Alignment.TopCenter,
    ) {
        val minSide = min(maxWidth, maxHeight)
        val isArming = snapshot.phase == SosPhase.ARMING

        Column(
            Modifier
                .widthIn(max = 560.dp)
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .padding(top = 48.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (isArming) {
                ArmingView(
                    secondsLeft = snapshot.secondsLeft,
                    buttonSize = minSide * 0.62f,
                    onSendNow = onSendNow,
                    onCancel = onCancel,
                )
            } else {
                SentView(
                    snapshot = snapshot,
                    heroSize = minSide * 0.34f,
                    onClassify = onClassify,
                    onResolve = onResolve,
                    onFirstAid = { showFirstAid = true },
                )
            }
        }
    }

    if (showFirstAid) {
        FirstAidOverlay(onDismiss = { showFirstAid = false })
    }
}

// ─── ARMING ───────────────────────────────────────────────────────────────────────
@Composable
private fun ArmingView(
    secondsLeft: Int,
    buttonSize: androidx.compose.ui.unit.Dp,
    onSendNow: () -> Unit,
    onCancel: () -> Unit,
) {
    Text(
        "SENDING SOS IN",
        color = Color.White.copy(0.7f),
        fontSize = 15.sp,
        letterSpacing = 3.sp,
        fontWeight = FontWeight.Medium,
    )
    Spacer(Modifier.height(8.dp))
    Text("$secondsLeft", color = PanicRed, fontSize = 64.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(24.dp))

    // The giant one-tap button: tap = send NOW (skip countdown). Pulsing countdown ring around it.
    val transition = rememberInfiniteTransition(label = "pulse")
    val pulse by transition.animateFloat(
        0.92f, 1.06f,
        infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse),
        label = "p",
    )
    Box(contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(buttonSize * 1.18f)) {
            drawCircle(PanicRed.copy(alpha = 0.18f), radius = size.minDimension / 2f * pulse)
        }
        Surface(
            onClick = onSendNow,
            modifier = Modifier.size(buttonSize),
            shape = CircleShape,
            color = PanicRed,
            border = BorderStroke(4.dp, Color.White.copy(0.25f)),
        ) {
            Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("SOS", color = Color.White, fontSize = 52.sp, fontWeight = FontWeight.Bold)
                Text("TAP TO SEND NOW", color = Color.White.copy(0.85f), fontSize = 12.sp, letterSpacing = 1.sp)
            }
        }
    }

    Spacer(Modifier.height(40.dp))
    Surface(
        onClick = onCancel,
        shape = RoundedCornerShape(16.dp),
        color = Slate,
        border = BorderStroke(1.dp, Color.White.copy(0.15f)),
        modifier = Modifier.fillMaxWidth().height(56.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text("CANCEL — I'm okay", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
    Spacer(Modifier.height(12.dp))
    Text(
        "Your location is being captured now.",
        color = Color.White.copy(0.45f),
        fontSize = 13.sp,
        textAlign = TextAlign.Center,
    )
}

// ─── SENT / BROADCASTING ────────────────────────────────────────────────────────────
@Composable
private fun SentView(
    snapshot: SosSnapshot,
    heroSize: androidx.compose.ui.unit.Dp,
    onClassify: (SosCategory) -> Unit,
    onResolve: () -> Unit,
    onFirstAid: () -> Unit,
) {
    Box(
        Modifier.size(heroSize).clip(CircleShape).background(PanicRed.copy(0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(heroSize * 0.62f).clip(CircleShape).background(PanicRed),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(heroSize * 0.32f))
        }
    }
    Spacer(Modifier.height(16.dp))
    Text("SOS SENT", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
    Text(
        if (snapshot.location != null) "Your location was attached" else "Sent without a location fix",
        color = Color.White.copy(0.55f),
        fontSize = 13.sp,
    )

    Spacer(Modifier.height(20.dp))
    ChannelStatusList(snapshot.channels)

    Spacer(Modifier.height(24.dp))

    // DELAYED classification — only appears AFTER the alert is out. Never blocks the send.
    Text(
        "What's the emergency? (optional)",
        color = Color.White.copy(0.7f),
        fontSize = 13.sp,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Start,
    )
    Spacer(Modifier.height(10.dp))
    ClassificationChips(selected = snapshot.category, onClassify = onClassify)

    Spacer(Modifier.height(28.dp))

    // Survival actions.
    ActionRow(icon = Icons.Outlined.HealthAndSafety, label = "Offline first-aid", accent = Verdant, onClick = onFirstAid)
    Spacer(Modifier.height(10.dp))
    ActionRow(icon = Icons.Outlined.Close, label = "I'm safe — stand down", accent = Slate, onClick = onResolve)
}

@Composable
private fun ChannelStatusList(channels: List<ChannelResult>) {
    Surface(color = Slate, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            channels.forEach { c ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(c.status)
                    Spacer(Modifier.size(12.dp))
                    Text(c.channel, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.weight(1f))
                    Text(
                        statusLabel(c),
                        color = statusColor(c.status),
                        fontSize = 13.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusDot(status: ChannelStatus) {
    val color = statusColor(status)
    if (status == ChannelStatus.SENDING) {
        val tr = rememberInfiniteTransition(label = "dot")
        val a by tr.animateFloat(0.3f, 1f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "a")
        Box(Modifier.size(12.dp).clip(CircleShape).background(color.copy(alpha = a)))
    } else {
        Box(Modifier.size(12.dp).clip(CircleShape).background(color))
    }
}

private fun statusColor(status: ChannelStatus): Color = when (status) {
    ChannelStatus.SENT -> Verdant
    ChannelStatus.SENDING -> Color(0xFFF0C96B)
    ChannelStatus.FAILED -> PanicRed
    ChannelStatus.SKIPPED -> Color.White.copy(0.35f)
    ChannelStatus.PENDING -> Color.White.copy(0.35f)
}

private fun statusLabel(c: ChannelResult): String = when (c.status) {
    ChannelStatus.SENT -> c.detail.ifBlank { "Sent" }
    ChannelStatus.SENDING -> "Sending…"
    ChannelStatus.FAILED -> c.detail.ifBlank { "Failed" }
    ChannelStatus.SKIPPED -> c.detail.ifBlank { "Skipped" }
    ChannelStatus.PENDING -> "Waiting"
}

@Composable
private fun ClassificationChips(selected: SosCategory, onClassify: (SosCategory) -> Unit) {
    val options = listOf(SosCategory.MEDICAL, SosCategory.FIRE, SosCategory.HAZARD, SosCategory.TRAPPED)
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { cat ->
            val active = selected == cat
            Surface(
                onClick = { onClassify(cat) },
                shape = RoundedCornerShape(12.dp),
                color = if (active) PanicRed else Slate,
                border = BorderStroke(1.dp, if (active) PanicRed else Color.White.copy(0.15f)),
                modifier = Modifier.weight(1f).height(48.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        cat.label,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    accent: Color,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (accent == Slate) Slate else accent.copy(0.15f),
        border = BorderStroke(1.dp, if (accent == Slate) Color.White.copy(0.15f) else accent.copy(0.5f)),
        modifier = Modifier.fillMaxWidth().height(54.dp),
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = if (accent == Slate) Color.White else accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.size(12.dp))
            Text(label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        }
    }
}
