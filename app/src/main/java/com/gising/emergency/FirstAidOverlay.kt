package com.gising.emergency

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.sin

/**
 * Fully-offline, swipeable first-aid cards. Shown as a full-screen dialog over the panic UI so a
 * user can act on critical guidance in seconds. Each card pairs big numbered steps with a small
 * looping Canvas animation that conveys the motion (compression rhythm, pressure, duck-and-cover)
 * faster than words. Zero network — all content from [FirstAidContent].
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FirstAidOverlay(onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val cards = FirstAidContent.cards
        val pager = rememberPagerState(pageCount = { cards.size })

        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xFF0D0F12)),
        ) {
            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                AidCardView(cards[page])
            }

            // Close button.
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .background(Color.White.copy(alpha = 0.1f), CircleShape),
            ) {
                Icon(Icons.Filled.Close, "Close", tint = Color.White)
            }

            // Page dots.
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                repeat(cards.size) { i ->
                    val active = i == pager.currentPage
                    Box(
                        Modifier
                            .size(if (active) 10.dp else 7.dp)
                            .clip(CircleShape)
                            .background(if (active) cards[pager.currentPage].accent else Color.White.copy(0.3f)),
                    )
                }
            }
        }
    }
}

@Composable
private fun AidCardView(card: FirstAidCard) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .padding(top = 64.dp, bottom = 60.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(card.accent.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(card.icon, null, tint = card.accent, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.size(14.dp))
            Column {
                Text(card.title, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text(card.subtitle, color = Color.White.copy(0.6f), fontSize = 13.sp)
            }
        }

        Spacer(Modifier.height(20.dp))

        // Looping illustrative animation.
        Surface(
            color = card.accent.copy(alpha = 0.08f),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2.2f),
        ) {
            AidAnimation(card.anim, card.accent)
        }

        Spacer(Modifier.height(20.dp))

        // Numbered steps.
        card.steps.forEachIndexed { i, step ->
            Row(Modifier.padding(vertical = 7.dp)) {
                Box(
                    Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(card.accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("${i + 1}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.size(12.dp))
                Text(
                    step,
                    color = Color.White.copy(0.92f),
                    fontSize = 16.sp,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
            }
        }
    }
}

/** Small looping Canvas animations — purely decorative motion cues, no assets needed. */
@Composable
private fun AidAnimation(anim: AidAnim, accent: Color) {
    val transition = rememberInfiniteTransition(label = "aid")
    val t by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
        label = "t",
    )

    Canvas(Modifier.fillMaxSize().padding(16.dp)) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val pulse = (sin(t * 2 * Math.PI).toFloat() + 1f) / 2f // 0..1..0
        when (anim) {
            AidAnim.COMPRESSION, AidAnim.BREATH -> {
                // Beating heart / breathing ring: radius pulses with the rhythm.
                val r = size.minDimension / 4f * (0.7f + 0.3f * pulse)
                drawCircle(accent.copy(alpha = 0.25f), radius = r * 1.5f, center = Offset(cx, cy))
                drawCircle(accent, radius = r, center = Offset(cx, cy))
            }
            AidAnim.PRESSURE, AidAnim.TOURNIQUET -> {
                // A band tightening: two converging arrows / a shrinking gap.
                val gap = size.width / 3f * (1f - 0.5f * pulse)
                val y = cy
                drawLine(accent, Offset(cx - gap, y), Offset(cx - 12f, y), strokeWidth = 14f, cap = StrokeCap.Round)
                drawLine(accent, Offset(cx + 12f, y), Offset(cx + gap, y), strokeWidth = 14f, cap = StrokeCap.Round)
                drawCircle(accent.copy(alpha = 0.3f), radius = 18f, center = Offset(cx, cy))
            }
            AidAnim.DUCK_COVER -> {
                // Expanding protective rings (shelter pulse).
                for (i in 0 until 3) {
                    val phase = (t + i / 3f) % 1f
                    drawCircle(
                        accent.copy(alpha = (1f - phase) * 0.5f),
                        radius = size.minDimension / 2f * phase,
                        center = Offset(cx, cy),
                        style = Stroke(width = 4f),
                    )
                }
                drawCircle(accent, radius = 14f, center = Offset(cx, cy))
            }
            AidAnim.COOL -> {
                // Falling "water" droplets cooling a burn.
                for (i in 0 until 4) {
                    val phase = (t + i / 4f) % 1f
                    val x = cx - size.width / 4f + i * size.width / 6f
                    drawCircle(accent, radius = 6f, center = Offset(x, size.height * phase))
                }
            }
        }
    }
}
