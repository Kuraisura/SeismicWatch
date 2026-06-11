package com.gising.ui.screens.connections

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gising.ui.screens.ContactsScreen
import com.gising.ui.screens.ContactsViewModel
import com.gising.ui.screens.components.AnimatedSegmentedControl
import com.gising.ui.screens.components.SegmentTab

/**
 * The "People" tab: two complementary capabilities under one roof.
 *  • Live — Life360-style real-time location sharing within circles (Firebase-backed).
 *  • Alerts — the original emergency contacts that get auto-SMS/called on a major quake.
 */
@Composable
fun FamilyHubScreen(
    circlesViewModel: CirclesViewModel,
    contactsViewModel: ContactsViewModel,
) {
    var tab by remember { mutableIntStateOf(0) }

    // Sits inside ManageOverlay, which already supplies the tactical ground + status inset.
    Column(Modifier.fillMaxSize()) {
        AnimatedSegmentedControl(
            tabs = listOf(
                SegmentTab("Live location", Icons.Outlined.Groups),
                SegmentTab("Alerts", Icons.Outlined.NotificationsActive),
            ),
            selected = tab,
            onSelect = { tab = it },
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 6.dp),
        )
        Spacer(Modifier.height(6.dp))
        // Slide the panel in the direction of travel as the tab changes.
        AnimatedContent(
            targetState = tab,
            transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                (slideInHorizontally(tween(280)) { w -> dir * w / 6 } + fadeIn(tween(280)))
                    .togetherWith(slideOutHorizontally(tween(220)) { w -> -dir * w / 6 } + fadeOut(tween(180)))
                    .using(SizeTransform(clip = false))
            },
            label = "peopleTab",
        ) { current ->
            when (current) {
                0 -> CirclesScreen(circlesViewModel)
                else -> ContactsScreen(contactsViewModel)
            }
        }
    }
}
