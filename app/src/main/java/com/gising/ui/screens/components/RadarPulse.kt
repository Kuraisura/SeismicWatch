package com.gising.ui.screens.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Decelerating expansion so each ring leaves the center fast and eases out at the rim. */
private val RingDecelerate = CubicBezierEasing(0f, 0f, 0.2f, 1f)

/**
 * Concentric "radar" pulse used on the home screen. Expanding rings ripple outward from the
 * center over a [periodMillis] cycle — drive that from event recency so a fresh quake visibly
 * pulses faster than a stale one. [accent] drives the color, [sweep] adds a rotating radar
 * comet for active/severe events, and [content] is drawn centered on top.
 */
@Composable
fun RadarPulse(
    accent: Color,
    modifier: Modifier = Modifier,
    diameter: Dp = 260.dp,
    ringCount: Int = 3,
    periodMillis: Int = 2600,
    sweep: Boolean = false,
    content: @Composable () -> Unit,
) {
    val transition = rememberInfiniteTransition(label = "radar")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(periodMillis.coerceAtLeast(600), easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "radar-progress",
    )
    val sweepAngle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(periodMillis.coerceAtLeast(600) * 2, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "radar-sweep",
    )

    Box(modifier = modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(diameter)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val maxRadius = size.minDimension / 2f

            // Static backdrop rings.
            val staticRings = 4
            for (i in 1..staticRings) {
                val r = maxRadius * (i / staticRings.toFloat())
                drawCircle(
                    color = accent.copy(alpha = 0.08f),
                    radius = r,
                    center = center,
                    style = Stroke(width = 1f),
                )
            }

            // Rotating radar comet — a faint angular trail that points the sweep around the dial.
            if (sweep) {
                rotate(degrees = sweepAngle, pivot = center) {
                    drawArc(
                        brush = Brush.sweepGradient(
                            0.0f to Color.Transparent,
                            0.78f to Color.Transparent,
                            0.97f to accent.copy(alpha = 0.05f),
                            1.0f to accent.copy(alpha = 0.22f),
                            center = center,
                        ),
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = true,
                        topLeft = Offset(center.x - maxRadius, center.y - maxRadius),
                        size = Size(maxRadius * 2f, maxRadius * 2f),
                    )
                }
            }

            // Animated expanding pulse rings, evenly phase-offset, decelerating toward the rim.
            for (i in 0 until ringCount) {
                val phase = (progress + i.toFloat() / ringCount) % 1f
                val radius = maxRadius * RingDecelerate.transform(phase)
                val alpha = (1f - phase) * 0.5f
                drawCircle(
                    color = accent.copy(alpha = alpha),
                    radius = radius,
                    center = center,
                    style = Stroke(width = 2.5f),
                )
            }

            // Soft inner glow.
            drawCircle(
                color = accent.copy(alpha = 0.10f),
                radius = maxRadius * 0.42f,
                center = center,
            )
        }
        content()
    }
}
