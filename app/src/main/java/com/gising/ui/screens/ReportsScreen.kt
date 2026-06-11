package com.gising.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Cyclone
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Storm
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.Terrain
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.gising.data.PhilippineWeatherPoints
import com.gising.data.model.DayPoint
import com.gising.data.model.Earthquake
import com.gising.data.model.HourPoint
import com.gising.data.model.LiveWeather
import com.gising.data.model.Typhoon
import com.gising.data.model.TyphoonAlert
import com.gising.data.model.TyphoonCategory
import com.gising.data.model.WeatherDetail
import com.gising.data.model.WeatherPower
import com.gising.data.model.wmoLabel
import com.gising.data.repository.WeatherRepository
import com.gising.ui.screens.components.AnimatedSegmentedControl
import com.gising.ui.screens.components.MapLibreCanvas
import com.gising.ui.screens.components.MapPin
import com.gising.ui.screens.components.RadarPulse
import com.gising.ui.screens.components.SegmentTab
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import com.gising.ui.theme.SeismicBlinkingDot
import com.gising.ui.theme.SeismicDisplayFont
import com.gising.ui.theme.SeismicFont
import com.gising.ui.theme.SeismicHot
import com.gising.ui.theme.seismicEntrance
import com.gising.ui.theme.seismicTacticalBackground
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Reports — a two-feed monitoring hub, toggled between Earthquakes and Typhoons.
 *
 * Both feeds run on the same live data (PHIVOLCS/Supabase quakes; GDACS/PAGASA cyclones) and share
 * the tactical look. Earthquakes is a severity-grouped quake list; Typhoons is a PAGASA-style storm
 * monitor split into Active Storms (inside PAR) and Outside-PAR monitoring. Tapping a storm opens
 * the dedicated Typhoon tracker map; tapping a quake opens its detail.
 */
@Composable
fun ReportsScreen(
    viewModel: MainViewModel,
    onEarthquakeClick: (Earthquake) -> Unit,
    onOpenCyclone: () -> Unit = {},
    onOpenNotifications: () -> Unit = {},
) {
    var tab by remember { mutableStateOf(0) } // 0 = Earthquakes, 1 = Typhoons

    Box(
        modifier = Modifier.fillMaxSize().seismicTacticalBackground(),
        contentAlignment = Alignment.TopCenter,
    ) {
        CompositionLocalProvider(LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = SeismicFont)) {
            Column(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            ) {
                // Reserve room for the floating tab bar that hovers over the scrolling feed.
                Spacer(Modifier.statusBarsPadding().height(78.dp))

                // Offline Mode notice — the feeds below fall back to saved reports when signal drops.
                val reportsState by viewModel.uiState.collectAsState()
                com.gising.ui.screens.components.OfflineBanner(visible = reportsState.isOffline)

                if (tab == 0) EarthquakesFeed(viewModel, onEarthquakeClick)
                else TyphoonsFeed(viewModel, onOpenCyclone)

                Spacer(Modifier.height(110.dp)) // clearance for the bottom nav
            }
        }

        // Floating tab bar — mirrors the Home "Good Morning" header: a status-bar-aware overlay with a
        // neon hot-line and a vertical gradient fade, so the feed scrolls beneath it instead of pushing it.
        ReportsTabBar(
            tab = tab,
            onSelect = { tab = it },
            modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
        )
    }
}

@Composable
private fun ReportsTabBar(tab: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val bg = SeismicHot.Base
    Column(
        modifier = modifier.background(
            Brush.verticalGradient(listOf(bg, bg.copy(alpha = 0.92f), bg.copy(alpha = 0f))),
        ),
    ) {
        // Neon hot-line under the status bar.
        Box(Modifier.statusBarsPadding().fillMaxWidth().height(2.dp).background(SeismicHot.Gradient))
        Spacer(Modifier.height(12.dp))
        AnimatedSegmentedControl(
            tabs = listOf(
                SegmentTab("Earthquakes", Icons.Outlined.GraphicEq),
                SegmentTab("Typhoons", Icons.Outlined.Cyclone),
            ),
            selected = tab,
            onSelect = onSelect,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(14.dp))
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  EARTHQUAKES FEED
// ══════════════════════════════════════════════════════════════════════════════

private enum class Sev(val label: String, val color: Color) {
    CRITICAL("Critical", SeismicHot.Red),
    HIGH("High", SeismicHot.Orange),
    MODERATE("Moderate", SeismicHot.Yellow),
    LOW("Low", Color(0xFF8A8D93)),
}

private fun sevOf(eq: Earthquake): Sev = when {
    eq.magnitude >= 6.0 || eq.tsunami == 1 -> Sev.CRITICAL
    eq.magnitude >= 5.0 -> Sev.HIGH
    eq.magnitude >= 4.0 -> Sev.MODERATE
    else -> Sev.LOW
}

private enum class SevFilter(val label: String) { ALL("All"), CRITICAL("Critical"), HIGH("High"), MODERATE("Moderate") }

@Composable
private fun EarthquakesFeed(viewModel: MainViewModel, onEarthquakeClick: (Earthquake) -> Unit) {
    val state by viewModel.uiState.collectAsState()
    val now = System.currentTimeMillis()

    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(SevFilter.ALL) }
    var newestFirst by remember { mutableStateOf(true) }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    // Today's window (with a fallback so the screen is never empty on a quiet/stale feed).
    val window = remember(state.earthquakes) {
        val t = state.earthquakes.filter { now - it.timeMs <= 24 * 3600_000 }
        if (t.size >= 6) t else state.earthquakes.take(80)
    }
    val counts = remember(window) {
        Counts(
            critical = window.count { sevOf(it) == Sev.CRITICAL },
            high = window.count { sevOf(it) == Sev.HIGH },
            moderate = window.count { sevOf(it) == Sev.MODERATE || sevOf(it) == Sev.LOW },
            total = window.size,
        )
    }
    val matched = remember(window, query, filter) {
        window.filter { q ->
            (query.isBlank() || q.place.contains(query, true) || "%.1f".format(q.magnitude).contains(query)) &&
                when (filter) {
                    SevFilter.ALL -> true
                    SevFilter.CRITICAL -> sevOf(q) == Sev.CRITICAL
                    SevFilter.HIGH -> sevOf(q) == Sev.HIGH
                    SevFilter.MODERATE -> sevOf(q) == Sev.MODERATE || sevOf(q) == Sev.LOW
                }
        }
    }
    val comparator = remember(newestFirst) {
        if (newestFirst) compareByDescending<Earthquake> { it.timeMs } else compareByDescending { it.magnitude }
    }
    val critical = remember(matched, comparator) { matched.filter { sevOf(it) == Sev.CRITICAL }.sortedWith(comparator) }
    val high = remember(matched, comparator) { matched.filter { sevOf(it) == Sev.HIGH }.sortedWith(comparator) }
    val moderate = remember(matched, comparator) {
        matched.filter { sevOf(it) == Sev.MODERATE || sevOf(it) == Sev.LOW }.sortedWith(comparator)
    }
    var shownModerate by remember(filter, query, newestFirst) { mutableStateOf(8) }

    Column(Modifier.fillMaxWidth()) {
        EqHeader(todayCount = counts.total, modifier = Modifier.seismicEntrance(visible, 0))
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.seismicEntrance(visible, 60).padding(horizontal = 20.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SummaryCard("CRITICAL", counts.critical.toString(), SeismicHot.Red, Modifier.weight(1f))
            SummaryCard("HIGH", counts.high.toString(), SeismicHot.Orange, Modifier.weight(1f))
            SummaryCard("MODERATE", counts.moderate.toString(), SeismicHot.Yellow, Modifier.weight(1f))
            SummaryCard("TODAY", counts.total.toString(), SeismicHot.White, Modifier.weight(1f))
        }
        Spacer(Modifier.height(16.dp))
        SearchField(query, { query = it }, "Search location, magnitude...", Modifier.seismicEntrance(visible, 100))
        Spacer(Modifier.height(14.dp))
        EqFilterChips(filter, { filter = it }, Modifier.seismicEntrance(visible, 140))
        Spacer(Modifier.height(20.dp))
        LiveUpdatesBar(newestFirst, { newestFirst = !newestFirst }, Modifier.seismicEntrance(visible, 180))
        Spacer(Modifier.height(12.dp))

        if (matched.isEmpty()) {
            EmptyState(Icons.Outlined.GraphicEq, "No matching earthquakes", "Try a different filter or search term.", Modifier.seismicEntrance(visible, 220))
        }

        SeverityGroup("CRITICAL ALERTS", Sev.CRITICAL.color, critical, visible, 200, onEarthquakeClick)
        SeverityGroup("HIGH SEVERITY", Sev.HIGH.color, high, visible, 240, onEarthquakeClick)

        if (moderate.isNotEmpty()) {
            GroupHeader("MODERATE", Sev.MODERATE.color, moderate.size, Modifier.seismicEntrance(visible, 280))
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                moderate.take(shownModerate).forEach { q -> QuakeCard(q) { onEarthquakeClick(q) } }
            }
            if (shownModerate < moderate.size) {
                Spacer(Modifier.height(14.dp))
                LoadMoreButton(moderate.size - shownModerate) { shownModerate = (shownModerate + 20).coerceAtMost(moderate.size) }
            }
        }

    }
}

