package com.gising.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gising.R

/**
 * Shared "SeismicWatch · Tactical Vibrant" design tokens — the bold red/orange dark system used by the
 * Auth, Home, Reports and Notifications screens. Kept separate from the app-wide Material theme so
 * these high-urgency surfaces can opt in without re-tinting the rest of the app.
 */

// Bundled brand typefaces shipped in res/font.
//
// Pairing — Clash Grotesk (display) + Satoshi (body):
//   • ClashDisplay → screen titles, hero text and big numeric readouts. A bold geometric
//     display face that carries the "Tactical Vibrant" urgency.
//   • Satoshi → body, labels, inputs and everything else. A clean, highly legible workhorse.
val ClashDisplay = FontFamily(
    Font(R.font.clash_grotesk_light, FontWeight.Light),
    Font(R.font.clash_grotesk_regular, FontWeight.Normal),
    Font(R.font.clash_grotesk_medium, FontWeight.Medium),
    Font(R.font.clash_grotesk_semibold, FontWeight.SemiBold),
    Font(R.font.clash_grotesk_bold, FontWeight.Bold),
)

val Satoshi = FontFamily(
    Font(R.font.satoshi_light, FontWeight.Light),
    Font(R.font.satoshi_regular, FontWeight.Normal),
    Font(R.font.satoshi_medium, FontWeight.Medium),
    Font(R.font.satoshi_bold, FontWeight.Bold),
    Font(R.font.satoshi_black, FontWeight.Black),
)

// Screen-wide default body family for the tactical surfaces (provided via LocalTextStyle).
val SeismicFont = Satoshi

// Display family for titles / hero text / big numbers on the tactical surfaces.
val SeismicDisplayFont = ClashDisplay

/**
 * Live design tokens for the tactical surfaces. Backed by a snapshot-state [SeismicPalette] so that
 * when the user switches theme ([SeismicWatchTheme] calls [apply]), every composable that reads one
 * of these properties recomposes with the new color — no per-screen edits needed.
 *
 * `Red`/`Orange` are the brand accent pair (they re-theme per palette — e.g. blue in Deep Ocean);
 * genuine data severity is the separate [SeismicColors] magnitude ramp + [SeismicHot.Critical].
 */
object SeismicHot {
    var palette by mutableStateOf(SeismicPalettes.paletteFor(AppTheme.DEFAULT, dark = true))
        private set

    /** Swap the active palette (called by the theme layer on theme/mode change). */
    fun apply(newPalette: SeismicPalette) { palette = newPalette }

    val Base: Color get() = palette.base
    val Card: Color get() = palette.card
    val Field: Color get() = palette.field
    val Nav: Color get() = palette.nav
    val Border: Color get() = palette.border
    val Red: Color get() = palette.accent          // brand accent (re-themes per palette)
    val Orange: Color get() = palette.accentAlt     // secondary accent
    val Yellow: Color get() = palette.moderate
    val Label: Color get() = palette.label
    val Muted: Color get() = palette.muted
    val White: Color get() = palette.white
    val Safe: Color get() = palette.safe
    val Signal: Color get() = palette.signal
    val Grid: Color get() = palette.accent.copy(alpha = 0.04f)

    // Explicit semantic accessors for callers that want them by intent.
    val Accent: Color get() = palette.accent
    val AccentAlt: Color get() = palette.accentAlt
    val Critical: Color get() = palette.critical
    val High: Color get() = palette.high

    val Gradient: Brush
        get() = Brush.linearGradient(
            colors = listOf(palette.accent, palette.accentAlt),
            start = Offset(0f, 0f),
            end = Offset(800f, 300f),
        )
}

/**
 * The tactical ground: obsidian base, top-down radial ember glow and a 28dp seismic grid.
 *
 * Drawn ONCE via [drawWithCache] (the brush + grid geometry are built per-size, not per-frame) so it
 * adds zero continuous render cost — important for smooth input on older devices. The old drifting
 * scanline was removed: it forced this whole layer (radial-gradient shader + grid loop) to redraw
 * ~60×/second on every screen, which starved touch handling. Motion now comes only from the cheap,
 * GPU-composited blinking dots / pulses.
 */
fun Modifier.seismicTacticalBackground(): Modifier =
    this
        .background(SeismicHot.Base)
        .drawWithCache {
            val w = size.width; val h = size.height
            val glow = Brush.radialGradient(
                listOf(SeismicHot.Red.copy(alpha = 0.18f), Color.Transparent),
                Offset(w / 2f, 0f), 320.dp.toPx(),
            )
            val cell = 28.dp.toPx()
            onDrawBehind {
                drawRect(glow, size = Size(w, h))
                var x = 0f
                while (x <= w) { drawLine(SeismicHot.Grid, Offset(x, 0f), Offset(x, h), 1f); x += cell }
                var y = 0f
                while (y <= h) { drawLine(SeismicHot.Grid, Offset(0f, y), Offset(w, y), 1f); y += cell }
            }
        }

/** Staggered fade-up entrance: slides up 18dp and fades in once [visible], after [delayMillis]. */
@Composable
fun Modifier.seismicEntrance(visible: Boolean, delayMillis: Int): Modifier {
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(420, delayMillis = delayMillis),
        label = "entranceAlpha",
    )
    val translateY by animateFloatAsState(
        targetValue = if (visible) 0f else 18f,
        animationSpec = tween(480, delayMillis = delayMillis, easing = FastOutSlowInEasing),
        label = "entranceY",
    )
    return this.graphicsLayer { this.alpha = alpha; this.translationY = translateY }
}

/** A small dot that blinks between 0.3 and 1.0 alpha on a steady 0.9s loop. */
@Composable
fun SeismicBlinkingDot(color: Color, size: Dp, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "blink")
    val a by transition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "blinkAlpha",
    )
    androidx.compose.foundation.layout.Box(
        modifier.size(size).graphicsLayer { alpha = a }.clip(CircleShape).background(color),
    )
}

/** The "MONITORING ACTIVE" live pill with a blinking dot. */
@Composable
fun SeismicLivePill(text: String = "MONITORING ACTIVE", modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(SeismicHot.Red.copy(alpha = 0.12f))
            .border(1.dp, SeismicHot.Red.copy(alpha = 0.3f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        SeismicBlinkingDot(SeismicHot.Red, 6.dp)
        androidx.compose.material3.Text(
            text,
            fontFamily = SeismicDisplayFont,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            color = SeismicHot.Label,
        )
    }
}
