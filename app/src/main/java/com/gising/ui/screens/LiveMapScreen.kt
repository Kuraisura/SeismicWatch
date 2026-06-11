package com.gising.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material.icons.outlined.Storm
import androidx.compose.material.icons.outlined.WbSunny
import com.gising.ui.theme.SeismicDisplayFont
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gising.data.model.Earthquake
import com.gising.data.model.LiveWeather
import com.gising.data.model.PagasaCyclone
import com.gising.data.model.Typhoon
import com.gising.data.model.WeatherPower
import com.gising.ui.screens.components.MapLibreCanvas
import com.gising.ui.screens.components.MapPin
import com.gising.ui.theme.SeismicDisplayFont
import com.gising.ui.theme.SeismicFont
import com.gising.ui.theme.SeismicHot
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap

private const val REFRESH_INTERVAL_MS = 60_000L
private const val TYPHOON_COLOR = 0xFF7C4DFF.toInt()
private val USER_COLOR = 0xFF4D7CFE.toInt()

// Magnitude-tier palette — a green→crimson severity ramp tuned to read well on the dark map.
private val TierMinor = SeismicHot.Safe          // < 4
private val TierLight = SeismicHot.Yellow        // 4 – 5
private val TierModerate = SeismicHot.Orange     // 5 – 6
private val TierStrong = Color(0xFFFF4D2D)       // 6 – 7
private val TierMajor = SeismicHot.Red           // ≥ 7

// Center of the Philippine archipelago + a snug bounds box that keeps the camera target pinned on
// the country (so it can't be dragged off to neighbouring seas) and a default overview zoom.
private const val PH_CENTER_LAT = 12.6
private const val PH_CENTER_LON = 122.0
private const val PH_OVERVIEW_ZOOM = 4.4
private val PH_BOUNDS: LatLngBounds = LatLngBounds.from(
    /* latNorth = */ 21.2, /* lonEast = */ 127.5, /* latSouth = */ 4.3, /* lonWest = */ 116.3,
)

/** Magnitude categories used by the map's filter chips + legend. */
private enum class MagFilter(val label: String, val range: ClosedFloatingPointRange<Double>) {
    ALL("All", 0.0..12.0),
    MINOR("< 4", 0.0..3.999),
    LIGHT("4 – 5", 4.0..4.999),
    MODERATE("5 – 6", 5.0..5.999),
    STRONG("6 – 7", 6.0..6.999),
    MAJOR("≥ 7", 7.0..12.0);

    fun matches(mag: Double) = mag in range
}

/**
 * Full-screen live map of recent earthquakes AND active typhoons across the Philippine region —
 * "SeismicWatch · Tactical Vibrant" overlays on an OpenFreeMap dark basemap.
 *
 * Earthquakes plot as magnitude-tiered circles + tappable markers (tap → frame that zone, tap the
 * focused event again → detail); typhoons plot as purple storm circles with an info bubble. Layers
 * and magnitude categories can be toggled/filtered, and while the screen is open the feeds
 * auto-refresh every minute so the picture stays realtime without any user action.
 */
