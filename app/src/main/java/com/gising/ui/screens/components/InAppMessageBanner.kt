package com.gising.ui.screens.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gising.ui.theme.SeismicFont
import com.gising.ui.theme.SeismicHot
import kotlinx.coroutines.delay

/** Payload for the heads-up chat banner. */
data class MessageBannerData(
    val peerUid: String,
    val name: String,
    val text: String,
)

private const val AUTO_DISMISS_MS = 4500L

/**
 * A Messenger-style heads-up notification that drops in over the whole app when a chat message
 * arrives while you're elsewhere. Its own identity — a dark glass pill with a brand-gradient avatar
 * ring, a live "NOW" pulse tag and a chat glyph — so it's unmistakably an incoming message and not a
 * hazard alert. Tap to open the conversation; it also auto-dismisses after a few seconds.
 */
@Composable
fun InAppMessageBanner(
    data: MessageBannerData?,
    onOpen: (MessageBannerData) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Auto-dismiss timer, restarted whenever a new message replaces the current one.
    LaunchedEffect(data) {
        if (data != null) {
            delay(AUTO_DISMISS_MS)
            onDismiss()
        }
    }

    AnimatedVisibility(
        visible = data != null,
        enter = slideInVertically(tween(320)) { -it } + fadeIn(tween(260)),
        exit = slideOutVertically(tween(240)) { -it } + fadeOut(tween(200)),
        modifier = modifier,
    ) {
        val shown = data
        Column(Modifier.statusBarsPadding()) {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .fillMaxWidth()
                    .widthIn(max = 620.dp)
                    .shadow(18.dp, RoundedCornerShape(20.dp), spotColor = SeismicHot.Red, ambientColor = SeismicHot.Orange)
                    .clip(RoundedCornerShape(20.dp))
                    .background(SeismicHot.Nav)
                    .border(1.dp, SeismicHot.Border, RoundedCornerShape(20.dp))
                    .clickable { shown?.let(onOpen) }
                    .padding(horizontal = 12.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Gradient-ringed avatar with initials.
                Box(contentAlignment = Alignment.Center) {
                    Box(
                        Modifier.size(46.dp).clip(CircleShape).background(SeismicHot.Gradient),
                    )
                    Box(
                        Modifier.size(41.dp).clip(CircleShape).background(SeismicHot.Nav),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            initialsOf(shown?.name.orEmpty()),
                            color = SeismicHot.White,
                            fontFamily = SeismicFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                        )
                    }
                    // Little chat glyph badge, bottom-end.
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(SeismicHot.Base)
                            .padding(2.dp)
                            .clip(CircleShape)
                            .background(SeismicHot.Gradient),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Chat, null,
                            tint = SeismicHot.White, modifier = Modifier.size(9.dp),
                        )
                    }
                }

                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text(
                            shown?.name.orEmpty().ifBlank { "New message" },
                            color = SeismicHot.White,
                            fontFamily = SeismicFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        // "NOW" pulse tag.
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(SeismicHot.Red.copy(alpha = 0.16f))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Box(Modifier.size(5.dp).clip(CircleShape).background(SeismicHot.Red))
                            Text("NOW", color = SeismicHot.Red, fontFamily = SeismicFont, fontWeight = FontWeight.Bold, fontSize = 9.sp, letterSpacing = 0.8.sp)
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        shown?.text.orEmpty().ifBlank { "sent you a message" },
                        color = SeismicHot.Label,
                        fontFamily = SeismicFont,
                        fontSize = 12.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(Modifier.width(2.dp))
                // Reply affordance.
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(brush = Brush.linearGradient(listOf(SeismicHot.Red.copy(alpha = 0.18f), SeismicHot.Orange.copy(alpha = 0.18f))))
                        .border(1.dp, SeismicHot.Red.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.AutoMirrored.Filled.Chat, "Open chat", tint = SeismicHot.Red, modifier = Modifier.size(17.dp))
                }
            }
        }
    }
}

private fun initialsOf(name: String): String =
    name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2)
        .joinToString("") { it.first().uppercase() }.ifBlank { "?" }
