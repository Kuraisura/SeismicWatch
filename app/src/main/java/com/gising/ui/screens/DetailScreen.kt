package com.gising.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gising.data.model.*
import com.gising.ui.theme.SeismicDisplayFont
import com.gising.ui.theme.SeismicFont
import com.gising.ui.theme.SeismicHot
import com.gising.ui.theme.seismicTacticalBackground
import java.text.SimpleDateFormat
import java.util.*

/**
 * Earthquake Report — "SeismicWatch · Tactical Vibrant".
 *
 * Restyled to the app-wide obsidian / red→orange ground with Clash Grotesk titles + Satoshi body:
 * an OpenFreeMap dark mini-map of the epicenter, a safety verdict, quick actions, the weekly
 * frequency strip, a "did you feel it?" prompt, and the technical detail block (stats grid, MMI
 * scale). All data comes from the live feeds via [MainViewModel].
 */
@Composable
fun DetailScreen(
    earthquake: Earthquake,
    viewModel: MainViewModel,
    onViewArea: (Earthquake) -> Unit = {},
    onBack: () -> Unit,
) {
    val prediction = remember(earthquake.id) { viewModel.computePrediction(earthquake) }
    val tier = MagnitudeTier.from(earthquake.magnitude)
    val tierColor = magnitudeTierColor(tier)
    val context = androidx.compose.ui.platform.LocalContext.current
    val weeklyCount = remember(earthquake.id) { viewModel.weeklyCountNear(earthquake) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .seismicTacticalBackground(),
    ) {
        CompositionLocalProvider(LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = SeismicFont)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 640.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                // ── Map header with magnitude overlay + back button ─────────────
                MapHeader(
                    earthquake = earthquake,
                    tier = tier,
                    tierColor = tierColor,
                    distanceKm = prediction.distanceKm,
                    onBack = onBack,
                )

                Spacer(Modifier.height(18.dp))

                // ── Safety verdict ──────────────────────────────────────────────
                SafetyVerdictCard(prediction = prediction)
                Spacer(Modifier.height(12.dp))

                // ── View Area / Share ───────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlineAction(
                        text = "View Area",
                        icon = Icons.Outlined.NearMe,
                        accent = SeismicHot.Red,
                        onClick = { onViewArea(earthquake) },
                        modifier = Modifier.weight(1f),
                    )
                    OutlineAction(
                        text = "Share",
                        icon = Icons.Outlined.Share,
                        accent = SeismicHot.White,
                        onClick = { shareReport(context, earthquake) },
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(16.dp))

                // ── Weekly frequency strip ──────────────────────────────────────
                WeeklyCountStrip(count = weeklyCount, place = earthquake.place)
                Spacer(Modifier.height(16.dp))

                // ── Did you feel it? ────────────────────────────────────────────
                FeelReportCard(onReport = { openFeltReport(context, earthquake) })
                Spacer(Modifier.height(24.dp))

                // ── Technical detail ────────────────────────────────────────────
                Text(
                    "TECHNICAL DETAIL",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    color = SeismicHot.Muted,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                Spacer(Modifier.height(12.dp))

                StatsGrid(earthquake = earthquake, prediction = prediction)
                Spacer(Modifier.height(16.dp))
                MmiScaleBar(mmi = prediction.estimatedMMI)

                Spacer(Modifier.height(36.dp))
            }
        }
    }
}

// ─── Map Header ─────────────────────────────────────────────────────────────────
@Composable
private fun MapHeader(
    earthquake: Earthquake,
    tier: MagnitudeTier,
    tierColor: Color,
    distanceKm: Double,
    onBack: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp)
            .background(SeismicHot.Card),
    ) {
        // OpenFreeMap dark mini-map centered on the epicenter, with a severity-tiered marker.
        com.gising.ui.screens.components.MapLibreCanvas(
            pins = listOf(
                com.gising.ui.screens.components.MapPin(
                    id = earthquake.id,
                    lat = earthquake.latitude,
                    lon = earthquake.longitude,
                    colorInt = (tierColorInt(earthquake.magnitude) and 0x00FFFFFF) or 0x99000000.toInt(),
                    radiusDp = 8f + earthquake.magnitude.toFloat() * 2.5f,
                ),
            ),
            initialLat = earthquake.latitude,
            initialLon = earthquake.longitude,
            initialZoom = 6.5,
            minZoom = 3.0,
            maxZoom = 12.0,
            modifier = Modifier.fillMaxSize(),
        )

        // Bottom scrim so the overlay text stays legible over the map.
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.45f to Color.Transparent,
                        1f to SeismicHot.Base.copy(alpha = 0.92f),
                    ),
                ),
        )

        // Back button over the map.
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(start = 14.dp, top = 10.dp)
                .size(42.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(SeismicHot.Base.copy(alpha = 0.7f))
                .border(1.dp, SeismicHot.Border, RoundedCornerShape(13.dp))
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.ArrowBack, "Back", tint = SeismicHot.White, modifier = Modifier.size(20.dp))
        }

        // Minimal attribution (OSM / OpenFreeMap credit required; default UI is hidden).
        Text(
            "© OpenStreetMap",
            fontSize = 8.5.sp,
            color = SeismicHot.White.copy(alpha = 0.45f),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 16.dp, end = 12.dp),
        )

        // Magnitude + tier overlay.
        Column(modifier = Modifier.align(Alignment.BottomStart).padding(20.dp)) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    String.format("%.1f", earthquake.magnitude),
                    fontFamily = SeismicDisplayFont,
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-2).sp,
                    color = tierColor,
                )
                Text(
                    tier.label.uppercase(),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    color = tierColor,
                    modifier = Modifier
                        .padding(bottom = 10.dp)
                        .clip(CircleShape)
                        .background(tierColor.copy(alpha = 0.16f))
                        .border(1.dp, tierColor.copy(alpha = 0.4f), CircleShape)
                        .padding(horizontal = 9.dp, vertical = 3.dp),
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                earthquake.place,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = SeismicHot.White,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                HeaderMeta(Icons.Outlined.Schedule, formatRelativeTime(earthquake.timeMs))
                HeaderMeta(Icons.Outlined.Layers, "${String.format("%.0f", earthquake.depth)} km deep")
                HeaderMeta(Icons.Outlined.NearMe, "${String.format("%.0f", distanceKm)} km away")
            }
        }
    }
}