@Composable
fun LiveMapScreen(
    viewModel: MainViewModel,
    onEarthquakeClick: (Earthquake) -> Unit,
    onOpenCyclone: () -> Unit = {},
    initialFocus: Earthquake? = null,
    onFocusConsumed: () -> Unit = {},
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    // The list/Reports keep full 30-day history; the map shows the last 7 days so it stays
    // readable and light to redraw on each refresh.
    val quakes = remember(state.earthquakes) {
        val weekAgo = System.currentTimeMillis() - 7L * 24 * 3600_000
        state.earthquakes.filter { it.timeMs >= weekAgo }
    }
    val typhoons = state.typhoons
    val userLat = viewModel.userLat
    val userLon = viewModel.userLon

    var showQuakes by remember { mutableStateOf(true) }
    var showTyphoons by remember { mutableStateOf(true) }
    var magFilter by remember { mutableStateOf(MagFilter.ALL) }

    // Earthquakes actually drawn = layer on AND inside the selected magnitude category.
    val visibleQuakes = remember(quakes, showQuakes, magFilter) {
        if (!showQuakes) emptyList() else quakes.filter { magFilter.matches(it.magnitude) }
    }

    // Keep the picture current while this screen is on top (refresh() also pulls typhoons).
    LaunchedEffect(Unit) {
        viewModel.refresh()
        while (isActive) {
            kotlinx.coroutines.delay(REFRESH_INTERVAL_MS)
            viewModel.refresh()
        }
    }

    val mapHolder = remember { arrayOfNulls<MapLibreMap>(1) }
    val mapReady = remember { mutableStateOf(false) }
    // The currently-focused event drives the bottom detail card. Object-based (not id lookup) so it
    // survives data refreshes and the initialFocus being cleared after it's consumed.
    var focusedQuake by remember { mutableStateOf<Earthquake?>(null) }
    var focusedTyphoon by remember { mutableStateOf<Typhoon?>(null) }
    val hasFocus = focusedQuake != null || focusedTyphoon != null

    val pins = remember(visibleQuakes, showTyphoons, typhoons, userLat, userLon, focusedQuake) {
        val base = buildPins(
            quakes = visibleQuakes,
            typhoons = if (showTyphoons) typhoons else emptyList(),
            userLat = userLat,
            userLon = userLon,
        )
        // Ensure the focused event always has a marker, even if it's older than the 7-day window.
        val ff = focusedQuake
        if (ff != null && visibleQuakes.none { it.id == ff.id }) {
            base + MapPin(
                id = ff.id,
                lat = ff.latitude,
                lon = ff.longitude,
                colorInt = (tierColorInt(ff.magnitude) and 0x00FFFFFF) or 0x99000000.toInt(),
                radiusDp = 7f + ff.magnitude.toFloat() * 2.2f,
            )
        } else base
    }
    val quakeById = remember(visibleQuakes) { visibleQuakes.associateBy { it.id } }
    val typhoonById = remember(typhoons, showTyphoons) {
        (if (showTyphoons) typhoons else emptyList()).associateBy { it.id }
    }

    // When the user picks a magnitude category, frame the full set of those events.
    LaunchedEffect(magFilter, visibleQuakes.size) {
        if (magFilter != MagFilter.ALL) {
            mapHolder[0]?.frameZone(visibleQuakes.map { LatLng(it.latitude, it.longitude) })
        }
    }

    // "View Area" entry: once the map is ready, frame + focus the requested earthquake, then clear it.
    LaunchedEffect(initialFocus?.id, mapReady.value) {
        val f = initialFocus
        if (f != null && mapReady.value) {
            focusedTyphoon = null
            focusedQuake = f
            mapHolder[0]?.frameZone(listOf(LatLng(f.latitude, f.longitude)))
            onFocusConsumed()
        }
    }

    Box(Modifier.fillMaxSize().background(SeismicHot.Base)) {
        MapLibreCanvas(
            pins = pins,
            initialLat = PH_CENTER_LAT,
            initialLon = PH_CENTER_LON,
            initialZoom = PH_OVERVIEW_ZOOM,
            minZoom = 4.0,
            maxZoom = 12.0,
            bounds = PH_BOUNDS,
            showLabels = true,
            modifier = Modifier.fillMaxSize(),
            onMapReady = { mapHolder[0] = it; mapReady.value = true },
            onPinClick = { id ->
                // Tap a marker → focus it + frame its zone + reveal the detail card at the bottom.
                quakeById[id]?.let { eq ->
                    focusedTyphoon = null
                    focusedQuake = eq
                    mapHolder[0]?.frameZone(listOf(LatLng(eq.latitude, eq.longitude)))
                    return@MapLibreCanvas
                }
                typhoonById[id]?.let { t ->
                    focusedQuake = null
                    focusedTyphoon = t
                    mapHolder[0]?.frameZone(listOf(LatLng(t.latitude, t.longitude)))
                }
            },
        )

        CompositionLocalProvider(LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = SeismicFont)) {
          Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.align(Alignment.TopCenter),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TopOverlay(
                    quakeCount = visibleQuakes.size,
                    typhoonCount = if (showTyphoons) typhoons.size else 0,
                    lastUpdated = state.lastUpdated,
                    isLoading = state.isLoading,
                    onBack = onBack,
                    onRefresh = { viewModel.refresh() },
                    onRecenter = {
                        // Reset to the whole-country overview and clear any zone/category focus.
                        magFilter = MagFilter.ALL
                        focusedQuake = null
                        focusedTyphoon = null
                        mapHolder[0]?.animateCamera(
                            org.maplibre.android.camera.CameraUpdateFactory.newCameraPosition(
                                CameraPosition.Builder()
                                    .target(LatLng(PH_CENTER_LAT, PH_CENTER_LON))
                                    .zoom(PH_OVERVIEW_ZOOM)
                                    .build(),
                            ),
                        )
                    },
                )
                LayerChips(
                    showQuakes = showQuakes,
                    showTyphoons = showTyphoons,
                    onToggleQuakes = { showQuakes = !showQuakes },
                    onToggleTyphoons = { showTyphoons = !showTyphoons },
                )
                MagnitudeChips(selected = magFilter, onSelect = { magFilter = it })
            }

            // The legend + weather give way to the focused-event detail card when a marker is tapped.
            if (!hasFocus) {
                Legend(modifier = Modifier.align(Alignment.BottomStart).padding(16.dp))
                WeatherPill(
                    weather = state.liveWeather,
                    pagasa = state.pagasaCyclone,
                    cyclone = state.nearestTyphoon,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                )
            }

            focusedQuake?.let { eq ->
                FocusedQuakeCard(
                    quake = eq,
                    distanceKm = viewModel.distanceTo(eq),
                    onView = { onEarthquakeClick(eq) },
                    onDismiss = { focusedQuake = null },
                    modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(12.dp),
                )
            }
            focusedTyphoon?.let { t ->
                FocusedTyphoonCard(
                    typhoon = t,
                    onTrack = onOpenCyclone,
                    onDismiss = { focusedTyphoon = null },
                    modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(12.dp),
                )
            }

            // Required OSM/OpenFreeMap credit (default MapLibre attribution UI is hidden).
            Text(
                "© OpenStreetMap",
                fontSize = 8.5.sp,
                color = SeismicHot.White.copy(alpha = 0.4f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 6.dp, bottom = 2.dp),
            )
          }
        }
    }
}

