package com.gising.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Storm
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gising.data.gibs.GibsFrame
import com.gising.data.model.CycloneType
import com.gising.ui.screens.components.CycloneMapCanvas
import com.gising.ui.theme.SeismicBlinkingDot
import com.gising.ui.theme.SeismicDisplayFont
import com.gising.ui.theme.SeismicFont
import com.gising.ui.theme.SeismicHot
import kotlinx.coroutines.isActive
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private const val REFRESH_INTERVAL_MS = 60_000L

// Translucent tactical card ground used by every floating overlay on the map.
private val cardBg = SeismicHot.Card.copy(alpha = 0.94f)

/**
 * Typhoon Tracker — "SeismicWatch · Tactical Vibrant".
 *
 * A Windy-style live cyclone map: a NASA-GIBS satellite IR time-loop basemap with the currently
 * active tropical cyclone's track (white dashed line + category-colored points). All chrome is the
 * app's red/orange tactical system (Clash/Satoshi). Reads only from Supabase via [CycloneViewModel];
 * shows an empty state when no storm is active.
 */
@Composable
fun CycloneTrackerScreen(
    onBack: () -> Unit,
    viewModel: CycloneViewModel = viewModel(),
) {
    val state by viewModel.ui.collectAsState()
    val track = state.track

    // Keep the picture current while this screen is on top.
    LaunchedEffect(Unit) {
        while (isActive) {
            kotlinx.coroutines.delay(REFRESH_INTERVAL_MS)
            viewModel.refresh()
        }
    }

    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    val selectedPoint = selectedIndex?.let { idx -> track?.points?.getOrNull(idx) }

    // ── Satellite loop playback ───────────────────────────────────────────────────
    val frames = state.frames
    var currentFrame by remember { mutableIntStateOf(0) }
    var isPlaying by remember { mutableStateOf(true) }
    var speed by remember { mutableFloatStateOf(1f) }

    LaunchedEffect(frames.size) {
        if (currentFrame > frames.lastIndex) currentFrame = 0
    }
    LaunchedEffect(isPlaying, speed, frames.size) {
        if (frames.size > 1) {
            while (isActive && isPlaying) {
                val atLast = currentFrame >= frames.lastIndex
                kotlinx.coroutines.delay(if (atLast) 1000L else (400f / speed).toLong())
                currentFrame = if (atLast) 0 else currentFrame + 1
            }
        }
    }

    Box(Modifier.fillMaxSize().background(SeismicHot.Base)) {
        CycloneMapCanvas(
            track = track,
            frames = frames,
            currentFrame = currentFrame.coerceIn(0, frames.lastIndex.coerceAtLeast(0)),
            modifier = Modifier.fillMaxSize(),
            onPointClick = { selectedIndex = it },
        )

        CompositionLocalProvider(LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = SeismicFont)) {
            // ── Top: header + controls ───────────────────────────────────────────
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OverlayIconButton(Icons.Outlined.ArrowBack, "Back", onBack)
                HeaderCard(
                    title = track?.name ?: "No Active Cyclone",
                    subtitle = when {
                        track != null -> track.latest
                            ?.let { "${track.category.label} · as of ${formatFrameTime(it.observedAtMs)}" }
                            ?: track.category.label
                        state.isLoading -> "Checking for active storms…"
                        state.error != null -> "Feed unavailable"
                        else -> "All clear across the Philippine area"
                    },
                    accent = track?.let { Color(it.category.colorInt) } ?: SeismicHot.Safe,
                    active = track != null,
                    signal = track?.let { signalFromType(it.category) } ?: 0,
                    modifier = Modifier.weight(1f),
                )
                OverlayIconButton(Icons.Outlined.Refresh, "Refresh", viewModel::refresh)
            }

            // ── Loading / error ──────────────────────────────────────────────────────
            if (state.isLoading && track == null) {
                CircularProgressIndicator(
                    color = SeismicHot.Red,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            state.error?.let { msg ->
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 32.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(cardBg)
                        .border(1.dp, SeismicHot.Border, RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Text(msg, fontSize = 13.sp, color = SeismicHot.Label)
                }
            }

            // ── Bottom: legend · point popup · loop controls ─────────────────────────
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                selectedPoint?.let { p ->
                    PointPopup(
                        name = p.cycloneName,
                        category = p.type.label,
                        observed = formatFrameTime(p.observedAtMs),
                        accent = Color(p.type.colorInt),
                        onDismiss = { selectedIndex = null },
                    )
                }
                Row(Modifier.fillMaxWidth()) {
                    CategoryLegend()
                    Spacer(Modifier.weight(1f))
                }
                when {
                    frames.size > 1 -> PlaybackBar(
                        frames = frames,
                        currentFrame = currentFrame.coerceIn(0, frames.lastIndex),
                        isPlaying = isPlaying,
                        speed = speed,
                        onPlayPause = { isPlaying = !isPlaying },
                        onScrub = { currentFrame = it; isPlaying = false },
                        onCycleSpeed = { speed = nextSpeed(speed) },
                    )
                    state.imageryUnavailable -> ImageryNote()
                }
            }
        }
    }
}

/** Map a cyclone category to a coarse PAGASA wind-signal number (1–5). */
private fun signalFromType(type: CycloneType): Int = when (type) {
    CycloneType.STY -> 5
    CycloneType.TY -> 4
    CycloneType.STS -> 3
    CycloneType.TS -> 2
    CycloneType.TD -> 1
    CycloneType.UNKNOWN -> 0
}

