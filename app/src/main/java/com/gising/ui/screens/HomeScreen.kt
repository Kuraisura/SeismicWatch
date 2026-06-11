package com.gising.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import com.gising.R
import com.gising.data.model.*
import com.gising.ui.theme.SeismicDisplayFont
import com.gising.ui.theme.SeismicFont
import com.gising.ui.theme.SeismicColors

/**
 * Home dashboard — "SeismicWatch · Tactical Vibrant".
 *
 * A high-energy emergency dashboard on a deep obsidian ground: seismic grid + radial ember glows
 * + drifting scanline, a sticky header with a neon hot-line, a live status bar, horizontally
 * scrolling stat cards, urgent alert cards, monitored-region pills, and an activity timeline — all
 * driven by the real [MainViewModel] feeds (PHIVOLCS quakes, GDACS/PAGASA cyclones).
 */

// Brand typefaces (Clash Grotesk display + Satoshi body) come from the shared theme:
// SeismicFont = Satoshi (body), SeismicDisplayFont = Clash Grotesk (titles / hero).

// ─── Local design tokens ─────────────────────────────────────────────────────
// Delegates to the shared, theme-aware SeismicHot tokens so Home re-skins with the selected theme.
private object Hot {
    val Base: Color get() = com.gising.ui.theme.SeismicHot.Base
    val Card: Color get() = com.gising.ui.theme.SeismicHot.Card
    val Border: Color get() = com.gising.ui.theme.SeismicHot.Border
    val Red: Color get() = com.gising.ui.theme.SeismicHot.Red
    val Orange: Color get() = com.gising.ui.theme.SeismicHot.Orange
    val Label: Color get() = com.gising.ui.theme.SeismicHot.Label
    val Muted: Color get() = com.gising.ui.theme.SeismicHot.Muted
    val White: Color get() = com.gising.ui.theme.SeismicHot.White
    val Safe: Color get() = com.gising.ui.theme.SeismicHot.Safe
    val Signal: Color get() = com.gising.ui.theme.SeismicHot.Signal
    val Grid: Color get() = com.gising.ui.theme.SeismicHot.Grid
    val Gradient: Brush get() = com.gising.ui.theme.SeismicHot.Gradient
}

private const val ALERT_NEARBY_KM = 500.0       // a severe quake this close counts as an active alert