private data class Counts(val critical: Int, val high: Int, val moderate: Int, val total: Int)

@Composable
private fun EqHeader(todayCount: Int, modifier: Modifier = Modifier) {
    FeedHeader(
        title = "Earthquakes",
        pillText = "$todayCount TODAY",
        accent = SeismicHot.Orange,
        icon = Icons.Outlined.GraphicEq,
        modifier = modifier,
    )
}

@Composable
private fun EqFilterChips(selected: SevFilter, onSelect: (SevFilter) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SevFilter.entries.forEach { f ->
            val accent = when (f) {
                SevFilter.ALL -> SeismicHot.Red
                SevFilter.CRITICAL -> Sev.CRITICAL.color
                SevFilter.HIGH -> Sev.HIGH.color
                SevFilter.MODERATE -> Sev.MODERATE.color
            }
            Chip(label = f.label, accent = accent, active = f == selected, isAll = f == SevFilter.ALL) { onSelect(f) }
        }
    }
}

@Composable
private fun LiveUpdatesBar(newestFirst: Boolean, onToggleSort: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SeismicBlinkingDot(SeismicHot.Red, 7.dp)
            Text("LIVE UPDATES", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = SeismicHot.Red)
        }
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(9.dp)).background(SeismicHot.Field)
                .border(1.dp, SeismicHot.Border, RoundedCornerShape(9.dp))
                .clickable(onClick = onToggleSort).padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(Icons.Outlined.SwapVert, null, tint = SeismicHot.Label, modifier = Modifier.size(14.dp))
            Text(if (newestFirst) "Newest first" else "Strongest first", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = SeismicHot.Label)
        }
    }
}

@Composable
private fun SeverityGroup(
    title: String, color: Color, items: List<Earthquake>, visible: Boolean, baseDelay: Int,
    onEarthquakeClick: (Earthquake) -> Unit,
) {
    if (items.isEmpty()) return
    GroupHeader(title, color, items.size, Modifier.seismicEntrance(visible, baseDelay))
    Spacer(Modifier.height(8.dp))
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items.forEach { q -> QuakeCard(q) { onEarthquakeClick(q) } }
    }
    Spacer(Modifier.height(22.dp))
}

@Composable
private fun GroupHeader(title: String, color: Color, count: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SeismicBlinkingDot(color, 8.dp)
            Text(title, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = color)
        }
        Text("$count event${if (count == 1) "" else "s"}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color.copy(alpha = 0.85f))
    }
}