@Composable
private fun HeaderCard(
    title: String,
    subtitle: String,
    accent: Color,
    active: Boolean,
    signal: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(13.dp))
            .background(cardBg)
            .border(1.dp, SeismicHot.Border, RoundedCornerShape(13.dp))
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(accent.copy(alpha = if (active) 0.22f else 0.15f)),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Outlined.Storm, null, Modifier.size(19.dp), tint = accent) }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontFamily = SeismicDisplayFont,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.4).sp,
                color = SeismicHot.White,
                maxLines = 1,
            )
            Text(subtitle, fontSize = 11.sp, color = SeismicHot.Muted, maxLines = 1)
        }
        if (signal > 0) {
            Box(
                Modifier.clip(RoundedCornerShape(8.dp)).background(accent.copy(alpha = 0.18f))
                    .border(1.dp, accent.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text("SIG $signal", fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp, color = accent)
            }
        } else if (active) {
            SeismicBlinkingDot(accent, 7.dp)
        }
    }
}

@Composable
private fun OverlayIconButton(icon: ImageVector, desc: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(cardBg)
            .border(1.dp, SeismicHot.Border, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, desc, tint = SeismicHot.White, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun CategoryLegend(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(13.dp))
            .background(cardBg)
            .border(1.dp, SeismicHot.Border, RoundedCornerShape(13.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        listOf(
            CycloneType.TD, CycloneType.TS, CycloneType.STS, CycloneType.TY, CycloneType.STY,
        ).forEach { type ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(Color(type.colorInt)))
                Text("${type.code} · ${type.label}", fontSize = 11.sp, color = SeismicHot.Label)
            }
        }
        // 7-day projection key — a hollow amber marker, matching the map's forecast path.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Box(
                Modifier.size(10.dp).clip(CircleShape).background(SeismicHot.Base)
                    .border(1.6.dp, ForecastAmber, CircleShape),
            )
            Text("7-day projection", fontSize = 11.sp, color = SeismicHot.Label)
        }
    }
}

/** Amber used for the dead-reckoned 7-day movement projection (mirrors CycloneMapCanvas). */
private val ForecastAmber = Color(0xFFF5C518)

// ── Satellite loop playback controls ─────────────────────────────────────────────
private val MANILA_ZONE = ZoneId.of("Asia/Manila")
private val FRAME_TIME_FMT = DateTimeFormatter.ofPattern("EEE · d MMM yyyy, h:mm a", Locale.ENGLISH)

private fun formatFrameTime(epochMs: Long): String =
    FRAME_TIME_FMT.format(Instant.ofEpochMilli(epochMs).atZone(MANILA_ZONE))

private fun nextSpeed(speed: Float): Float = when (speed) {
    0.5f -> 1f
    1f -> 2f
    else -> 0.5f
}

@Composable
private fun PlaybackBar(
    frames: List<GibsFrame>,
    currentFrame: Int,
    isPlaying: Boolean,
    speed: Float,
    onPlayPause: () -> Unit,
    onScrub: (Int) -> Unit,
    onCycleSpeed: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(cardBg)
            .border(1.dp, SeismicHot.Border, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                formatFrameTime(frames[currentFrame].timeMs),
                fontFamily = SeismicDisplayFont,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = SeismicHot.White,
            )
            Spacer(Modifier.weight(1f))
            Text("Asia/Manila", fontSize = 10.sp, color = SeismicHot.Muted)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OverlayIconButton(
                if (isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                if (isPlaying) "Pause" else "Play",
                onPlayPause,
            )
            Slider(
                value = currentFrame.toFloat(),
                onValueChange = { onScrub(it.roundToInt()) },
                valueRange = 0f..frames.lastIndex.toFloat(),
                steps = (frames.size - 2).coerceAtLeast(0),
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = SeismicHot.Red,
                    activeTrackColor = SeismicHot.Red,
                    inactiveTrackColor = SeismicHot.Border,
                ),
            )
            SpeedChip(speed, onCycleSpeed)
        }
    }
}

@Composable
private fun SpeedChip(speed: Float, onClick: () -> Unit) {
    val label = when (speed) {
        0.5f -> "0.5x"
        2f -> "2x"
        else -> "1x"
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(SeismicHot.Red.copy(alpha = 0.18f))
            .border(1.dp, SeismicHot.Red.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = SeismicHot.Red)
    }
}

@Composable
private fun ImageryNote() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(cardBg)
            .border(1.dp, SeismicHot.Border, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text("Satellite imagery unavailable — showing base map.", fontSize = 12.sp, color = SeismicHot.Muted)
    }
}

@Composable
private fun PointPopup(
    name: String,
    category: String,
    observed: String,
    accent: Color,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(cardBg)
            .border(1.dp, accent.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(accent))
        Column(Modifier.weight(1f)) {
            Text(name, fontFamily = SeismicDisplayFont, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = SeismicHot.White)
            Text("$category · $observed", fontSize = 11.sp, color = SeismicHot.Muted)
        }
        Box(
            Modifier.size(28.dp).clip(CircleShape).clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Outlined.Close, "Dismiss", tint = SeismicHot.Muted, modifier = Modifier.size(20.dp)) }
    }
}
