package com.gising.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Cyclone
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.gising.data.model.Earthquake
import com.gising.data.model.Typhoon
import com.gising.ui.theme.SeismicHot

/**
 * A unified, severity-ranked hazard alert, derived from the live PHIVOLCS / GDACS feeds. Powers the
 * redesigned Reports list and the Notifications screen so both speak the same "Critical / High /
 * Moderate" language.
 */

enum class AlertSeverity(val label: String, val color: Color, val rank: Int) {
    CRITICAL("Critical", SeismicHot.Red, 3),
    HIGH("High", SeismicHot.Orange, 2),
    MODERATE("Moderate", SeismicHot.Yellow, 1),
}

enum class AlertKind(val label: String, val icon: ImageVector) {
    EARTHQUAKE("Earthquake", Icons.Outlined.GraphicEq),
    TYPHOON("Typhoon", Icons.Outlined.Cyclone),
    TROPICAL_STORM("Tropical Storm", Icons.Outlined.WaterDrop),
}

/**
 * One hazard event normalized for display.
 *
 * @param value the headline number ("6.8", "195", "LVL 3")
 * @param valueUnit the sub-label under it ("MAGNITUDE", "KM/H WIND", "ALERT")
 * @param badges short metadata pills ("10km depth", "2 min ago")
 * @param detailRows icon-prefixed rows shown in the expanded panel
 * @param waveform whether to render the seismic sparkline (earthquakes only)
 * @param quake the backing earthquake, when this alert is one (enables tap-through to Detail)
 */
data class AlertItem(
    val id: String,
    val kind: AlertKind,
    val severity: AlertSeverity,
    val title: String,
    val subtitle: String,
    val value: String,
    val valueUnit: String,
    val timeMs: Long,
    val badges: List<String>,
    val summary: String,
    val detailRows: List<AlertDetailRow>,
    val waveform: Boolean = false,
    val quake: Earthquake? = null,
)

data class AlertDetailRow(val icon: ImageVector, val text: String)

/**
 * Fold every live feed into a single severity-sorted alert list.
 *
 * Severity rules:
 *  - Earthquake: M≥6 (or tsunami) → Critical, M≥5 → High, M≥4 → Moderate (weaker quakes omitted).
 *  - Cyclone: GDACS RED → Critical, ORANGE → High, else Moderate.
 */
fun buildAlerts(
    quakes: List<Earthquake>,
    typhoons: List<Typhoon>,
    distanceToQuake: (Earthquake) -> Double?,
): List<AlertItem> {
    val out = mutableListOf<AlertItem>()

    quakes.asSequence()
        .filter { it.magnitude >= 4.0 }
        .forEach { q ->
            val sev = when {
                q.magnitude >= 6.0 || q.tsunami == 1 -> AlertSeverity.CRITICAL
                q.magnitude >= 5.0 -> AlertSeverity.HIGH
                else -> AlertSeverity.MODERATE
            }
            val dist = distanceToQuake(q)
            val badges = buildList {
                add("${q.depth.toInt()}km depth")
                add(formatRelativeTime(q.timeMs))
            }
            val detail = buildList {
                add(AlertDetailRow(Icons.Outlined.Bolt, "${"%.2f".format(q.latitude)}°N, ${"%.2f".format(q.longitude)}°E"))
                add(AlertDetailRow(Icons.Outlined.GraphicEq, "Depth ${q.depth.toInt()} km · ${q.place}"))
                if (q.tsunami == 1) add(AlertDetailRow(Icons.Outlined.WaterDrop, "Tsunami advisory issued for coastal areas"))
                dist?.let { add(AlertDetailRow(Icons.Outlined.Bolt, "${it.toInt()} km from your location")) }
            }
            val summary = when {
                q.tsunami == 1 -> "Tsunami advisory issued · evacuate to higher ground"
                q.magnitude >= 6.0 -> "Strong shaking expected near the epicenter"
                q.magnitude >= 5.0 -> "Felt across the region · check for damage"
                else -> "Tectonic · no damage reported"
            }
            out += AlertItem(
                id = "eq-${q.id}",
                kind = AlertKind.EARTHQUAKE,
                severity = sev,
                title = "Earthquake",
                subtitle = q.place,
                value = "%.1f".format(q.magnitude),
                valueUnit = "MAGNITUDE",
                timeMs = q.timeMs,
                badges = badges,
                summary = summary,
                detailRows = detail,
                waveform = true,
                quake = q,
            )
        }

    typhoons.asSequence()
        .filter { it.isCurrent }
        .forEach { t ->
            val sev = when (t.alertLevel.name) {
                "RED" -> AlertSeverity.CRITICAL
                "ORANGE" -> AlertSeverity.HIGH
                else -> AlertSeverity.MODERATE
            }
            val wind = t.windKph?.toInt()
            val isStorm = (wind ?: 0) < 118
            val badges = buildList {
                add(t.category.label)
                add(formatRelativeTime(t.fromMs))
            }
            val detail = buildList {
                wind?.let { add(AlertDetailRow(Icons.Outlined.Cyclone, "$it km/h sustained winds")) }
                add(AlertDetailRow(Icons.Outlined.WaterDrop, t.alertLevel.label))
            }
            out += AlertItem(
                id = "ty-${t.id}",
                kind = if (isStorm) AlertKind.TROPICAL_STORM else AlertKind.TYPHOON,
                severity = sev,
                title = if (isStorm) "Tropical Storm" else "Typhoon ${t.name}",
                subtitle = t.category.label,
                value = wind?.toString() ?: "—",
                valueUnit = "KM/H WIND",
                timeMs = t.fromMs,
                badges = badges,
                summary = if (isStorm) "Rain advisory · flooding possible" else "Landfall risk · monitor PAGASA bulletins",
                detailRows = detail,
            )
        }

    // Most severe first, then most recent.
    return out.sortedWith(compareByDescending<AlertItem> { it.severity.rank }.thenByDescending { it.timeMs })
}