@Composable
private fun QuakeCard(eq: Earthquake, onClick: () -> Unit) {
    val sev = sevOf(eq)
    val accent = sev.color
    val (title, subtitle) = remember(eq.place) { parseQuakePlace(eq.place) }

    Column(
        modifier = Modifier
            .padding(horizontal = 20.dp).fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.13f), accent.copy(alpha = 0.04f))))
            .border(1.dp, accent.copy(alpha = 0.30f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick).padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                Modifier.size(54.dp).clip(RoundedCornerShape(15.dp)).background(accent.copy(alpha = 0.16f))
                    .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(15.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("%.1f".format(eq.magnitude), fontFamily = SeismicDisplayFont, fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp, color = accent)
                    Text("MAG", fontSize = 7.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = accent.copy(alpha = 0.7f))
                }
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Badge(sev.label, sev.color)
                    Spacer(Modifier.weight(1f))
                    Text(formatRelativeTime(eq.timeMs), fontSize = 10.5.sp, color = SeismicHot.Muted)
                    if (sev == Sev.CRITICAL) {
                        Spacer(Modifier.width(7.dp)); SeismicBlinkingDot(SeismicHot.Red, 7.dp)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = SeismicHot.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, fontSize = 11.5.sp, color = SeismicHot.Label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetaBadge(Icons.Outlined.Layers, "${eq.depth.toInt()}km depth")
            MetaBadge(Icons.Outlined.Terrain, quakeOrigin(eq))
            Spacer(Modifier.weight(1f))
            Text("Details ›", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = accent)
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  TYPHOONS FEED
// ══════════════════════════════════════════════════════════════════════════════

/** A coarse PAGASA-style signal number derived from the cyclone category. */
private fun signalOf(t: Typhoon): Int = when (t.category) {
    TyphoonCategory.SUPER_TYPHOON -> 4
    TyphoonCategory.TYPHOON -> 3
    TyphoonCategory.SEVERE_TROPICAL_STORM -> 2
    else -> 1
}

private fun catColor(c: TyphoonCategory): Color = when (c) {
    TyphoonCategory.SUPER_TYPHOON -> SeismicHot.Red
    TyphoonCategory.TYPHOON -> Color(0xFFFF4D2D)
    TyphoonCategory.SEVERE_TROPICAL_STORM -> SeismicHot.Orange
    TyphoonCategory.TROPICAL_STORM -> SeismicHot.Yellow
    TyphoonCategory.TROPICAL_DEPRESSION -> Color(0xFF8A8D93)
}

private enum class TyFilter(val label: String, val match: (TyphoonCategory) -> Boolean) {
    ALL("All", { true }),
    SUPER("Super Typhoon", { it == TyphoonCategory.SUPER_TYPHOON }),
    TYPHOON("Typhoon", { it == TyphoonCategory.TYPHOON }),
    SEVERE("Severe TS", { it == TyphoonCategory.SEVERE_TROPICAL_STORM }),
    STORM("Tropical Storm", { it == TyphoonCategory.TROPICAL_STORM }),
}

@Composable
private fun TyphoonsFeed(viewModel: MainViewModel, onOpenCyclone: () -> Unit) {
    val state by viewModel.uiState.collectAsState()

    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(TyFilter.ALL) }
    var bySeverity by remember { mutableStateOf(true) }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    val current = remember(state.typhoons) { state.typhoons.filter { it.isCurrent } }
    val matched = remember(current, query, filter) {
        current.filter {
            (query.isBlank() || it.name.contains(query, true)) && filter.match(it.category)
        }
    }
    val cmp = remember(bySeverity) {
        if (bySeverity) compareByDescending<Typhoon> { it.windKph ?: 0.0 }
        else compareBy { viewModel.distanceTo(it) }
    }
    val active = remember(matched, cmp) { matched.filter { insidePar(it) }.sortedWith(cmp) }
    val monitoring = remember(matched, cmp) { matched.filter { !insidePar(it) }.sortedWith(cmp) }

    val s4 = active.count { signalOf(it) >= 4 }
    val s3 = active.count { signalOf(it) == 3 }
    val s12 = active.count { signalOf(it) <= 2 }

    Column(Modifier.fillMaxWidth()) {
        FeedHeader(
            title = "Typhoons",
            pillText = "${active.size} ACTIVE",
            accent = SeismicHot.Signal,
            icon = Icons.Outlined.Storm,
            modifier = Modifier.seismicEntrance(visible, 0),
        )
        Spacer(Modifier.height(14.dp))
        SearchField(query, { query = it }, "Search storm name, location...", Modifier.seismicEntrance(visible, 80))
        Spacer(Modifier.height(14.dp))
        TyFilterChips(filter, { filter = it }, Modifier.seismicEntrance(visible, 120))
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.seismicEntrance(visible, 160).padding(horizontal = 20.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SummaryCard("SIGNAL 4", s4.toString(), SeismicHot.Red, Modifier.weight(1f))
            SummaryCard("SIGNAL 3", s3.toString(), Color(0xFFFF4D2D), Modifier.weight(1f))
            SummaryCard("SIGNAL 1–2", s12.toString(), SeismicHot.Yellow, Modifier.weight(1f))
            SummaryCard("TODAY", active.size.toString(), SeismicHot.White, Modifier.weight(1f))
        }
        Spacer(Modifier.height(22.dp))

        WeatherSection(viewModel, Modifier.seismicEntrance(visible, 180))
        Spacer(Modifier.height(22.dp))

        if (matched.isEmpty()) {
            EmptyState(Icons.Outlined.Cyclone, "No active cyclones", "All clear across the Philippine area of responsibility.", Modifier.seismicEntrance(visible, 200))
        }

        if (active.isNotEmpty()) {
            Row(
                modifier = Modifier.seismicEntrance(visible, 200).fillMaxWidth().padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SeismicBlinkingDot(SeismicHot.Red, 7.dp)
                    Text("ACTIVE STORMS", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = SeismicHot.Red)
                }
                Row(
                    modifier = Modifier.clickable { bySeverity = !bySeverity },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text("Sort by: ${if (bySeverity) "Severity" else "Distance"} ↓", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SeismicHot.Orange)
                }
            }
            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                active.forEachIndexed { i, t ->
                    StormCard(t, distanceKm = viewModel.distanceTo(t), modifier = Modifier.seismicEntrance(visible, 230 + i * 30), onClick = onOpenCyclone)
                }
            }
            Spacer(Modifier.height(22.dp))
        }

        if (monitoring.isNotEmpty()) {
            Row(
                modifier = Modifier.seismicEntrance(visible, 260).fillMaxWidth().padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Outlined.NearMe, null, tint = SeismicHot.Muted, modifier = Modifier.size(14.dp))
                Text("OUTSIDE PAR — MONITORING", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = SeismicHot.Muted)
            }
            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                monitoring.forEachIndexed { i, t ->
                    MonitorCard(t, modifier = Modifier.seismicEntrance(visible, 290 + i * 30), onClick = onOpenCyclone)
                }
            }
            Spacer(Modifier.height(22.dp))
        }
    }
}

@Composable
private fun TyFilterChips(selected: TyFilter, onSelect: (TyFilter) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TyFilter.entries.forEach { f ->
            val accent = when (f) {
                TyFilter.ALL -> SeismicHot.Red
                TyFilter.SUPER -> SeismicHot.Red
                TyFilter.TYPHOON -> Color(0xFFFF4D2D)
                TyFilter.SEVERE -> SeismicHot.Orange
                TyFilter.STORM -> SeismicHot.Yellow
            }
            Chip(label = f.label, accent = accent, active = f == selected, isAll = f == TyFilter.ALL) { onSelect(f) }
        }
    }
}