// ─── Surfaces ───────────────────────────────────────────────────────────────────
private val cardBg = SeismicHot.Card.copy(alpha = 0.96f)

/** Bottom detail card for a tapped earthquake marker: magnitude, place, meta + open-detail CTA. */
@Composable
private fun FocusedQuakeCard(
    quake: Earthquake,
    distanceKm: Double,
    onView: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = Color(tierColorInt(quake.magnitude))
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(cardBg)
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(18.dp))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                Modifier.size(54.dp).clip(RoundedCornerShape(15.dp)).background(color.copy(alpha = 0.16f))
                    .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(15.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("%.1f".format(quake.magnitude), fontFamily = SeismicDisplayFont, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = color)
                    Text("MAG", fontSize = 7.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = color.copy(alpha = 0.7f))
                }
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(quake.place, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = SeismicHot.White, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    CloseChip(onDismiss)
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MapMeta(Icons.Outlined.Schedule, formatRelativeTime(quake.timeMs))
                    MapMeta(Icons.Outlined.Layers, "${quake.depth.toInt()} km")
                    MapMeta(Icons.Outlined.NearMe, "${distanceKm.roundToInt()} km")
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        GradientMapButton("View Full Report", Icons.Outlined.Bolt, onView)
    }
}

/** Bottom detail card for a tapped typhoon marker: category, wind + track-storm CTA. */
@Composable
private fun FocusedTyphoonCard(
    typhoon: Typhoon,
    onTrack: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = Color(TYPHOON_COLOR)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(cardBg)
            .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(color.copy(alpha = 0.18f))
                    .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.Storm, null, tint = color, modifier = Modifier.size(26.dp)) }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(typhoon.category.label.uppercase(), fontSize = 9.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp, color = color, modifier = Modifier.weight(1f))
                    CloseChip(onDismiss)
                }
                Text(typhoon.name, fontFamily = SeismicDisplayFont, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = SeismicHot.White, maxLines = 1)
                Text(
                    buildString {
                        typhoon.windKph?.let { append("${it.roundToInt()} km/h winds · ") }
                        append(typhoon.alertLevel.label)
                    },
                    fontSize = 11.5.sp, color = SeismicHot.Label,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        GradientMapButton("Track Storm", Icons.Outlined.Storm, onTrack)
    }
}

@Composable
private fun MapMeta(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, null, Modifier.size(13.dp), tint = SeismicHot.Muted)
        Text(text, fontSize = 11.sp, color = SeismicHot.Label)
    }
}

@Composable
private fun CloseChip(onDismiss: () -> Unit) {
    Box(
        Modifier.size(28.dp).clip(CircleShape).background(SeismicHot.Field).clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Outlined.Close, "Dismiss", tint = SeismicHot.Muted, modifier = Modifier.size(16.dp)) }
}