@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onEarthquakeClick: (Earthquake) -> Unit,
    onSos: () -> Unit,
    onCheckSavedPlaces: () -> Unit,
    onOpenMap: () -> Unit,
    onOpenCyclone: () -> Unit = {},
    userName: String = "",
    userInitial: String = "?",
    userPhotoUrl: String = "",
    onOpenProfile: () -> Unit = {},
    onOpenNotifications: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()

    // Keep earthquakes + typhoons live while Home is on screen.
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(60_000)
            viewModel.refresh()
        }
    }

    // ── Derive everything from the real feeds ─────────────────────────────────
    val now = System.currentTimeMillis()
    val severeNearby = remember(state.earthquakes, viewModel.userLat, viewModel.userLon) {
        state.earthquakes
            .filter { it.magnitude >= 5.0 && now - it.timeMs <= 2L * 24 * 3600_000 }
            .filter { viewModel.distanceTo(it) <= ALERT_NEARBY_KM }
            .sortedByDescending { it.magnitude }
    }
    val activeTyphoons = state.typhoons.filter { it.isCurrent }
    val quakesToday = state.earthquakes.count { now - it.timeMs <= 24 * 3600_000 }
    val alertCount = severeNearby.size + activeTyphoons.size

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .tacticalBackground(),
        contentAlignment = Alignment.TopCenter,
    ) {
        CompositionLocalProvider(LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = SeismicFont)) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .widthIn(max = 640.dp)
                    .fillMaxWidth(),
            ) {
                // Reserve room for the sticky header.
                Spacer(Modifier.statusBarsPadding().height(72.dp))

                // Offline Mode notice — appears when signal drops; the feed below is served from cache.
                com.gising.ui.screens.components.OfflineBanner(visible = state.isOffline)

                StatRow(
                    alertCount = alertCount,
                    quakesToday = quakesToday,
                    typhoonCount = activeTyphoons.size,
                    typhoonLabel = state.nearestTyphoon?.name ?: state.nearestTyphoon?.category?.label ?: "All clear",
                    modifier = Modifier.entrance(visible, 90),
                )
                Spacer(Modifier.height(26.dp))

                // ── Active alerts ─────────────────────────────────────────────
                SectionHeader("Active Alerts", action = "View all", onAction = onOpenMap, modifier = Modifier.entrance(visible, 140))
                Spacer(Modifier.height(12.dp))
                if (severeNearby.isEmpty() && activeTyphoons.isEmpty()) {
                    AllClearCard(modifier = Modifier.entrance(visible, 170))
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        severeNearby.take(2).forEachIndexed { i, q ->
                            QuakeAlertCard(
                                quake = q,
                                distanceKm = viewModel.distanceTo(q),
                                onDetails = { onEarthquakeClick(q) },
                                onShare = onCheckSavedPlaces,
                                modifier = Modifier.entrance(visible, 170 + i * 40),
                            )
                        }
                        activeTyphoons.take(1).forEach { t ->
                            TyphoonAlertCard(
                                typhoon = t,
                                pagasa = state.pagasaCyclone,
                                onDetails = onOpenCyclone,
                                modifier = Modifier.entrance(visible, 250),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(28.dp))

                // ── Recent activity ───────────────────────────────────────────
                SectionHeader("Recent Activity", action = "Reports", onAction = onOpenMap, modifier = Modifier.entrance(visible, 380))
                Spacer(Modifier.height(12.dp))
                val activity = remember(state.earthquakes, activeTyphoons) {
                    buildActivityLog(state.earthquakes, activeTyphoons)
                }
                ActivityLog(entries = activity, onEntryClick = onEarthquakeClick, modifier = Modifier.entrance(visible, 410))

                Spacer(Modifier.height(28.dp))
            }
        }

        // Sticky header floats over the scrolling content.
        HomeHeader(
            userName = userName,
            userInitial = userInitial,
            userPhotoUrl = userPhotoUrl,
            onOpenProfile = onOpenProfile,
            modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
        )
    }
}

// ─── Background ──────────────────────────────────────────────────────────────
// Static ground (grid + ember glows) built once via drawWithCache — no per-frame scanline redraw,
// so the dashboard stays responsive to taps on older devices.
private fun Modifier.tacticalBackground(): Modifier =
    this
        .background(Hot.Base)
        .drawWithCache {
            val w = size.width; val h = size.height
            val glowTop = Brush.radialGradient(listOf(Hot.Red.copy(alpha = 0.20f), Color.Transparent), Offset(w / 2f, 0f), 320.dp.toPx())
            val glowRight = Brush.radialGradient(listOf(Hot.Orange.copy(alpha = 0.14f), Color.Transparent), Offset(w * 0.95f, h * 0.4f), 200.dp.toPx())
            val cell = 28.dp.toPx()
            onDrawBehind {
                drawRect(glowTop, size = Size(w, h))
                drawRect(glowRight, size = Size(w, h))
                var x = 0f
                while (x <= w) { drawLine(Hot.Grid, Offset(x, 0f), Offset(x, h), 1f); x += cell }
                var y = 0f
                while (y <= h) { drawLine(Hot.Grid, Offset(0f, y), Offset(w, y), 1f); y += cell }
            }
        }