// ── Philippine weather — live for cities across Luzon / Visayas / Mindanao (Open-Meteo, no key) ──
@Composable
private fun WeatherSection(viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val repo = remember { WeatherRepository() }
    val places = remember { PhilippineWeatherPoints.PLACES }
    var query by remember { mutableStateOf("") }
    var island by remember { mutableStateOf<String?>(null) } // null = All islands
    var reload by remember { mutableStateOf(0) }             // bump to re-fetch (retry button)
    var ascending by remember { mutableStateOf(true) }       // A→Z (true) / Z→A (false)
    var mapMode by remember { mutableStateOf(false) }        // false = list, true = Panahon map
    // Tapped place → opens the detailed forecast sheet. Triple = (title, lat, lon).
    var selected by remember { mutableStateOf<Triple<String, Double, Double>?>(null) }

    // Live Open-Meteo lookups for every city, fired in parallel. null = still loading. Re-runs when
    // [reload] changes so the user can retry after a spotty network left some cities "Unavailable".
    val weather by produceState<List<LiveWeather?>?>(initialValue = null, reload) {
        value = null
        value = repo.fetchBulkWeather(places.map { it.lat to it.lon })
    }
    val loadedCount = weather?.count { it != null } ?: 0

    // The user's own location weather — already fetched by the ViewModel for their live GPS position.
    val state by viewModel.uiState.collectAsState()
    val myWeather = state.liveWeather

    val q = query.trim()
    val shown = places.mapIndexed { i, p -> p to weather?.getOrNull(i) }
        .filter { (p, _) ->
            (island == null || p.island == island) &&
                (q.isBlank() || p.name.contains(q, true) || p.island.contains(q, true))
        }
        .sortedBy { it.first.name.lowercase() }
        .let { if (ascending) it else it.asReversed() }

    Column(modifier.fillMaxWidth()) {
        // Header: title on the left, a compact search box on the right.
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Thermostat, null, tint = SeismicHot.Signal, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(8.dp))
            Text("PHILIPPINE WEATHER", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = SeismicHot.Signal)
            Spacer(Modifier.weight(1f))
            CompactSearch(query, { query = it }, Modifier.width(148.dp))
        }
        Spacer(Modifier.height(6.dp))
        // Progress / retry line. When nothing loaded, surface the ACTUAL error so it's diagnosable.
        val allFailed = weather != null && loadedCount == 0
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                when {
                    weather == null -> "Loading live weather for ${places.size} places…"
                    allFailed -> "Couldn't load weather: ${repo.lastError ?: "no connection"}"
                    else -> "$loadedCount of ${places.size} places · live · Open-Meteo"
                },
                fontSize = 10.5.sp,
                color = if (allFailed) SeismicHot.Red else SeismicHot.Muted,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.weight(1f))
            if (weather != null && loadedCount < places.size) {
                Text(
                    "Retry",
                    fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SeismicHot.Signal,
                    modifier = Modifier.clickable { reload++ },
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        // Island-group filter chips.
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Chip("All", SeismicHot.Signal, active = island == null, isAll = true) { island = null }
            PhilippineWeatherPoints.ISLANDS.forEach { name ->
                Chip(name, SeismicHot.Signal, active = island == name, isAll = false) {
                    island = if (island == name) null else name
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        // View toggle (List / Panahon map) + alphabetical sort direction.
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MiniToggle("List", active = !mapMode) { mapMode = false }
            MiniToggle("Panahon Map", active = mapMode) { mapMode = true }
            Spacer(Modifier.weight(1f))
            if (!mapMode) {
                Row(
                    Modifier.clip(RoundedCornerShape(10.dp)).background(SeismicHot.Field)
                        .border(1.dp, SeismicHot.Border, RoundedCornerShape(10.dp))
                        .clickable { ascending = !ascending }.padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(Icons.Outlined.SwapVert, null, tint = SeismicHot.Label, modifier = Modifier.size(15.dp))
                    Text(if (ascending) "A–Z" else "Z–A", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = SeismicHot.Label)
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        if (mapMode) {
            PanahonMap(
                places = shown,
                loading = weather == null,
                island = island,
                onPick = { name, lat, lon -> selected = Triple(name, lat, lon) },
            )
        } else {
            Column(
                Modifier.padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // The user's own location first — "accurate location shows their weather".
                YourLocationCard(myWeather, onClick = { selected = Triple("Your Location", viewModel.userLat, viewModel.userLon) })
                if (shown.isEmpty()) {
                    WeatherInfoCard("No place matches “$q”.")
                } else {
                    shown.forEach { (p, w) ->
                        key(p.name) {
                            CityWeatherCard(p.name, p.island, w, loading = weather == null,
                                onClick = { selected = Triple(p.name, p.lat, p.lon) })
                        }
                    }
                }
            }
        }
    }

    selected?.let { (title, lat, lon) ->
        WeatherDetailDialog(repo, title, lat, lon, onDismiss = { selected = null })
    }
}

/** Small pill toggle used for the List / Panahon-map switch. */
@Composable
private fun MiniToggle(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(10.dp))
            .then(
                if (active) Modifier.background(SeismicHot.Signal.copy(alpha = 0.18f))
                    .border(1.dp, SeismicHot.Signal.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                else Modifier.background(SeismicHot.Field).border(1.dp, SeismicHot.Border, RoundedCornerShape(10.dp))
            )
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(label, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = if (active) SeismicHot.Signal else SeismicHot.Muted)
    }
}

/**
 * "Panahon" live weather map — every place plotted on an OpenFreeMap dark basemap as a circle colored
 * by its weather-power tier, labelled with name + temperature. Reuses [MapLibreCanvas]; tapping a pin
 * opens that place's detailed forecast.
 */
@Composable
private fun PanahonMap(
    places: List<Pair<PhilippineWeatherPoints.Place, LiveWeather?>>,
    loading: Boolean,
    island: String?,
    onPick: (name: String, lat: Double, lon: Double) -> Unit,
) {
    val pins = places.map { (p, w) ->
        val color = (w?.power?.let { powerColor(it) } ?: SeismicHot.Muted).toArgb()
        val label = if (w?.tempC != null) "${p.name} ${w.tempC.roundToInt()}°" else p.name
        MapPin(id = p.name, lat = p.lat, lon = p.lon, colorInt = color, radiusDp = 7f, label = label)
    }
    val byName = remember(places) { places.associate { it.first.name to it.first } }

    // Camera target per island so picking a filter flies the map to that island group.
    val (target, targetZoom) = when (island) {
        PhilippineWeatherPoints.LUZON -> LatLng(16.5, 121.2) to 6.0
        PhilippineWeatherPoints.VISAYAS -> LatLng(10.9, 123.6) to 6.8
        PhilippineWeatherPoints.MINDANAO -> LatLng(7.6, 125.0) to 6.2
        else -> LatLng(12.4, 122.6) to 5.1
    }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    LaunchedEffect(map, island) {
        map?.animateCamera(CameraUpdateFactory.newLatLngZoom(target, targetZoom), 700)
    }

    Box(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp)
            .height(440.dp).clip(RoundedCornerShape(16.dp))
            .border(1.dp, SeismicHot.Border, RoundedCornerShape(16.dp)),
    ) {
        MapLibreCanvas(
            pins = pins,
            initialLat = target.latitude, initialLon = target.longitude, initialZoom = targetZoom,
            minZoom = 4.5, maxZoom = 12.0,
            showLabels = true,
            glow = true,
            onPinClick = { id -> byName[id]?.let { onPick(it.name, it.lat, it.lon) } },
            onMapReady = { map = it },
            modifier = Modifier.fillMaxSize(),
        )
        if (loading) {
            Box(
                Modifier.align(Alignment.TopCenter).padding(10.dp)
                    .clip(RoundedCornerShape(10.dp)).background(SeismicHot.Card.copy(alpha = 0.9f))
                    .border(1.dp, SeismicHot.Border, RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) { Text("Loading live weather…", fontSize = 11.sp, color = SeismicHot.Label) }
        }
        // Legend.
        Row(
            Modifier.align(Alignment.BottomStart).padding(10.dp)
                .clip(RoundedCornerShape(10.dp)).background(SeismicHot.Card.copy(alpha = 0.9f))
                .border(1.dp, SeismicHot.Border, RoundedCornerShape(10.dp))
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LegendDot("Calm", SeismicHot.Safe)
            LegendDot("Windy", SeismicHot.Yellow)
            LegendDot("Blustery", SeismicHot.Orange)
            LegendDot("Stormy", Color(0xFFFF4D2D))
            LegendDot("Severe", SeismicHot.Red)
        }
    }
}

@Composable
private fun LegendDot(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(color))
        Text(label, fontSize = 9.5.sp, color = SeismicHot.Label)
    }
}

@Composable
private fun CompactSearch(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .height(40.dp)
            .clip(RoundedCornerShape(12.dp)).background(SeismicHot.Field)
            .border(1.5.dp, SeismicHot.Border, RoundedCornerShape(12.dp)).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Search, null, tint = SeismicHot.Muted, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) Text("Search", color = SeismicHot.Muted, fontSize = 13.sp)
            BasicTextField(
                value = query, onValueChange = onQueryChange, singleLine = true,
                textStyle = TextStyle(color = SeismicHot.White, fontSize = 13.sp, fontFamily = SeismicFont),
                cursorBrush = Brush.linearGradient(listOf(SeismicHot.Red, SeismicHot.Red)),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun YourLocationCard(w: LiveWeather?, onClick: () -> Unit) {
    val accent = w?.power?.let { powerColor(it) } ?: SeismicHot.Signal
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp)).background(SeismicHot.Signal.copy(alpha = 0.10f))
            .border(1.dp, SeismicHot.Signal.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.NearMe, null, tint = SeismicHot.Signal, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Your Location", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = SeismicHot.White)
            Text(
                w?.let { it.condition + (it.tempC?.let { t -> " · ${t.roundToInt()}°C" } ?: "") }
                    ?: "Enable location to see your local weather",
                fontSize = 12.sp, color = SeismicHot.Label,
            )
        }
        w?.let { wx -> WeatherStat(wx, accent) }
        Spacer(Modifier.width(6.dp))
        Icon(Icons.Outlined.ChevronRight, null, tint = SeismicHot.Muted, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun CityWeatherCard(name: String, island: String, w: LiveWeather?, loading: Boolean, onClick: () -> Unit) {
    val accent = w?.power?.let { powerColor(it) } ?: SeismicHot.Muted
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp)).background(SeismicHot.Card)
            .border(1.dp, SeismicHot.Border, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = SeismicHot.White)
                Text(island.uppercase(), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp, color = SeismicHot.Muted)
            }
            Text(
                when {
                    loading -> "Loading…"
                    w == null -> "Unavailable"
                    else -> w.condition + (w.tempC?.let { " · ${it.roundToInt()}°C" } ?: "")
                },
                fontSize = 12.sp, color = SeismicHot.Label,
            )
        }
        w?.let { wx -> WeatherStat(wx, accent) }
        Spacer(Modifier.width(6.dp))
        Icon(Icons.Outlined.ChevronRight, null, tint = SeismicHot.Muted, modifier = Modifier.size(18.dp))
    }
}

