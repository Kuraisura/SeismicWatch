package com.gising.ui.screens.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gising.ui.theme.SeismicColors
import com.gising.ui.theme.SeismicHot

/** Extra bottom space so scrollable content clears the floating bottom-nav pill. */
val FloatingNavClearance: Dp = 104.dp

/**
 * Pill-style segmented control with a single indicator that physically slides between options
 * (spring) as the selection changes — shared across Reports and People so the top-of-screen tab
 * chrome feels native and consistent. Labels may carry an optional leading [SegmentTab.icon].
 */
data class SegmentTab(val label: String, val icon: ImageVector? = null)

@Composable
fun AnimatedSegmentedControl(
    tabs: List<SegmentTab>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = SeismicHot.Card,
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, SeismicHot.Border),
    ) {
        BoxWithConstraints(Modifier.padding(4.dp)) {
            val count = tabs.size.coerceAtLeast(1)
            val segWidth = maxWidth / count
            val indicatorOffset by animateDpAsState(
                targetValue = segWidth * selected,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
                label = "segmentIndicator",
            )
            // Sliding selection pill — red→orange gradient to match the tactical system.
            Box(
                Modifier
                    .offset(x = indicatorOffset)
                    .width(segWidth)
                    .height(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(SeismicHot.Gradient),
            )
            Row(Modifier.fillMaxWidth()) {
                tabs.forEachIndexed { i, tab ->
                    val active = i == selected
                    val tint by androidx.compose.animation.animateColorAsState(
                        targetValue = if (active) SeismicHot.White else SeismicHot.Muted,
                        animationSpec = tween(220),
                        label = "segmentTint",
                    )
                    Box(
                        Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication = null,
                            ) { onSelect(i) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            if (tab.icon != null) {
                                Icon(tab.icon, null, Modifier.size(16.dp), tint = tint)
                            }
                            Text(tab.label, style = MaterialTheme.typography.labelLarge, color = tint)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Wraps [content] in a one-shot fade + slide-up entrance, staggered by [index] so a column of cards
 * animates in sequence on first composition. Cheap and version-safe (no list-scope animation APIs).
 */
@Composable
fun EntranceItem(
    index: Int = 0,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(durationMillis = 280, delayMillis = index * 55)) +
            slideInVertically(
                animationSpec = tween(durationMillis = 320, delayMillis = index * 55),
                initialOffsetY = { it / 6 },
            ),
        modifier = modifier,
    ) { content() }
}

/**
 * Shared large screen header used across Reports / People / Settings so every top-level tab has a
 * consistent title block: a bold title, an optional one-line subtitle, and an optional trailing
 * action slot (icon button, chip, etc.). Sits on the screen background with status-bar inset.
 */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 20.dp, end = 16.dp, top = 16.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SeismicColors.Verdant.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, tint = SeismicColors.Verdant, modifier = Modifier.size(22.dp)) }
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

/** Section label above a group of cards — small, tracked, muted. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/** A rounded card surface with consistent border/elevation used for grouped content. */
@Composable
fun SettingsCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline),
        tonalElevation = 1.dp,
    ) {
        Column(Modifier.fillMaxWidth().padding(4.dp), content = content)
    }
}

/**
 * A tappable list row inside a [SettingsCard]: leading icon chip, title + subtitle, and an optional
 * trailing composable (chevron by default when [onClick] is set).
 */
@Composable
fun SettingRow(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    iconTint: Color = SeismicColors.Verdant,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(iconTint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = iconTint, modifier = Modifier.size(20.dp)) }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        when {
            trailing != null -> trailing()
            onClick != null -> Icon(
                Icons.Outlined.ChevronRight, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