// ─── Header ──────────────────────────────────────────────────────────────────
@Composable
private fun HomeHeader(
    userName: String,
    userInitial: String,
    userPhotoUrl: String,
    onOpenProfile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg = Hot.Base
    Column(
        modifier = modifier.background(
            Brush.verticalGradient(listOf(bg, bg.copy(alpha = 0.92f), bg.copy(alpha = 0f))),
        ),
    ) {
        // Neon hot-line.
        Box(
            Modifier
                .statusBarsPadding()
                .fillMaxWidth()
                .height(2.dp)
                .background(Hot.Gradient),
        )
        Row(
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .shadow(10.dp, RoundedCornerShape(11.dp), spotColor = Hot.Red, ambientColor = Hot.Red)
                        .clip(RoundedCornerShape(11.dp))
                        .background(Hot.Gradient)
                        .clickable(onClick = onOpenProfile),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = androidx.compose.ui.res.painterResource(R.drawable.ic_seismic_wave),
                        contentDescription = "SeismicWatch",
                        tint = Hot.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Column {
                    Text(greeting(), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, color = Hot.Muted)
                    Text(
                        userName.ifBlank { "Welcome" },
                        fontFamily = SeismicDisplayFont,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.4).sp,
                        color = Hot.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

// ─── Stat cards ──────────────────────────────────────────────────────────────
@Composable
private fun StatRow(
    alertCount: Int,
    quakesToday: Int,
    typhoonCount: Int,
    typhoonLabel: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StatCard(
            icon = Icons.Outlined.Warning,
            accent = Hot.Red,
            label = "ALERTS",
            value = alertCount.toString(),
            sub = if (alertCount > 0) "Active now" else "All clear",
            hot = alertCount > 0,
        )
        StatCard(
            icon = Icons.Outlined.GraphicEq,
            accent = Hot.Signal,
            label = "QUAKES",
            value = quakesToday.toString(),
            sub = "Today",
        )
        StatCard(
            icon = Icons.Outlined.Cyclone,
            accent = Hot.Safe,
            label = "SIGNAL",
            value = typhoonCount.toString(),
            sub = typhoonLabel,
        )
    }
}

@Composable
private fun StatCard(
    icon: ImageVector,
    accent: Color,
    label: String,
    value: String,
    sub: String,
    hot: Boolean = false,
) {
    Column(
        modifier = Modifier
            .widthIn(min = 112.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Hot.Card)
            .border(1.dp, if (hot) Hot.Red.copy(alpha = 0.35f) else Hot.Border, RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(10.dp))
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = Hot.Muted)
        Spacer(Modifier.height(2.dp))
        Text(value, fontFamily = SeismicDisplayFont, fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp, color = Hot.White)
        Spacer(Modifier.height(2.dp))
        Text(sub, fontSize = 11.sp, color = accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ─── Section header ──────────────────────────────────────────────────────────
@Composable
private fun SectionHeader(title: String, action: String, onAction: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, fontFamily = SeismicDisplayFont, fontSize = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp, color = Hot.White)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onAction).padding(horizontal = 4.dp, vertical = 2.dp),
        ) {
            Text(action, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Hot.Red)
            Icon(Icons.Outlined.ArrowForward, null, tint = Hot.Red, modifier = Modifier.size(13.dp))
        }
    }
}

// ─── Alert cards ─────────────────────────────────────────────────────────────
@Composable
private fun QuakeAlertCard(
    quake: Earthquake,
    distanceKm: Double?,
    onDetails: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val high = quake.magnitude >= 6.0 || quake.tsunami == 1
    val accent = if (high) Hot.Red else Hot.Orange
    Column(
        modifier = modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.14f), accent.copy(alpha = 0.05f))))
            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(46.dp).clip(RoundedCornerShape(13.dp)).background(accent.copy(alpha = 0.18f)).border(1.dp, accent.copy(alpha = 0.4f), RoundedCornerShape(13.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.GraphicEq, null, tint = accent, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SeverityChip(if (high) "HIGH" else "MEDIUM", accent)
                    Text(formatRelativeTime(quake.timeMs), fontSize = 10.5.sp, color = Hot.Muted)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Earthquake Mag. ${String.format("%.1f", quake.magnitude)}",
                    fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Hot.White,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(quake.place, fontSize = 12.5.sp, color = Hot.Label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            MetaChip(Icons.Outlined.Place, "${String.format("%.1f", quake.latitude)}°, ${String.format("%.1f", quake.longitude)}°")
            MetaChip(Icons.Outlined.Layers, "Depth: ${String.format("%.0f", quake.depth)} km")
            if (distanceKm != null) MetaChip(Icons.Outlined.NearMe, "${String.format("%.0f", distanceKm)} km")
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HotCtaButton("Full Details", Icons.Outlined.Bolt, onDetails, Modifier.weight(1f))
            Box(
                Modifier.size(46.dp).clip(RoundedCornerShape(13.dp)).background(Hot.Base.copy(alpha = 0.4f)).border(1.dp, Hot.Border, RoundedCornerShape(13.dp)).clickable(onClick = onShare),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.Share, "Share", tint = Hot.White, modifier = Modifier.size(18.dp)) }
        }
    }
}