@Composable
private fun GradientMapButton(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth().height(46.dp)
            .clip(RoundedCornerShape(13.dp)).background(SeismicHot.Gradient)
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = SeismicHot.White, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = SeismicHot.White)
    }
}

/**
 * Compact live-weather readout pinned to the map's bottom-right: the current local weather power
 * normally, elevated to the storm name + official PAGASA signal when a cyclone is active.
 */
@Composable
private fun WeatherPill(
    weather: LiveWeather?,
    pagasa: PagasaCyclone?,
    cyclone: Typhoon?,
    modifier: Modifier = Modifier,
) {
    val cycloneActive = pagasa != null || cyclone != null
    if (!cycloneActive && weather == null) return

    val icon: androidx.compose.ui.graphics.vector.ImageVector
    val accent: Color
    val line1: String
    val line2: String
    if (cycloneActive) {
        val signal = pagasa?.highestSignal ?: 0
        icon = Icons.Outlined.Storm
        accent = if (signal >= 3) SeismicHot.Red else TierStrong
        line1 = pagasa?.name ?: cyclone?.name ?: "Cyclone"
        line2 = pagasa?.highestSignal?.let { "Signal $it" } ?: cyclone?.category?.label ?: "Active cyclone"
    } else {
        val w = weather!!
        icon = when (w.power) {
            WeatherPower.CALM -> Icons.Outlined.WbSunny
            WeatherPower.BREEZY, WeatherPower.WINDY -> Icons.Outlined.Air
            WeatherPower.BLUSTERY -> Icons.Outlined.Cloud
            WeatherPower.STORMY, WeatherPower.SEVERE -> Icons.Outlined.Storm
        }
        accent = weatherPowerColor(w.power)
        line1 = w.power.label
        line2 = "${w.windKph.roundToInt()} km/h · ${w.condition}"
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(13.dp))
            .background(cardBg)
            .border(1.dp, SeismicHot.Border, RoundedCornerShape(13.dp))
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Box(
            Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, Modifier.size(18.dp), tint = accent) }
        Column {
            Text(line1, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = SeismicHot.White)
            Text(line2, fontSize = 10.5.sp, color = SeismicHot.Muted)
        }
    }
}

/**
 * Builds the flat list of map pins from the current data: magnitude-tiered earthquake circles,
 * purple typhoon circles (sized up by category), and the user's location dot on top.
 * Pin radii are in dp (screen-space) so they stay legible at every zoom level.
 */
private fun buildPins(
    quakes: List<Earthquake>,
    typhoons: List<Typhoon>,
    userLat: Double,
    userLon: Double,
): List<MapPin> {
    val pins = ArrayList<MapPin>(quakes.size + typhoons.size + 1)

    quakes.forEach { eq ->
        pins.add(
            MapPin(
                id = eq.id,
                lat = eq.latitude,
                lon = eq.longitude,
                // Translucent fill; the canvas opaques the stroke automatically.
                colorInt = (tierColorInt(eq.magnitude) and 0x00FFFFFF) or 0x99000000.toInt(),
                radiusDp = (7f + eq.magnitude.toFloat() * 2.2f),
            ),
        )
    }

    typhoons.forEach { t ->
        pins.add(
            MapPin(
                id = t.id,
                lat = t.latitude,
                lon = t.longitude,
                colorInt = (TYPHOON_COLOR and 0x00FFFFFF) or 0x88000000.toInt(),
                radiusDp = (16f + t.category.ordinal * 4f),
                label = t.name,
            ),
        )
    }

    pins.add(
        MapPin(
            id = "user-location",
            lat = userLat,
            lon = userLon,
            colorInt = USER_COLOR,
            radiusDp = 6f,
        ),
    )
    return pins
}

/**
 * Animates the map to give a full overview of a zone: a single point zooms in to its locality,
 * a set of points fits them all in view with padding. Wrapped so a not-yet-laid-out map no-ops.
 */
private fun MapLibreMap.frameZone(points: List<LatLng>) {
    if (points.isEmpty()) return
    runCatching {
        if (points.size == 1) {
            animateCamera(
                org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(points.first(), 8.0),
            )
        } else {
            val builder = LatLngBounds.Builder()
            points.forEach { builder.include(it) }
            animateCamera(
                org.maplibre.android.camera.CameraUpdateFactory.newLatLngBounds(builder.build(), 120),
            )
        }
    }
}