@Composable
private fun HeaderMeta(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, null, Modifier.size(13.dp), tint = SeismicHot.Label)
        Text(text, fontSize = 11.sp, color = SeismicHot.Label)
    }
}

private fun tierColorInt(mag: Double): Int = when {
    mag < 4.0 -> 0xFF22C55E.toInt()
    mag < 5.0 -> 0xFFF5C518.toInt()
    mag < 6.0 -> 0xFFFF6A00.toInt()
    mag < 7.0 -> 0xFFFF4D2D.toInt()
    else -> 0xFFFF2D55.toInt()
}

// ─── Safety verdict ──────────────────────────────────────────────────────────────
@Composable
private fun SafetyVerdictCard(prediction: ShakingPrediction) {
    val safe = prediction.safetyAction == SafetyAction.NO_ACTION
    val accent = if (safe) SeismicHot.Safe else SeismicHot.Orange
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.14f), accent.copy(alpha = 0.05f))))
            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier.size(46.dp).clip(RoundedCornerShape(13.dp)).background(accent.copy(alpha = 0.18f))
                .border(1.dp, accent.copy(alpha = 0.4f), RoundedCornerShape(13.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (safe) Icons.Outlined.VerifiedUser else Icons.Outlined.Warning,
                null, tint = accent, modifier = Modifier.size(24.dp),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                if (safe) "You Are Safe" else prediction.safetyAction.instruction,
                fontFamily = SeismicDisplayFont,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = accent,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                if (safe)
                    "This earthquake is ${String.format("%.0f", prediction.distanceKm)} km from you — too far to affect you. No action needed."
                else
                    "Estimated intensity MMI ${String.format("%.1f", prediction.estimatedMMI)} at your location.",
                fontSize = 12.5.sp,
                color = SeismicHot.Label,
            )
        }
    }
}

// ─── Outline action button ───────────────────────────────────────────────────────
@Composable
private fun OutlineAction(
    text: String,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(50.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(SeismicHot.Card)
            .border(1.dp, if (accent == SeismicHot.White) SeismicHot.Border else accent.copy(alpha = 0.5f), RoundedCornerShape(13.dp))
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(17.dp), tint = accent)
        Spacer(Modifier.width(8.dp))
        Text(text, color = accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ─── Weekly count strip ──────────────────────────────────────────────────────────
@Composable
private fun WeeklyCountStrip(count: Int, place: String) {
    val region = place.substringAfterLast(", ").ifBlank { place }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(SeismicHot.Card)
            .border(1.dp, SeismicHot.Border, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(SeismicHot.Orange.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Outlined.ShowChart, null, tint = SeismicHot.Orange, modifier = Modifier.size(18.dp)) }
        Text(
            "This is the ${ordinal(count.coerceAtLeast(1))} earthquake near $region this week",
            fontSize = 12.5.sp,
            color = SeismicHot.White,
        )
    }
}

private fun ordinal(n: Int): String {
    val suffix = if (n % 100 in 11..13) "th" else when (n % 10) {
        1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th"
    }
    return "$n$suffix"
}

// ─── Did you feel it? ────────────────────────────────────────────────────────────
@Composable
private fun FeelReportCard(onReport: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(SeismicHot.Card)
            .border(1.dp, SeismicHot.Border, RoundedCornerShape(16.dp))
            .clickable(onClick = onReport)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(SeismicHot.Red.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Outlined.Feedback, null, tint = SeismicHot.Red, modifier = Modifier.size(20.dp)) }
        Column(Modifier.weight(1f)) {
            Text("Did you feel this earthquake?", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = SeismicHot.White)
            Text("Tap to report — helps improve local alerts", fontSize = 12.sp, color = SeismicHot.Muted)
        }
        Icon(Icons.Outlined.ChevronRight, null, tint = SeismicHot.Muted)
    }
}