@Composable
private fun TyphoonAlertCard(
    typhoon: Typhoon,
    pagasa: PagasaCyclone?,
    onDetails: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = Hot.Orange
    Column(
        modifier = modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.14f), accent.copy(alpha = 0.05f))))
            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(46.dp).clip(RoundedCornerShape(13.dp)).background(accent.copy(alpha = 0.18f)).border(1.dp, accent.copy(alpha = 0.4f), RoundedCornerShape(13.dp)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.Cyclone, null, tint = accent, modifier = Modifier.size(22.dp)) }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SeverityChip("MEDIUM", accent)
                    if (typhoon.fromMs > 0) Text(formatRelativeTime(typhoon.fromMs), fontSize = 10.5.sp, color = Hot.Muted)
                }
                Spacer(Modifier.height(6.dp))
                val signal = pagasa?.highestSignal
                Text(
                    if (signal != null) "Typhoon Signal #$signal" else "Typhoon ${typhoon.name}",
                    fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Hot.White,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(typhoon.category.label, fontSize = 12.5.sp, color = Hot.Label)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            typhoon.windKph?.let { MetaChip(Icons.Outlined.Air, "${it.roundToInt()} km/h winds") }
            MetaChip(Icons.Outlined.NearMe, typhoon.alertLevel.label)
        }
        Spacer(Modifier.height(14.dp))
        HotCtaButton("Track Storm", Icons.Outlined.Map, onDetails, Modifier.fillMaxWidth())
    }
}

@Composable
private fun AllClearCard(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Hot.Card)
            .border(1.dp, Hot.Safe.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier.size(46.dp).clip(RoundedCornerShape(13.dp)).background(Hot.Safe.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Check, null, tint = Hot.Safe, modifier = Modifier.size(24.dp)) }
        Column {
            Text("No active threats", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Hot.White)
            Text("All monitored regions are clear", fontSize = 12.5.sp, color = Hot.Muted)
        }
    }
}

// ─── Activity log ────────────────────────────────────────────────────────────
@Composable
private fun ActivityLog(entries: List<ActivityEntry>, onEntryClick: (Earthquake) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Hot.Card)
            .border(1.dp, Hot.Border, RoundedCornerShape(16.dp))
            .padding(vertical = 6.dp),
    ) {
        if (entries.isEmpty()) {
            Text("No recent activity", fontSize = 12.5.sp, color = Hot.Muted, modifier = Modifier.padding(16.dp))
        }
        entries.forEachIndexed { i, e ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (e.quake != null) Modifier.clickable { onEntryClick(e.quake) } else Modifier)
                    .height(IntrinsicSize.Min)
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(3.dp).fillMaxHeight().padding(vertical = 8.dp).background(e.severity))
                Box(
                    Modifier.padding(start = 12.dp).size(34.dp).clip(RoundedCornerShape(10.dp)).background(e.severity.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) { Icon(e.icon, null, tint = e.severity.takeIf { it != Hot.Border } ?: Hot.Label, modifier = Modifier.size(17.dp)) }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 12.dp)) {
                    Text(e.title, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Hot.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(e.location, fontSize = 11.sp, color = Hot.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(formatRelativeTime(e.timeMs), fontSize = 10.5.sp, color = Hot.Muted, modifier = Modifier.padding(end = 14.dp))
            }
            if (i < entries.lastIndex) HorizontalDivider(color = Hot.Border.copy(alpha = 0.5f), modifier = Modifier.padding(start = 15.dp))
        }
    }
}

// ─── Small building blocks ───────────────────────────────────────────────────
@Composable
private fun SeverityChip(text: String, accent: Color) {
    Box(Modifier.clip(CircleShape).background(accent.copy(alpha = 0.18f)).border(1.dp, accent.copy(alpha = 0.4f), CircleShape).padding(horizontal = 8.dp, vertical = 2.dp)) {
        Text(text, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, color = accent)
    }
}

