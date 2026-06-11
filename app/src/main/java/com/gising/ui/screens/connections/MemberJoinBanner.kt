package com.gising.ui.screens.connections

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Diversity3
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gising.ui.theme.SeismicColors
import kotlinx.coroutines.delay

/**
 * A lightweight, in-app drop-down banner announcing that someone just joined a circle.
 *
 * Entirely local: it's fed by [CirclesViewModel.memberJoined], which is driven by the
 * existing Firestore live listener, so it works while the app is open with no server,
 * push notification, or extra permission. It auto-dismisses after [VISIBLE_MS].
 *
 * Pass the latest [event] (or null when nothing to show); [onDismiss] is called when the
 * banner has timed out so the host can clear its state.
 */
@Composable
fun MemberJoinBanner(
    event: MemberJoinEvent?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Retain the last event so its text stays put during the slide-out animation.
    var shown by remember { mutableStateOf<MemberJoinEvent?>(null) }

    LaunchedEffect(event) {
        if (event != null) {
            shown = event
            delay(VISIBLE_MS)
            onDismiss()
        }
    }

    AnimatedVisibility(
        visible = event != null,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = modifier,
    ) {
        val name = (event ?: shown)?.displayName ?: ""
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = SeismicColors.Slate,
            shadowElevation = 8.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(40.dp)
                        .background(SeismicColors.EmberDim, CircleShape),
                ) {
                    Icon(
                        Icons.Outlined.Diversity3,
                        contentDescription = null,
                        tint = SeismicColors.EmberGlow,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Column(modifier = Modifier.padding(start = 12.dp)) {
                    Text(
                        text = "$name joined your family",
                        color = SeismicColors.Chalk,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "They can now share their location with your circle",
                        color = SeismicColors.Mist,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

private const val VISIBLE_MS = 4_000L
