package com.gising.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The 5 user-selectable themes. DEEP_OCEAN is the calm default (the client found the original
 * red overwhelming). TACTICAL_RED preserves the original look as an opt-in.
 *
 * The `key` is what's persisted in DataStore; `displayName` is shown in Settings; `aliasSuffix`
 * maps to the `<activity-alias>` that swaps the OS launcher icon (see ThemeAliasManager).
 */
enum class AppTheme(val key: String, val displayName: String, val aliasSuffix: String) {
    DEEP_OCEAN("deep_ocean", "Deep Ocean", "DeepOcean"),
    FOREST("forest", "Forest", "Forest"),
    INDIGO_NIGHT("indigo_night", "Indigo Night", "Indigo"),
    SLATE_MONO("slate_mono", "Slate", "Slate"),
    TACTICAL_RED("tactical_red", "Tactical Red", "TacticalRed");

    companion object {
        val DEFAULT = DEEP_OCEAN
        fun fromKey(key: String?): AppTheme = entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}

/** Light / dark / follow-system mode for the active theme. */
enum class ThemeMode(val key: String) {
    SYSTEM("system"), LIGHT("light"), DARK("dark");

    companion object {
        fun fromKey(key: String?): ThemeMode = entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

/**
 * The full set of brand + surface + severity colors a screen needs. Field names mirror the legacy
 * [SeismicHot] token object so every existing call site keeps working once [SeismicHot] reads from
 * the active palette. Severity colors (critical/high/moderate) stay meaningful across all themes —
 * only the *chrome* (base/card/field/nav/border/label + the brand accent) changes per theme, which
 * is what removes the pervasive red the client disliked.
 */
data class SeismicPalette(
    // Surfaces & chrome
    val base: Color,
    val card: Color,
    val field: Color,
    val nav: Color,
    val border: Color,
    // Brand accent pair (logo gradient, active nav, live pills, wordmark)
    val accent: Color,
    val accentAlt: Color,
    // Text
    val white: Color,   // primary foreground on tactical surfaces (dark ink on light themes)
    val label: Color,   // secondary / caption text
    val muted: Color,   // disabled / faint
    // Fixed-meaning status colors (kept consistent across themes, lightly softened)
    val safe: Color = Color(0xFF22C55E),
    val signal: Color = Color(0xFF4D7CFE),
    val critical: Color = Color(0xFFF2495C),   // genuine danger — still red, a touch softer
    val high: Color = Color(0xFFF6852E),
    val moderate: Color = Color(0xFFF0B72E),
    // Splash
    val splashBg: Color,
    val splashIcon: Color,
)

/**
 * Catalog of every palette. Built from one accent pair + a tuned neutral ramp per theme so the look
 * is cohesive. `paletteFor(theme, dark)` is the single lookup used by the theme layer.
 */
object SeismicPalettes {

    // ── Deep Ocean (default) — calm blues ────────────────────────────────────
    private val OceanDark = SeismicPalette(
        base = Color(0xFF0A1019), card = Color(0xFF0F1825), field = Color(0xFF12202F),
        nav = Color(0xFF0A121C), border = Color(0xFF1D3247),
        accent = Color(0xFF2E9BE6), accentAlt = Color(0xFF22C7D6),
        white = Color(0xFFEAF2FB), label = Color(0xFF8FB3D9), muted = Color(0xFF3C5670),
        splashBg = Color(0xFF0A1019), splashIcon = Color(0xFF2E9BE6),
    )
    private val OceanLight = SeismicPalette(
        base = Color(0xFFF3F8FD), card = Color(0xFFFFFFFF), field = Color(0xFFEAF1F8),
        nav = Color(0xFFFFFFFF), border = Color(0xFFD4E2F0),
        accent = Color(0xFF1577C7), accentAlt = Color(0xFF0E96A8),
        white = Color(0xFF10202F), label = Color(0xFF3C5670), muted = Color(0xFF92A9C2),
        splashBg = Color(0xFFF3F8FD), splashIcon = Color(0xFF1577C7),
    )

    // ── Forest — calm greens ─────────────────────────────────────────────────
    private val ForestDark = SeismicPalette(
        base = Color(0xFF0A1410), card = Color(0xFF0F1D17), field = Color(0xFF12241C),
        nav = Color(0xFF0A1510), border = Color(0xFF1D3A2C),
        accent = Color(0xFF35B97A), accentAlt = Color(0xFF8FCB54),
        white = Color(0xFFE9F6EE), label = Color(0xFF8FC4A6), muted = Color(0xFF3A5C49),
        splashBg = Color(0xFF0A1410), splashIcon = Color(0xFF35B97A),
    )
    private val ForestLight = SeismicPalette(
        base = Color(0xFFF2F9F4), card = Color(0xFFFFFFFF), field = Color(0xFFE8F3EC),
        nav = Color(0xFFFFFFFF), border = Color(0xFFD2E7DA),
        accent = Color(0xFF1E8E58), accentAlt = Color(0xFF5C9E2E),
        white = Color(0xFF10211A), label = Color(0xFF3A5C49), muted = Color(0xFF92B6A1),
        splashBg = Color(0xFFF2F9F4), splashIcon = Color(0xFF1E8E58),
    )

    // ── Indigo Night — calm purples ──────────────────────────────────────────
    private val IndigoDark = SeismicPalette(
        base = Color(0xFF0D0B1A), card = Color(0xFF141127), field = Color(0xFF1A1633),
        nav = Color(0xFF0B0915), border = Color(0xFF2A2450),
        accent = Color(0xFF7C6CF0), accentAlt = Color(0xFFB36BE6),
        white = Color(0xFFEDEBFA), label = Color(0xFFA9A2D9), muted = Color(0xFF4C4570),
        splashBg = Color(0xFF0D0B1A), splashIcon = Color(0xFF7C6CF0),
    )
    private val IndigoLight = SeismicPalette(
        base = Color(0xFFF6F5FD), card = Color(0xFFFFFFFF), field = Color(0xFFEDEBF8),
        nav = Color(0xFFFFFFFF), border = Color(0xFFDDD9F0),
        accent = Color(0xFF5B4BD6), accentAlt = Color(0xFF9148C7),
        white = Color(0xFF161229), label = Color(0xFF4C4570), muted = Color(0xFFA29BC2),
        splashBg = Color(0xFFF6F5FD), splashIcon = Color(0xFF5B4BD6),
    )

    // ── Slate Mono — neutral grey + a single cool accent (most minimal) ───────
    private val SlateDark = SeismicPalette(
        base = Color(0xFF101216), card = Color(0xFF171A20), field = Color(0xFF1C2027),
        nav = Color(0xFF0D0F13), border = Color(0xFF2A2F38),
        accent = Color(0xFF6E8BA8), accentAlt = Color(0xFF9AA7B5),
        white = Color(0xFFEDEFF2), label = Color(0xFF9AA3AF), muted = Color(0xFF4A515C),
        splashBg = Color(0xFF101216), splashIcon = Color(0xFF6E8BA8),
    )
    private val SlateLight = SeismicPalette(
        base = Color(0xFFF5F6F8), card = Color(0xFFFFFFFF), field = Color(0xFFECEEF1),
        nav = Color(0xFFFFFFFF), border = Color(0xFFDADEE3),
        accent = Color(0xFF4A6580), accentAlt = Color(0xFF6E7B8A),
        white = Color(0xFF14171C), label = Color(0xFF4A515C), muted = Color(0xFF9AA3AF),
        splashBg = Color(0xFFF5F6F8), splashIcon = Color(0xFF4A6580),
    )

    // ── Tactical Red — the original look, preserved ───────────────────────────
    private val RedDark = SeismicPalette(
        base = Color(0xFF0A0608), card = Color(0xFF130B0E), field = Color(0xFF1A0D11),
        nav = Color(0xFF0E080B), border = Color(0xFF2E1520),
        accent = Color(0xFFFF2D55), accentAlt = Color(0xFFFF6A00),
        white = Color(0xFFF7F2F4), label = Color(0xFFFF9AB0), muted = Color(0xFF6B3345),
        critical = Color(0xFFFF2D55), high = Color(0xFFFF6A00), moderate = Color(0xFFF5C518),
        splashBg = Color(0xFF0A0608), splashIcon = Color(0xFFFF2D55),
    )
    private val RedLight = SeismicPalette(
        base = Color(0xFFFDF5F6), card = Color(0xFFFFFFFF), field = Color(0xFFF7E9EC),
        nav = Color(0xFFFFFFFF), border = Color(0xFFF0D4DA),
        accent = Color(0xFFE01E45), accentAlt = Color(0xFFE25E00),
        white = Color(0xFF22090F), label = Color(0xFF8A4256), muted = Color(0xFFC290A0),
        critical = Color(0xFFE01E45), high = Color(0xFFE25E00), moderate = Color(0xFFD9A516),
        splashBg = Color(0xFFFDF5F6), splashIcon = Color(0xFFE01E45),
    )

    fun paletteFor(theme: AppTheme, dark: Boolean): SeismicPalette = when (theme) {
        AppTheme.DEEP_OCEAN -> if (dark) OceanDark else OceanLight
        AppTheme.FOREST -> if (dark) ForestDark else ForestLight
        AppTheme.INDIGO_NIGHT -> if (dark) IndigoDark else IndigoLight
        AppTheme.SLATE_MONO -> if (dark) SlateDark else SlateLight
        AppTheme.TACTICAL_RED -> if (dark) RedDark else RedLight
    }
}

/** The active palette, provided at the top of the tree by [SeismicWatchTheme]. */
val LocalSeismicPalette = staticCompositionLocalOf { SeismicPalettes.paletteFor(AppTheme.DEFAULT, dark = true) }