/** Right-hand stat block shared by the location + city cards: wind speed + weather-power pill. */
@Composable
private fun WeatherStat(w: LiveWeather, accent: Color) {
    Column(horizontalAlignment = Alignment.End) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(Icons.Outlined.Air, null, tint = SeismicHot.Muted, modifier = Modifier.size(13.dp))
            Text("${w.windKph.roundToInt()} km/h", fontSize = 12.sp, color = SeismicHot.Label)
        }
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier.clip(RoundedCornerShape(7.dp)).background(accent.copy(alpha = 0.15f))
                .border(1.dp, accent.copy(alpha = 0.4f), RoundedCornerShape(7.dp))
                .padding(horizontal = 8.dp, vertical = 3.dp),
        ) {
            Text(w.power.label.uppercase(), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp, color = accent)
        }
    }
}

private fun powerColor(p: WeatherPower): Color = when (p) {
    WeatherPower.CALM, WeatherPower.BREEZY -> SeismicHot.Safe
    WeatherPower.WINDY -> SeismicHot.Yellow
    WeatherPower.BLUSTERY -> SeismicHot.Orange
    WeatherPower.STORMY -> Color(0xFFFF4D2D)
    WeatherPower.SEVERE -> SeismicHot.Red
}

@Composable
private fun WeatherInfoCard(text: String) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp)).background(SeismicHot.Card)
            .border(1.dp, SeismicHot.Border, RoundedCornerShape(14.dp)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Outlined.Search, null, tint = SeismicHot.Muted, modifier = Modifier.size(16.dp))
        Text(text, fontSize = 12.5.sp, color = SeismicHot.Muted)
    }
}

// ── Detailed forecast sheet (tap any weather card) ────────────────────────────────────────────
@Composable
private fun WeatherDetailDialog(
    repo: WeatherRepository,
    title: String,
    lat: Double,
    lon: Double,
    onDismiss: () -> Unit,
) {
    var detail by remember(lat, lon) { mutableStateOf<WeatherDetail?>(null) }
    var done by remember(lat, lon) { mutableStateOf(false) }
    var reload by remember(lat, lon) { mutableStateOf(0) }
    LaunchedEffect(lat, lon, reload) {
        done = false
        detail = repo.fetchWeatherDetail(lat, lon)
        done = true
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxSize().background(SeismicHot.Base),
        ) {
            // Top bar
            Row(
                Modifier.fillMaxWidth().background(SeismicHot.Nav).statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = SeismicHot.White, modifier = Modifier.size(22.dp)) }
                Spacer(Modifier.width(4.dp))
                Text(title, color = SeismicHot.White, fontFamily = SeismicFont, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Box(
                    Modifier.size(40.dp).clip(CircleShape).clickable(enabled = done) { reload++ },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Outlined.Refresh, "Refresh", tint = if (done) SeismicHot.White else SeismicHot.Muted, modifier = Modifier.size(22.dp)) }
            }

            val snapshot = detail
            when {
                !done -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Loading forecast…", color = SeismicHot.Muted, fontSize = 13.sp)
                }
                snapshot == null -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "Forecast unavailable.\n${repo.lastError ?: "Check your internet connection."}",
                        color = SeismicHot.Muted, fontSize = 13.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
                else -> WeatherDetailContent(snapshot)
            }
        }
    }
}

