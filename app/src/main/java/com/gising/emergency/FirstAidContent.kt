package com.gising.emergency

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.Bloodtype
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Healing
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Hardcoded, fully-offline first-aid content. Everything here ships inside the APK — no network is
 * ever touched — because the moment these cards matter most is exactly when connectivity is gone.
 *
 * Content is intentionally terse and action-first: short numbered steps a panicking person can
 * follow, not paragraphs. Sourced from standard Red Cross / AHA layperson guidance.
 */

/** Which built-in animation accompanies a card (drawn in [FirstAidOverlay]). */
enum class AidAnim { COMPRESSION, PRESSURE, DUCK_COVER, TOURNIQUET, COOL, BREATH }

data class FirstAidCard(
    val title: String,
    val subtitle: String,
    val accent: Color,
    val icon: ImageVector,
    val anim: AidAnim,
    val steps: List<String>,
)

object FirstAidContent {

    private val Red = Color(0xFFFF3B30)
    private val Orange = Color(0xFFFF9500)
    private val Green = Color(0xFF00C7BE)
    private val Blue = Color(0xFF4FC3A1)

    val cards: List<FirstAidCard> = listOf(
        FirstAidCard(
            title = "CPR",
            subtitle = "Not breathing · no pulse",
            accent = Red,
            icon = Icons.Outlined.Favorite,
            anim = AidAnim.COMPRESSION,
            steps = listOf(
                "Lay them flat on a hard surface. Kneel beside the chest.",
                "Heel of one hand on the centre of the chest, other hand on top.",
                "Push HARD and FAST — at least 5 cm deep, 100–120 pushes/min.",
                "Let the chest rise fully between pushes. Don't stop.",
                "Keep going until they breathe or help takes over.",
            ),
        ),
        FirstAidCard(
            title = "Severe Bleeding",
            subtitle = "Heavy blood loss",
            accent = Red,
            icon = Icons.Outlined.Bloodtype,
            anim = AidAnim.PRESSURE,
            steps = listOf(
                "Press hard directly on the wound with a cloth or your hand.",
                "Do NOT remove soaked cloths — add more on top.",
                "Keep pressing firmly without stopping.",
                "If a limb, raise it above the heart while pressing.",
                "Keep them warm and still until help arrives.",
            ),
        ),
        FirstAidCard(
            title = "Tourniquet",
            subtitle = "Limb bleeding won't stop",
            accent = Orange,
            icon = Icons.Outlined.Healing,
            anim = AidAnim.TOURNIQUET,
            steps = listOf(
                "Only if direct pressure FAILS on an arm or leg.",
                "Tie a wide band 5–7 cm above the wound (not on a joint).",
                "Tighten with a stick/rod until bleeding stops.",
                "Secure the stick. Note the TIME applied.",
                "Do not loosen. Tell responders the time.",
            ),
        ),
        FirstAidCard(
            title = "Duck & Cover",
            subtitle = "Earthquake — shaking now",
            accent = Green,
            icon = Icons.Outlined.Shield,
            anim = AidAnim.DUCK_COVER,
            steps = listOf(
                "DROP to your hands and knees immediately.",
                "COVER your head and neck under a sturdy table.",
                "HOLD ON to it until the shaking stops.",
                "Stay away from windows and heavy furniture.",
                "After shaking, exit calmly — use stairs, not lifts.",
            ),
        ),
        FirstAidCard(
            title = "Burns",
            subtitle = "Heat / fire injury",
            accent = Orange,
            icon = Icons.Outlined.LocalFireDepartment,
            anim = AidAnim.COOL,
            steps = listOf(
                "Cool under clean running water for 20 minutes.",
                "Remove rings/watches before swelling starts.",
                "Do NOT pop blisters or apply ice, oil, or toothpaste.",
                "Cover loosely with cling film or a clean cloth.",
                "Seek help for large, deep, or facial burns.",
            ),
        ),
        FirstAidCard(
            title = "Choking",
            subtitle = "Can't breathe / speak",
            accent = Blue,
            icon = Icons.Outlined.Air,
            anim = AidAnim.BREATH,
            steps = listOf(
                "Ask: are you choking? If they can't speak, act.",
                "5 firm back blows between the shoulder blades.",
                "Then 5 abdominal thrusts (above the navel, inward/up).",
                "Alternate 5 and 5 until the object clears.",
                "If they collapse, start CPR.",
            ),
        ),
    )
}