@Composable
private fun MetaChip(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, null, tint = Hot.Muted, modifier = Modifier.size(13.dp))
        Text(text, fontSize = 11.sp, color = Hot.Label)
    }
}

@Composable
private fun HotCtaButton(text: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, label = "ctaScale")
    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .height(46.dp)
            .shadow(20.dp, RoundedCornerShape(13.dp), spotColor = Hot.Red, ambientColor = Hot.Orange)
            .clip(RoundedCornerShape(13.dp))
            .background(Hot.Gradient)
            .border(BorderStroke(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.18f), Color.Transparent))), RoundedCornerShape(13.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, tint = Hot.White, modifier = Modifier.size(18.dp))
            Text(text, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.3.sp, color = Hot.White)
        }
    }
}

@Composable
private fun BlinkingDot(color: Color, size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "blink")
    val a by transition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "blinkAlpha",
    )
    Box(modifier.size(size).graphicsLayer { alpha = a }.clip(CircleShape).background(color))
}

// ─── Data derivation ─────────────────────────────────────────────────────────
private data class ActivityEntry(
    val icon: ImageVector,
    val title: String,
    val location: String,
    val timeMs: Long,
    val severity: Color,
    val quake: Earthquake? = null,
)

private fun buildActivityLog(quakes: List<Earthquake>, typhoons: List<Typhoon>): List<ActivityEntry> {
    val quakeEntries = quakes.sortedByDescending { it.timeMs }.take(4).map { q ->
        ActivityEntry(
            icon = Icons.Outlined.GraphicEq,
            title = "Earthquake Mag. ${String.format("%.1f", q.magnitude)} detected",
            location = q.place,
            timeMs = q.timeMs,
            severity = when {
                q.magnitude >= 6.0 -> Hot.Red
                q.magnitude >= 4.5 -> Hot.Orange
                else -> Hot.Border
            },
            quake = q,
        )
    }
    val typhoonEntries = typhoons.filter { it.fromMs > 0 }.map { t ->
        ActivityEntry(
            icon = Icons.Outlined.Cyclone,
            title = "Typhoon ${t.name} — ${t.category.label}",
            location = t.alertLevel.label,
            timeMs = t.fromMs,
            severity = Hot.Orange,
        )
    }
    // Keep the home feed short and scannable — full history lives in Reports.
    return (quakeEntries + typhoonEntries).sortedByDescending { it.timeMs }.take(4)
}

private fun greeting(): String {
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    return when (hour) {
        in 5..11 -> "GOOD MORNING,"
        in 12..17 -> "GOOD AFTERNOON,"
        else -> "GOOD EVENING,"
    }
}

// ─── Staggered entrance ──────────────────────────────────────────────────────
@Composable
private fun Modifier.entrance(visible: Boolean, delayMillis: Int): Modifier {
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(420, delayMillis = delayMillis),
        label = "entranceAlpha",
    )
    val translateY by animateFloatAsState(
        targetValue = if (visible) 0f else 46f,
        animationSpec = tween(480, delayMillis = delayMillis, easing = FastOutSlowInEasing),
        label = "entranceY",
    )
    return this.graphicsLayer { this.alpha = alpha; this.translationY = translateY }
}

// ══════════════════════════════════════════════════════════════════════════════
// Shared, public helpers used by Detail / Reports / LiveMap screens — preserved.
// ══════════════════════════════════════════════════════════════════════════════