@Composable
private fun WeatherDetailContent(d: WeatherDetail) {
    val c = d.current
    val accent = powerColor(c.power)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp).navigationBarsPadding(),
    ) {
        // ── Hero: big temp + condition ──
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(wmoEmoji(c.weatherCode), fontSize = 52.sp)
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    c.tempC?.let { "${it.roundToInt()}°C" } ?: "—",
                    fontFamily = SeismicDisplayFont, fontSize = 46.sp, fontWeight = FontWeight.Bold,
                    letterSpacing = (-2).sp, color = SeismicHot.White,
                )
                Text(c.condition, fontSize = 14.sp, color = SeismicHot.Label)
                d.apparentC?.let {
                    Text("Feels like ${it.roundToInt()}°C", fontSize = 12.sp, color = SeismicHot.Muted)
                }
            }
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.clip(RoundedCornerShape(9.dp)).background(accent.copy(alpha = 0.15f))
                    .border(1.dp, accent.copy(alpha = 0.4f), RoundedCornerShape(9.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            ) { Text(c.power.label.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = accent) }
        }

        Spacer(Modifier.height(18.dp))

        // ── Stat grid ──
        val stats = buildList {
            add("Wind" to "${c.windKph.roundToInt()} km/h")
            add("Gusts" to "${c.gustKph.roundToInt()} km/h")
            c.humidity?.let { add("Humidity" to "$it%") }
            add("Rain" to "${"%.1f".format(c.precipMm)} mm")
            d.cloudCover?.let { add("Cloud" to "$it%") }
            d.pressureHpa?.let { add("Pressure" to "${it.roundToInt()} hPa") }
            d.uvIndexMax?.let { add("UV (max)" to "${it.roundToInt()}") }
            add("Sun" to "${timeOnly(d.sunrise)} / ${timeOnly(d.sunset)}")
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            stats.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { (k, v) -> StatTile(k, v, Modifier.weight(1f)) }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }

        // ── Hourly (next 24h) ──
        if (d.hourly.isNotEmpty()) {
            val hours = d.hourly.take(24)
            Spacer(Modifier.height(20.dp))
            SectionHeading("NEXT 24 HOURS")
            Spacer(Modifier.height(10.dp))
            TemperatureSparkline(hours, accent, Modifier.fillMaxWidth().height(64.dp))
            Spacer(Modifier.height(10.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                hours.forEach { h -> HourCell(h) }
            }
        }

        // ── Daily (7 days) ──
        if (d.daily.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            SectionHeading("7-DAY FORECAST")
            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                d.daily.forEach { day -> DayRow(day) }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("Live · Open-Meteo", fontSize = 10.sp, color = SeismicHot.Muted, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp)).background(SeismicHot.Card)
            .border(1.dp, SeismicHot.Border, RoundedCornerShape(12.dp)).padding(12.dp),
    ) {
        Text(label.uppercase(), fontSize = 9.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp, color = SeismicHot.Muted)
        Spacer(Modifier.height(3.dp))
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = SeismicHot.White)
    }
}

/** A smooth temperature curve across the next 24 hours, with min/max markers and a soft fill. */
@Composable
private fun TemperatureSparkline(hours: List<HourPoint>, accent: Color, modifier: Modifier = Modifier) {
    if (hours.size < 2) return
    val temps = hours.map { it.tempC }
    val minT = temps.min()
    val maxT = temps.max()
    val span = (maxT - minT).takeIf { it > 0.5 } ?: 1.0
    val labelColor = SeismicHot.Muted
    val gridColor = SeismicHot.Border

    Box(
        modifier.clip(RoundedCornerShape(12.dp)).background(SeismicHot.Card)
            .border(1.dp, SeismicHot.Border, RoundedCornerShape(12.dp)),
    ) {
        Canvas(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 12.dp)) {
            val w = size.width
            val h = size.height
            fun pointFor(i: Int): Offset {
                val x = if (temps.size == 1) 0f else w * i / (temps.size - 1)
                val norm = ((temps[i] - minT) / span).toFloat()
                val y = h - norm * h
                return Offset(x, y)
            }
            // Baseline grid line.
            drawLine(gridColor, Offset(0f, h), Offset(w, h), strokeWidth = 1f)

            val pts = temps.indices.map { pointFor(it) }
            // Soft gradient fill under the curve.
            val fill = Path().apply {
                moveTo(0f, h)
                pts.forEach { lineTo(it.x, it.y) }
                lineTo(w, h)
                close()
            }
            drawPath(
                fill,
                brush = Brush.verticalGradient(listOf(accent.copy(alpha = 0.28f), accent.copy(alpha = 0.02f))),
            )
            // The line itself.
            val line = Path().apply {
                moveTo(pts.first().x, pts.first().y)
                pts.drop(1).forEach { lineTo(it.x, it.y) }
            }
            drawPath(line, color = accent, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f))
            // Dots at the hottest + coldest hour.
            val hotI = temps.indexOf(maxT)
            val coldI = temps.indexOf(minT)
            drawCircle(accent, radius = 4f, center = pointFor(hotI))
            drawCircle(labelColor, radius = 4f, center = pointFor(coldI))
        }
        // Min / max labels.
        Text("${maxT.roundToInt()}°", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = SeismicHot.White,
            modifier = Modifier.align(Alignment.TopStart).padding(6.dp))
        Text("${minT.roundToInt()}°", fontSize = 10.sp, color = SeismicHot.Muted,
            modifier = Modifier.align(Alignment.BottomStart).padding(6.dp))
    }
}

@Composable
private fun HourCell(h: HourPoint) {
    Column(
        Modifier.width(62.dp).clip(RoundedCornerShape(12.dp)).background(SeismicHot.Card)
            .border(1.dp, SeismicHot.Border, RoundedCornerShape(12.dp)).padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(hourOnly(h.timeIso), fontSize = 10.5.sp, color = SeismicHot.Label)
        Spacer(Modifier.height(4.dp))
        Text(wmoEmoji(h.weatherCode), fontSize = 20.sp)
        Spacer(Modifier.height(4.dp))
        Text("${h.tempC.roundToInt()}°", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = SeismicHot.White)
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.WaterDrop, null, tint = SeismicHot.Signal, modifier = Modifier.size(9.dp))
            Text("${h.precipProb}%", fontSize = 9.5.sp, color = SeismicHot.Signal)
        }
    }
}

@Composable
private fun DayRow(day: DayPoint) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SeismicHot.Card)
            .border(1.dp, SeismicHot.Border, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(dayLabel(day.dateIso), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = SeismicHot.White, modifier = Modifier.width(64.dp))
        Text(wmoEmoji(day.weatherCode), fontSize = 20.sp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(wmoLabel(day.weatherCode), fontSize = 11.5.sp, color = SeismicHot.Label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.WaterDrop, null, tint = SeismicHot.Signal, modifier = Modifier.size(10.dp))
                Text(" ${day.precipProb}% · ${"%.0f".format(day.precipMm)} mm", fontSize = 10.5.sp, color = SeismicHot.Muted)
            }
        }
        Text("${day.maxC.roundToInt()}°", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = SeismicHot.White)
        Spacer(Modifier.width(6.dp))
        Text("${day.minC.roundToInt()}°", fontSize = 14.sp, color = SeismicHot.Muted)
    }
}

@Composable
private fun SectionHeading(text: String) {
    Text(text, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = SeismicHot.Signal)
}

/** Emoji glyph for a WMO weather code — a lightweight, always-available icon. */
private fun wmoEmoji(code: Int): String = when (code) {
    0 -> "☀️"
    1, 2 -> "🌤️"
    3 -> "☁️"
    45, 48 -> "🌫️"
    in 51..57 -> "🌦️"
    in 61..67 -> "🌧️"
    in 71..77 -> "🌨️"
    in 80..82 -> "🌧️"
    85, 86 -> "🌨️"
    in 95..99 -> "⛈️"
    else -> "🌡️"
}

/** "2026-07-03T15:00" → "3 PM". */
private fun hourOnly(iso: String): String = runCatching {
    val hm = iso.substringAfter("T")
    val hour = hm.substringBefore(":").toInt()
    val ampm = if (hour < 12) "AM" else "PM"
    val h12 = when { hour == 0 -> 12; hour > 12 -> hour - 12; else -> hour }
    "$h12 $ampm"
}.getOrDefault(iso)

