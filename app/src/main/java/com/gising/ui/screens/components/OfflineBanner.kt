package com.gising.ui.screens.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gising.ui.theme.SeismicFont
import com.gising.ui.theme.SeismicHot

/**
 * "Offline Mode" notice shown when the device has no validated internet path. Its own look — an amber
 * signal-lost card with a dashed left rail, a pulsing cloud-off glyph and a two-line caption — so it
 * reads as a distinct system state, not just another alert card. Animates in/out as signal drops and
 * returns. Tells the user they're browsing saved reports only (no new earthquakes/typhoons arrive).
 */
@Composable
fun OfflineBanner(visible: Boolean, modifier: Modifier = Modifier) {
    val amber = SeismicHot.Yellow
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(220)) + expandVertically(tween(260)),
        exit = fadeOut(tween(160)) + shrinkVertically(tween(200)),
        modifier = modifier,
    ) {
        val pulse = rememberInfiniteTransition(label = "offline")
        val glow by pulse.animateFloat(
            initialValue = 0.35f, targetValue = 0.9f,
            animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
            label = "offlineGlow",
        )
        Column {
          Row(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(amber.copy(alpha = 0.14f), SeismicHot.Card.copy(alpha = 0.6f)),
                    ),
                )
                .border(1.dp, amber.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                // Dashed-looking accent rail on the leading edge.
                .drawBehind {
                    val railW = 3.dp.toPx()
                    var y = 4.dp.toPx()
                    val seg = 7.dp.toPx()
                    while (y < size.height - 4.dp.toPx()) {
                        drawRoundRect(
                            color = amber,
                            topLeft = androidx.compose.ui.geometry.Offset(0f, y),
                            size = androidx.compose.ui.geometry.Size(railW, seg),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(railW),
                        )
                        y += seg * 2
                    }
                }
                .padding(start = 14.dp, end = 14.dp, top = 11.dp, bottom = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(amber.copy(alpha = 0.10f + glow * 0.14f))
                    .border(1.dp, amber.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.CloudOff, "Offline", tint = amber, modifier = Modifier.size(18.dp))
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(
                        "OFFLINE MODE",
                        color = amber,
                        fontFamily = SeismicFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        letterSpacing = 1.sp,
                    )
                    Box(Modifier.size(6.dp).clip(CircleShape).background(amber.copy(alpha = glow)))
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    "No connection — showing saved reports. New earthquakes & typhoons resume when you reconnect.",
                    color = SeismicHot.Label,
                    fontFamily = SeismicFont,
                    fontSize = 11.5.sp,
                    lineHeight = 15.sp,
                )
            }
          }
          Spacer(Modifier.height(16.dp))
        }
    }
}