@Composable
fun EarthquakeListItem(
    earthquake: Earthquake,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    distanceKm: Double? = null,
) {
    val tier = MagnitudeTier.from(earthquake.magnitude)
    val tierColor = magnitudeTierColor(tier)

    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(tierColor.copy(alpha = 0.22f))
                    .border(1.dp, tierColor.copy(alpha = 0.5f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "M",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = tierColor.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 2.dp),
                    )
                    Text(
                        String.format("%.1f", earthquake.magnitude),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontFeatureSettings = "tnum",
                        ),
                        color = tierColor,
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    earthquake.place.substringAfterLast(" of ").ifBlank { earthquake.place },
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    buildString {
                        append(formatRelativeTime(earthquake.timeMs))
                        if (distanceKm != null) append(" · ${String.format("%.0f", distanceKm)} km away")
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(modifier = Modifier.width(86.dp), contentAlignment = Alignment.CenterEnd) {
                FeltTag(magnitude = earthquake.magnitude)
            }
            Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
fun FeltTag(magnitude: Double) {
    val (label, color) = feltLabel(magnitude)
    Surface(
        color = color.copy(alpha = 0.16f),
        shape = RoundedCornerShape(8.dp),
    ) {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.6.sp),
            color = color,
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

fun feltLabel(magnitude: Double): Pair<String, Color> = when {
    magnitude >= 6.0 -> "Strongly Felt" to SeismicColors.MagnitudeMajor
    magnitude >= 5.0 -> "Felt" to SeismicColors.MagnitudeModerate
    magnitude >= 4.0 -> "Lightly Felt" to SeismicColors.MagnitudeLight
    else -> "Barely Felt" to SeismicColors.MagnitudeMinor
}

// ─── Utilities ──────────────────────────────────────────────────────────────────
fun weatherPowerColor(power: WeatherPower): Color = when (power) {
    WeatherPower.CALM, WeatherPower.BREEZY -> SeismicColors.Verdant
    WeatherPower.WINDY -> SeismicColors.MagnitudeLight
    WeatherPower.BLUSTERY -> SeismicColors.MagnitudeModerate
    WeatherPower.STORMY -> SeismicColors.MagnitudeStrong
    WeatherPower.SEVERE -> SeismicColors.MagnitudeMajor
}

fun aqiColor(category: AqiCategory): Color = when (category) {
    AqiCategory.GOOD -> SeismicColors.Verdant
    AqiCategory.MODERATE -> SeismicColors.MagnitudeLight
    AqiCategory.UNHEALTHY_SENSITIVE -> SeismicColors.MagnitudeModerate
    AqiCategory.UNHEALTHY -> SeismicColors.MagnitudeStrong
    AqiCategory.VERY_UNHEALTHY -> SeismicColors.MagnitudeMajor
    AqiCategory.HAZARDOUS -> SeismicColors.MagnitudeGreat
}

fun heatColor(category: HeatCategory): Color = when (category) {
    HeatCategory.NORMAL -> SeismicColors.Verdant
    HeatCategory.CAUTION -> SeismicColors.MagnitudeLight
    HeatCategory.EXTREME_CAUTION -> SeismicColors.MagnitudeModerate
    HeatCategory.DANGER -> SeismicColors.MagnitudeStrong
    HeatCategory.EXTREME_DANGER -> SeismicColors.MagnitudeMajor
}

fun typhoonColor(alert: TyphoonAlert): Color = when (alert) {
    TyphoonAlert.GREEN -> SeismicColors.MagnitudeLight
    TyphoonAlert.ORANGE -> SeismicColors.MagnitudeStrong
    TyphoonAlert.RED -> SeismicColors.MagnitudeMajor
}

fun magnitudeTierColor(tier: MagnitudeTier): Color = when (tier) {
    MagnitudeTier.MICRO, MagnitudeTier.MINOR -> SeismicColors.MagnitudeMinor
    MagnitudeTier.LIGHT -> SeismicColors.MagnitudeLight
    MagnitudeTier.MODERATE -> SeismicColors.MagnitudeModerate
    MagnitudeTier.STRONG -> SeismicColors.MagnitudeStrong
    MagnitudeTier.MAJOR -> SeismicColors.MagnitudeMajor
    MagnitudeTier.GREAT -> SeismicColors.MagnitudeGreat
}

fun formatRelativeTime(epochMs: Long): String {
    val diffMs = System.currentTimeMillis() - epochMs
    val diffMin = diffMs / 60_000
    return when {
        diffMin < 1 -> "just now"
        diffMin < 60 -> "${diffMin}m ago"
        diffMin < 1440 -> "${diffMin / 60}h ago"
        else -> "${diffMin / 1440}d ago"
    }
}