/** "2026-07-03T06:12" → "6:12 AM" (time portion only). */
private fun timeOnly(iso: String?): String {
    iso ?: return "—"
    return runCatching {
        val hm = iso.substringAfter("T")
        val hour = hm.substringBefore(":").toInt()
        val min = hm.substringAfter(":").take(2)
        val ampm = if (hour < 12) "AM" else "PM"
        val h12 = when { hour == 0 -> 12; hour > 12 -> hour - 12; else -> hour }
        "$h12:$min $ampm"
    }.getOrDefault("—")
}

/** "2026-07-03" → "Today" / "Fri" etc. */
private fun dayLabel(iso: String): String = runCatching {
    val cal = java.util.Calendar.getInstance()
    val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(cal.time)
    if (iso == today) return "Today"
    val date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).parse(iso)
    java.text.SimpleDateFormat("EEE", java.util.Locale.getDefault()).format(date!!)
}.getOrDefault(iso)

@Composable
private fun StormCard(t: Typhoon, distanceKm: Double, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val accent = catColor(t.category)
    val wind = t.windKph?.roundToInt() ?: 0
    val signal = signalOf(t)
    Column(
        modifier = modifier
            .padding(horizontal = 20.dp).fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.15f), accent.copy(alpha = 0.05f))))
            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick).padding(16.dp),
    ) {
        // Top: icon + category/name + big wind.
        Row(verticalAlignment = Alignment.Top) {
            Box(
                Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(accent.copy(alpha = 0.18f))
                    .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.Cyclone, null, tint = accent, modifier = Modifier.size(24.dp)) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Badge(t.category.label, accent)
                Spacer(Modifier.height(5.dp))
                Text(t.name, fontFamily = SeismicDisplayFont, fontSize = 19.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp, color = SeismicHot.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(describeLocation(t), fontSize = 11.5.sp, color = SeismicHot.Label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text("$wind", fontFamily = SeismicDisplayFont, fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1.5).sp, color = accent)
                Text("KM/H", fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp, color = SeismicHot.Muted)
            }
        }
        Spacer(Modifier.height(14.dp))
        // Mini stats: Signal · Distance · Alert.
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MiniStat("SIGNAL", "No. $signal", Modifier.weight(1f))
            MiniStat("DISTANCE", "${distanceKm.roundToInt()} km", Modifier.weight(1f))
            MiniStat("ALERT", t.alertLevel.label, Modifier.weight(1f))
        }
        Spacer(Modifier.height(14.dp))
        // Wind-intensity bar.
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Wind Intensity", fontSize = 10.5.sp, color = SeismicHot.Muted)
            Text(damageLabel(t.windKph ?: 0.0), fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = accent)
        }
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().height(7.dp).clip(CircleShape).background(SeismicHot.Field)) {
            // Single calm fill in the storm's own accent (no loud yellow→orange→red gradient).
            Box(
                Modifier.fillMaxWidth(((t.windKph ?: 0.0) / 250.0).toFloat().coerceIn(0.05f, 1f)).height(7.dp).clip(CircleShape)
                    .background(accent),
            )
        }
        Spacer(Modifier.height(14.dp))
        // Bottom badges + open affordance.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Badge("SIGNAL NO. $signal", accent)
            StatusBadge(t.alertLevel)
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.size(32.dp).clip(CircleShape).background(accent.copy(alpha = 0.18f)).border(1.dp, accent.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.ChevronRight, "Track storm", tint = accent, modifier = Modifier.size(18.dp)) }
        }
    }
}

@Composable
private fun MonitorCard(t: Typhoon, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val accent = catColor(t.category)
    val wind = t.windKph?.roundToInt() ?: 0
    Row(
        modifier = modifier
            .padding(horizontal = 20.dp).fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)).background(SeismicHot.Card.copy(alpha = 0.6f))
            .border(1.dp, SeismicHot.Border, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(accent.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Outlined.Cyclone, null, tint = accent.copy(alpha = 0.8f), modifier = Modifier.size(20.dp)) }
        Column(Modifier.weight(1f)) {
            Text(t.category.label.uppercase(), fontSize = 9.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp, color = SeismicHot.Muted)
            Spacer(Modifier.height(2.dp))
            Text(t.name, fontFamily = SeismicDisplayFont, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = SeismicHot.White.copy(alpha = 0.85f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(describeLocation(t), fontSize = 11.sp, color = SeismicHot.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("$wind", fontFamily = SeismicDisplayFont, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = SeismicHot.Muted)
            Text("KM/H", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = SeismicHot.Muted)
        }
        Icon(Icons.Outlined.ChevronRight, null, tint = SeismicHot.Muted, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun MiniStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp)).background(SeismicHot.Base.copy(alpha = 0.4f))
            .border(1.dp, SeismicHot.Border, RoundedCornerShape(12.dp))
            .padding(vertical = 10.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, fontSize = 8.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp, color = SeismicHot.Muted)
        Spacer(Modifier.height(3.dp))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = SeismicHot.White, maxLines = 1)
    }
}

@Composable
private fun StatusBadge(alert: TyphoonAlert) {
    val (label, color) = when (alert) {
        TyphoonAlert.RED -> "WARNING" to SeismicHot.Red
        TyphoonAlert.ORANGE -> "WATCH" to SeismicHot.Orange
        TyphoonAlert.GREEN -> "MONITORING" to SeismicHot.Safe
    }
    Box(
        Modifier.clip(RoundedCornerShape(7.dp)).background(color.copy(alpha = 0.15f))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(7.dp)).padding(horizontal = 9.dp, vertical = 4.dp),
    ) { Text(label, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp, color = color) }
}

// ══════════════════════════════════════════════════════════════════════════════
//  SHARED BUILDING BLOCKS
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun FeedHeader(
    title: String,
    subtitle: String = "",
    pillText: String,
    accent: Color,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(title, fontFamily = SeismicDisplayFont, fontSize = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1.5).sp, color = SeismicHot.White)
                Text(".", fontFamily = SeismicDisplayFont, fontSize = 32.sp, fontWeight = FontWeight.Bold, color = accent)
            }
            if (subtitle.isNotBlank()) Text(subtitle, fontSize = 12.5.sp, color = SeismicHot.Muted)
            Spacer(Modifier.height(9.dp))
            Row(
                modifier = Modifier
                    .clip(CircleShape).background(accent.copy(alpha = 0.12f))
                    .border(1.dp, accent.copy(alpha = 0.3f), CircleShape).padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                SeismicBlinkingDot(accent, 6.dp)
                Text(pillText, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp, color = accent)
            }
        }
        Spacer(Modifier.width(10.dp))
        // Live radar dial — accent-tinted rings sweeping behind the feed's glyph.
        RadarPulse(accent = accent, diameter = 76.dp, ringCount = 3, periodMillis = 2600, sweep = true) {
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(accent.copy(alpha = 0.18f))
                    .border(1.dp, accent.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, tint = accent, modifier = Modifier.size(20.dp)) }
        }
    }
}