// ─── Intent helpers ──────────────────────────────────────────────────────────────
private fun shareReport(context: android.content.Context, eq: Earthquake) {
    val text = "SeismicWatch — M${String.format("%.1f", eq.magnitude)} earthquake ${eq.place}. " +
        "https://maps.google.com/?q=${eq.latitude},${eq.longitude}"
    val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(android.content.Intent.EXTRA_TEXT, text)
    }
    runCatching { context.startActivity(android.content.Intent.createChooser(send, "Share report")) }
}

private fun openFeltReport(context: android.content.Context, eq: Earthquake) {
    val uri = android.net.Uri.parse("https://earthquake.usgs.gov/earthquakes/eventpage/${eq.id}/tellus")
    runCatching {
        context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, uri))
    }
}


// ─── Stats Grid ───────────────────────────────────────────────────────────────
@Composable
private fun StatsGrid(earthquake: Earthquake, prediction: ShakingPrediction) {
    val stats = listOf(
        Triple("Depth", "${String.format("%.1f", earthquake.depth)} km", Icons.Outlined.Layers),
        Triple("Distance", "${String.format("%.0f", prediction.distanceKm)} km", Icons.Outlined.MyLocation),
        Triple("Est. MMI", String.format("%.1f", prediction.estimatedMMI), Icons.Outlined.GraphicEq),
        Triple("Peak G", String.format("%.4f g", prediction.peakGroundAccel), Icons.Outlined.Speed),
        Triple("Tsunami", if (earthquake.tsunami == 1) "Warning" else "None", Icons.Outlined.Water),
        Triple("Status", earthquake.status.replaceFirstChar { it.uppercase() }, Icons.Outlined.Info),
    )

    Column(
        modifier = Modifier.padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        stats.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (label, value, icon) ->
                    StatCard(label = label, value = value, icon = icon, modifier = Modifier.weight(1f))
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(13.dp))
            .background(SeismicHot.Card)
            .border(1.dp, SeismicHot.Border, RoundedCornerShape(13.dp))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, null, modifier = Modifier.size(14.dp), tint = SeismicHot.Muted)
            Text(label.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp, color = SeismicHot.Muted)
        }
        Spacer(Modifier.height(8.dp))
        Text(value, fontFamily = SeismicDisplayFont, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = SeismicHot.White)
    }
}

// ─── MMI Scale Bar ─────────────────────────────────────────────────────────────
@Composable
private fun MmiScaleBar(mmi: Double) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(SeismicHot.Card)
            .border(1.dp, SeismicHot.Border, RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {
        Text("INTENSITY AT YOUR LOCATION", fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = SeismicHot.Muted)
        Spacer(Modifier.height(14.dp))

        val progress = (mmi / 12f).toFloat().coerceIn(0f, 1f)
        val mmiColor = when {
            mmi < 3 -> SeismicHot.Safe
            mmi < 5 -> SeismicHot.Yellow
            mmi < 7 -> SeismicHot.Orange
            mmi < 9 -> Color(0xFFFF4D2D)
            else -> SeismicHot.Red
        }

        // Custom gradient track.
        Box(
            Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(CircleShape)
                .background(SeismicHot.Field),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(progress)
                    .height(10.dp)
                    .clip(CircleShape)
                    .background(Brush.horizontalGradient(listOf(mmiColor.copy(alpha = 0.7f), mmiColor))),
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Not felt", fontSize = 10.sp, color = SeismicHot.Muted)
            Text("MMI ${String.format("%.1f", mmi)}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = mmiColor)
            Text("Catastrophic", fontSize = 10.sp, color = SeismicHot.Muted)
        }
        Spacer(Modifier.height(8.dp))
        Text(mmiDescription(mmi), fontSize = 12.sp, color = SeismicHot.Label)
    }
}

// ─── Helpers ──────────────────────────────────────────────────────────────────
fun formatFullTime(epochMs: Long): String {
    val sdf = SimpleDateFormat("MMM d, yyyy  HH:mm:ss z", Locale.getDefault())
    return sdf.format(Date(epochMs))
}

fun mmiDescription(mmi: Double): String = when {
    mmi < 2 -> "Not felt. Detected only by instruments."
    mmi < 3 -> "Weak. Felt by people at rest indoors."
    mmi < 4 -> "Light. Felt by most people indoors."
    mmi < 5 -> "Moderate. Felt by all, windows rattle."
    mmi < 6 -> "Strong. Many frightened. Minor damage."
    mmi < 7 -> "Very strong. Difficult to stand. Significant damage."
    mmi < 8 -> "Severe. Damage to well-built structures."
    mmi < 9 -> "Violent. Partial collapse of ordinary buildings."
    mmi < 10 -> "Extreme. Many structures destroyed."
    else -> "Catastrophic. Most structures destroyed."
}
