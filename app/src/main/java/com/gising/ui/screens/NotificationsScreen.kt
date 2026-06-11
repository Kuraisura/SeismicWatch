package com.gising.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gising.data.model.Earthquake
import com.gising.ui.theme.SeismicBlinkingDot
import com.gising.ui.theme.SeismicDisplayFont
import com.gising.ui.theme.SeismicFont
import com.gising.ui.theme.SeismicHot
import com.gising.ui.theme.seismicEntrance
import com.gising.ui.theme.seismicTacticalBackground

/**
 * Notifications — the alert inbox behind the header bell. Surfaces the same live PHIVOLCS / GDACS
 * hazards as Reports, but framed as a chronological notification feed (Unread → Earlier) with a
 * read/clear affordance. Tapping a seismic notification drills into the earthquake detail.
 */
@Composable
fun NotificationsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onEarthquakeClick: (Earthquake) -> Unit,
) {
    val state by viewModel.uiState.collectAsState()

    val alerts = remember(state.earthquakes, state.typhoons, viewModel.userLat, viewModel.userLon) {
        buildAlerts(
            quakes = state.earthquakes,
            typhoons = state.typhoons,
            distanceToQuake = { viewModel.distanceTo(it) },
        )
    }

    // Treat anything from the last 6h as "unread/new".
    val now = System.currentTimeMillis()
    val unread = alerts.filter { now - it.timeMs <= 6L * 3600_000 }
    val earlier = alerts.filter { now - it.timeMs > 6L * 3600_000 }

    // Locally clearable set, so the inbox feels responsive without a backing store.
    val dismissed = remember { mutableStateListOf<String>() }
    val visibleUnread = unread.filter { it.id !in dismissed }
    val visibleEarlier = earlier.filter { it.id !in dismissed }

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    Box(
        modifier = Modifier.fillMaxSize().seismicTacticalBackground(),
        contentAlignment = Alignment.TopCenter,
    ) {
        CompositionLocalProvider(LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = SeismicFont)) {
            Column(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .statusBarsPadding(),
            ) {
                Spacer(Modifier.height(12.dp))
                Header(
                    unreadCount = visibleUnread.size,
                    onBack = onBack,
                    onClearAll = { dismissed.addAll(alerts.map { it.id }) },
                    modifier = Modifier.seismicEntrance(visible, 0),
                )
                Spacer(Modifier.height(18.dp))

                if (visibleUnread.isEmpty() && visibleEarlier.isEmpty()) {
                    EmptyInbox(modifier = Modifier.seismicEntrance(visible, 60))
                }

                if (visibleUnread.isNotEmpty()) {
                    GroupLabel("NEW", SeismicHot.Red, modifier = Modifier.seismicEntrance(visible, 60))
                    Spacer(Modifier.height(10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        visibleUnread.forEachIndexed { i, a ->
                            NotificationRow(
                                item = a,
                                unread = true,
                                onClick = { a.quake?.let(onEarthquakeClick) },
                                onDismiss = { dismissed.add(a.id) },
                                modifier = Modifier.seismicEntrance(visible, 90 + i * 30),
                            )
                        }
                    }
                    Spacer(Modifier.height(22.dp))
                }

                if (visibleEarlier.isNotEmpty()) {
                    GroupLabel("EARLIER", SeismicHot.Muted, modifier = Modifier.seismicEntrance(visible, 120))
                    Spacer(Modifier.height(10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        visibleEarlier.forEachIndexed { i, a ->
                            NotificationRow(
                                item = a,
                                unread = false,
                                onClick = { a.quake?.let(onEarthquakeClick) },
                                onDismiss = { dismissed.add(a.id) },
                                modifier = Modifier.seismicEntrance(visible, 150 + i * 30),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(110.dp))
            }
        }
    }
}

@Composable
private fun Header(unreadCount: Int, onBack: () -> Unit, onClearAll: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SeismicHot.Card)
                    .border(1.dp, SeismicHot.Border, RoundedCornerShape(12.dp))
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.ArrowBack, "Back", tint = SeismicHot.White, modifier = Modifier.size(20.dp)) }
            Column {
                Text("Notifications", fontFamily = SeismicDisplayFont, fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.8).sp, color = SeismicHot.White)
                Text(
                    if (unreadCount > 0) "$unreadCount new alert${if (unreadCount == 1) "" else "s"}" else "You're all caught up",
                    fontSize = 12.sp,
                    color = SeismicHot.Muted,
                )
            }
        }
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(SeismicHot.Red.copy(alpha = 0.08f))
                .border(1.dp, SeismicHot.Red.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                .clickable(onClick = onClearAll)
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(Icons.Outlined.DoneAll, null, tint = SeismicHot.Label, modifier = Modifier.size(14.dp))
            Text("Clear", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SeismicHot.Label)
        }
    }
}

@Composable
private fun GroupLabel(text: String, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (color == SeismicHot.Red) SeismicBlinkingDot(color, 7.dp)
        Text(text, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = color)
    }
}

@Composable
private fun NotificationRow(
    item: AlertItem,
    unread: Boolean,
    onClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = item.severity.color
    Row(
        modifier = modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .then(
                if (unread) Modifier.background(Brush.linearGradient(listOf(accent.copy(alpha = 0.12f), accent.copy(alpha = 0.04f))))
                else Modifier.background(SeismicHot.Card)
            )
            .border(1.dp, if (unread) accent.copy(alpha = 0.30f) else SeismicHot.Border, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(accent.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) { Icon(item.kind.icon, null, tint = accent, modifier = Modifier.size(20.dp)) }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    item.title,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = SeismicHot.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (unread) SeismicBlinkingDot(accent, 6.dp)
            }
            Text(
                "${item.subtitle} · ${item.summary}",
                fontSize = 11.5.sp,
                color = SeismicHot.Label,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Text(formatRelativeTime(item.timeMs), fontSize = 10.5.sp, color = SeismicHot.Muted)
        }
        // Severity value chip.
        Box(
            Modifier.clip(CircleShape).background(accent.copy(alpha = 0.16f)).padding(horizontal = 9.dp, vertical = 4.dp),
        ) {
            Text(item.value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = accent)
        }
    }
}

@Composable
private fun EmptyInbox(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(64.dp).clip(RoundedCornerShape(20.dp)).background(SeismicHot.Card).border(1.dp, SeismicHot.Border, RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Outlined.NotificationsNone, null, tint = SeismicHot.Muted, modifier = Modifier.size(30.dp)) }
        Spacer(Modifier.height(16.dp))
        Text("No notifications", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = SeismicHot.White)
        Spacer(Modifier.height(4.dp))
        Text("Hazard alerts for your region will appear here.", fontSize = 12.5.sp, color = SeismicHot.Muted)
    }
}