@Composable
private fun SummaryCard(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp)).background(SeismicHot.Card)
            .border(1.dp, SeismicHot.Border, RoundedCornerShape(14.dp)).padding(horizontal = 10.dp, vertical = 12.dp),
    ) {
        // Reserve exactly two lines for every label so a wrapping title (e.g. "SIGNAL 1–2") can't
        // push its number down and knock the row of cards out of horizontal alignment.
        Text(
            label,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp,
            lineHeight = 12.sp,
            color = SeismicHot.Muted,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Text(value, fontFamily = SeismicDisplayFont, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .padding(horizontal = 20.dp).fillMaxWidth().height(50.dp)
            .clip(RoundedCornerShape(14.dp)).background(SeismicHot.Field)
            .border(1.5.dp, SeismicHot.Border, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Search, null, tint = SeismicHot.Muted, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) Text(placeholder, color = SeismicHot.Muted, fontSize = 14.sp)
            BasicTextField(
                value = query, onValueChange = onQueryChange, singleLine = true,
                textStyle = TextStyle(color = SeismicHot.White, fontSize = 14.sp, fontFamily = SeismicFont),
                cursorBrush = Brush.linearGradient(listOf(SeismicHot.Red, SeismicHot.Red)),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun Chip(label: String, accent: Color, active: Boolean, isAll: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .then(
                if (active && isAll) Modifier.background(SeismicHot.Gradient)
                else if (active) Modifier.background(accent.copy(alpha = 0.20f)).border(1.dp, accent.copy(alpha = 0.5f), CircleShape)
                else Modifier.background(SeismicHot.Field).border(1.5.dp, SeismicHot.Border, CircleShape)
            )
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (!isAll) Box(Modifier.size(8.dp).clip(CircleShape).background(accent))
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (active) (if (isAll) SeismicHot.White else accent) else SeismicHot.Muted, maxLines = 1)
    }
}

@Composable
private fun Badge(text: String, color: Color) {
    Box(
        Modifier.clip(RoundedCornerShape(7.dp)).background(color.copy(alpha = 0.18f))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(7.dp)).padding(horizontal = 9.dp, vertical = 3.dp),
    ) { Text(text.uppercase(), fontSize = 9.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp, color = color) }
}

@Composable
private fun MetaBadge(icon: ImageVector, text: String) {
    Row(
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(SeismicHot.Field).padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(icon, null, tint = SeismicHot.Muted, modifier = Modifier.size(12.dp))
        Text(text, fontSize = 10.5.sp, color = SeismicHot.Label)
    }
}

@Composable
private fun LoadMoreButton(remaining: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .padding(horizontal = 20.dp).fillMaxWidth()
            .clip(RoundedCornerShape(13.dp)).background(SeismicHot.Card)
            .border(1.dp, SeismicHot.Border, RoundedCornerShape(13.dp)).clickable(onClick = onClick).padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Load more — $remaining remaining", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = SeismicHot.Label)
    }
}

@Composable
private fun EmptyState(icon: ImageVector, title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = SeismicHot.Muted, modifier = Modifier.size(34.dp))
        Spacer(Modifier.height(10.dp))
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = SeismicHot.White)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, fontSize = 12.5.sp, color = SeismicHot.Muted)
    }
}

// ─── Helpers ─────────────────────────────────────────────────────────────────
private fun parseQuakePlace(place: String): Pair<String, String> {
    if (place.isBlank()) return "Unknown area" to ""
    val province = Regex("""\(([^)]+)\)""").find(place)?.groupValues?.getOrNull(1)?.trim()
    val noParen = place.replace(Regex("""\s*\([^)]*\)"""), "").trim()
    val locality = if (noParen.contains(" of ")) noParen.substringAfterLast(" of ").trim() else noParen
    val title = when {
        !province.isNullOrBlank() && locality.isNotBlank() && !locality.equals(province, true) -> "$locality, $province"
        !province.isNullOrBlank() -> province
        else -> locality.ifBlank { place }
    }
    return title to noParen.ifBlank { place }
}

private fun quakeOrigin(eq: Earthquake): String {
    val p = eq.place.lowercase()
    val volcanic = listOf("albay", "mayon", "bulusan", "sorsogon", "kanlaon", "camiguin", "biliran", "taal")
    return when {
        volcanic.any { p.contains(it) } -> "Volcanic"
        eq.depth <= 10.0 -> "Shallow"
        else -> "Tectonic"
    }
}

/** Philippine Area of Responsibility, box-approximated (5–25°N, 115–135°E). */
private fun insidePar(t: Typhoon): Boolean =
    t.latitude in 5.0..25.0 && t.longitude in 115.0..135.0

private fun damageLabel(windKph: Double): String = when {
    windKph < 62 -> "Minimal"
    windKph < 89 -> "Moderate Damage"
    windKph < 118 -> "Significant Damage"
    windKph < 185 -> "Very Destructive"
    else -> "Destructive Force"
}

/** A few PH coastal reference points used to describe a storm's position relative to land. */
private val PH_LANDMARKS = listOf(
    "Batanes" to (20.5 to 122.0),
    "Cagayan" to (18.0 to 121.8),
    "Aurora" to (15.7 to 121.6),
    "Eastern Samar" to (11.5 to 125.5),
    "Catanduanes" to (13.7 to 124.3),
    "Surigao" to (9.8 to 125.5),
    "Davao City" to (7.07 to 125.6),
    "General Santos" to (6.1 to 125.2),
    "Zamboanga" to (6.9 to 122.1),
    "Palawan" to (9.8 to 118.7),
    "Iloilo" to (10.7 to 122.6),
    "Manila" to (14.6 to 121.0),
)

/** "480 km E of Eastern Samar" — nearest landmark + cardinal bearing + distance. */
private fun describeLocation(t: Typhoon): String {
    val nearest = PH_LANDMARKS.minByOrNull { haversineKm(it.second.first, it.second.second, t.latitude, t.longitude) }
        ?: return "Philippine Sea"
    val (name, coord) = nearest
    val dist = haversineKm(coord.first, coord.second, t.latitude, t.longitude).roundToInt()
    val bearing = compass(coord.first, coord.second, t.latitude, t.longitude)
    val distStr = if (dist >= 1000) "%,d".format(dist) else dist.toString()
    val ref = if (dist > 900) "the Philippines" else name
    return "${distStr}km $bearing of $ref"
}

private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
    return 2 * r * Math.asin(Math.sqrt(a))
}

private fun compass(lat1: Double, lon1: Double, lat2: Double, lon2: Double): String {
    val dLon = Math.toRadians(lon2 - lon1)
    val y = sin(dLon) * cos(Math.toRadians(lat2))
    val x = cos(Math.toRadians(lat1)) * sin(Math.toRadians(lat2)) -
        sin(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * cos(dLon)
    val deg = (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    val dirs = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    return dirs[((deg + 22.5) / 45.0).toInt() % 8]
}