@Composable
private fun MagnitudeChips(selected: MagFilter, onSelect: (MagFilter) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MagFilter.entries.forEach { f ->
            val accent = magFilterColor(f)
            val isSel = f == selected
            val bg = if (isSel) accent.copy(alpha = 0.20f) else cardBg
            val fg = if (isSel) accent else SeismicHot.Muted
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(bg)
                    .border(1.dp, if (isSel) accent.copy(alpha = 0.5f) else SeismicHot.Border, CircleShape)
                    .clickable { onSelect(f) }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(Modifier.size(9.dp).clip(CircleShape).background(accent))
                Text(f.label, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = fg)
            }
        }
    }
}

private fun magFilterColor(f: MagFilter): Color = when (f) {
    MagFilter.ALL -> SeismicHot.Red
    MagFilter.MINOR -> TierMinor
    MagFilter.LIGHT -> TierLight
    MagFilter.MODERATE -> TierModerate
    MagFilter.STRONG -> TierStrong
    MagFilter.MAJOR -> TierMajor
}

@Composable
private fun TopOverlay(
    quakeCount: Int,
    typhoonCount: Int,
    lastUpdated: String,
    isLoading: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onRecenter: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OverlayButton(Icons.Outlined.ArrowBack, "Back", onBack)

        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(13.dp))
                .background(cardBg)
                .border(1.dp, SeismicHot.Border, RoundedCornerShape(13.dp))
                .padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            LivePulse()
            Column {
                Text(
                    "Live · $quakeCount quakes · $typhoonCount typhoons",
                    fontFamily = SeismicDisplayFont,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.2).sp,
                    color = SeismicHot.White,
                )
                Text(
                    if (isLoading) "Updating…"
                    else if (lastUpdated.isNotBlank()) "Updated $lastUpdated"
                    else "Auto-refresh on",
                    fontSize = 10.sp,
                    color = SeismicHot.Muted,
                )
            }
        }

        OverlayButton(Icons.Outlined.MyLocation, "Center on Philippines", onRecenter)
        OverlayButton(Icons.Outlined.Refresh, "Refresh", onRefresh)
    }
}

@Composable
private fun LayerChips(
    showQuakes: Boolean,
    showTyphoons: Boolean,
    onToggleQuakes: () -> Unit,
    onToggleTyphoons: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LayerChip(Icons.Outlined.Sensors, "Earthquakes", showQuakes, SeismicHot.Orange, onToggleQuakes)
        LayerChip(Icons.Outlined.Storm, "Typhoons", showTyphoons, Color(TYPHOON_COLOR), onToggleTyphoons)
    }
}

@Composable
private fun LayerChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    val bg = if (selected) accent.copy(alpha = 0.20f) else cardBg
    val fg = if (selected) accent else SeismicHot.Muted
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(bg)
            .border(1.dp, if (selected) accent.copy(alpha = 0.5f) else SeismicHot.Border, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, null, Modifier.size(16.dp), tint = fg)
        Text(label, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = fg)
    }
}

@Composable
private fun OverlayButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    desc: String,
    onClick: () -> Unit,
) {
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
private fun LivePulse() {
    val transition = rememberInfiniteTransition(label = "live")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "alpha",
    )
    Box(
        Modifier
            .size(9.dp)
            .clip(CircleShape)
            .background(SeismicHot.Red.copy(alpha = alpha)),
    )
}

@Composable
private fun Legend(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(13.dp))
            .background(cardBg)
            .border(1.dp, SeismicHot.Border, RoundedCornerShape(13.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        LegendRow(TierMinor, "< 4 minor")
        LegendRow(TierLight, "4 – 5 light")
        LegendRow(TierModerate, "5 – 6 moderate")
        LegendRow(TierStrong, "6 – 7 strong")
        LegendRow(TierMajor, "≥ 7 major")
        LegendRow(Color(TYPHOON_COLOR), "typhoon")
    }
}

@Composable
private fun LegendRow(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Text(label, fontSize = 11.sp, color = SeismicHot.Label)
    }
}

/** ARGB color for an event's magnitude tier (mirrors the home/detail palette). */
private fun tierColorInt(mag: Double): Int = when {
    mag < 4.0 -> 0xFF22C55E.toInt()
    mag < 5.0 -> 0xFFF5C518.toInt()
    mag < 6.0 -> 0xFFFF6A00.toInt()
    mag < 7.0 -> 0xFFFF4D2D.toInt()
    else -> 0xFFFF2D55.toInt()
}
