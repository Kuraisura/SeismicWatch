package com.gising.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import com.gising.R

// ─── Google Fonts Provider ──────────────────────────────────────────────────
val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

// Brand pairing — Clash Grotesk (display) + Satoshi (body), both bundled in res/font and
// defined in SeismicDesign.kt. Display/headline/title styles use Clash; body/label use Satoshi.
// PlusJakarta is retained as a downloadable fallback alias for any legacy references.
val JakartaFont = GoogleFont("Plus Jakarta Sans")

val PlusJakarta = FontFamily(
    Font(googleFont = JakartaFont, fontProvider = provider, weight = FontWeight.Light),
    Font(googleFont = JakartaFont, fontProvider = provider, weight = FontWeight.Normal),
    Font(googleFont = JakartaFont, fontProvider = provider, weight = FontWeight.Medium),
    Font(googleFont = JakartaFont, fontProvider = provider, weight = FontWeight.SemiBold),
    Font(googleFont = JakartaFont, fontProvider = provider, weight = FontWeight.Bold),
    Font(googleFont = JakartaFont, fontProvider = provider, weight = FontWeight.ExtraBold),
)

// Back-compat aliases so existing references compile while the whole app uses the brand pair.
val SpaceGrotesk = ClashDisplay
val InterTight = Satoshi
val DMMono = Satoshi

// ─── Color Palette ──────────────────────────────────────────────────────────
// Obsidian × Teal — dark geological palette with a calm tech-teal "safe" accent,
// amber for advisories and crimson for critical alerts.
object SeismicColors {
    // Backgrounds
    val Obsidian = Color(0xFF12141C)         // Midnight Obsidian — primary ground
    val DeepSlate = Color(0xFF181B26)        // card backgrounds
    val Slate = Color(0xFF1E2230)            // Tectonic Slate — elevated surfaces
    val MidSlate = Color(0xFF2A2F42)         // borders, dividers

    // Text
    val Chalk = Color(0xFFF5F6F9)            // Ashen White — primary text
    val Mist = Color(0xFF9A9BAD)             // secondary text
    val Fog = Color(0xFF5C5F72)              // disabled/hint

    // Accent — Safe-Zone Teal (primary brand: all-clear, "I'm safe", GPS pins)
    val Verdant = Color(0xFF00C7BE)          // primary brand accent
    val VerdantDim = Color(0xFF0A3A37)       // teal dark bg / containers
    val VerdantGlow = Color(0xFF33D6CE)      // hover / emphasis
    val VerdantDeep = Color(0xFF07302E)      // radar fill / deep panels

    // Alert — Epicenter Crimson (radar + SOS, high-magnitude warnings)
    val Alert = Color(0xFFFF3B30)            // active-alert accent
    val AlertDim = Color(0xFF3A1416)         // alert dark bg
    val AlertGlow = Color(0xFFFF6259)        // alert emphasis

    // Back-compat aliases: existing call sites use `Ember*`; repoint them to teal so the
    // whole app rebrands without touching every file.
    val Ember = Verdant                      // primary brand accent (now teal)
    val EmberDim = VerdantDim
    val EmberGlow = VerdantGlow

    // Seismic severity colors
    val MagnitudeMinor = Color(0xFF00C7BE)   // teal — minor / safe
    val MagnitudeLight = Color(0xFFFFC04D)   // soft amber — light
    val MagnitudeModerate = Color(0xFFFF9500) // Friction Amber — moderate
    val MagnitudeStrong = Color(0xFFFF6A2C)  // deep amber/orange — strong
    val MagnitudeMajor = Color(0xFFFF3B30)   // Epicenter Crimson — major
    val MagnitudeGreat = Color(0xFFA01FFF)   // purple — great/catastrophic

    // Light theme variants
    val PaperWhite = Color(0xFFF8F7F3)
    val PaperSurface = Color(0xFFEFEEE8)
    val PaperCard = Color(0xFFFFFFFF)
    val InkPrimary = Color(0xFF0F1117)
    val InkSecondary = Color(0xFF4A4D5E)
}

val DarkColorScheme = darkColorScheme(
    primary = SeismicColors.Verdant,
    onPrimary = SeismicColors.Obsidian,
    primaryContainer = SeismicColors.VerdantDim,
    onPrimaryContainer = SeismicColors.VerdantGlow,
    secondary = SeismicColors.MagnitudeMinor,
    onSecondary = SeismicColors.Obsidian,
    background = SeismicColors.Obsidian,
    onBackground = SeismicColors.Chalk,
    surface = SeismicColors.DeepSlate,
    onSurface = SeismicColors.Chalk,
    surfaceVariant = SeismicColors.Slate,
    onSurfaceVariant = SeismicColors.Mist,
    outline = SeismicColors.MidSlate,
    error = SeismicColors.MagnitudeMajor,
)

val LightColorScheme = lightColorScheme(
    primary = SeismicColors.Verdant,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCFF3F1),
    onPrimaryContainer = Color(0xFF063D3A),
    secondary = Color(0xFF0F6E56),
    onSecondary = Color.White,
    background = SeismicColors.PaperWhite,
    onBackground = SeismicColors.InkPrimary,
    surface = SeismicColors.PaperCard,
    onSurface = SeismicColors.InkPrimary,
    surfaceVariant = SeismicColors.PaperSurface,
    onSurfaceVariant = SeismicColors.InkSecondary,
    outline = Color(0xFFD0CFC8),
    error = SeismicColors.MagnitudeMajor,
)

// ─── Typography ─────────────────────────────────────────────────────────────
val SeismicTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = ClashDisplay,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 54.sp,
        lineHeight = 58.sp,
        letterSpacing = (-1.5).sp
    ),
    displayMedium = TextStyle(
        fontFamily = ClashDisplay,
        fontWeight = FontWeight.Bold,
        fontSize = 42.sp,
        lineHeight = 48.sp,
        letterSpacing = (-1).sp
    ),
    displaySmall = TextStyle(
        fontFamily = ClashDisplay,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = ClashDisplay,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.3).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = ClashDisplay,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.2).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = ClashDisplay,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp
    ),
    titleLarge = TextStyle(
        fontFamily = ClashDisplay,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = ClashDisplay,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    titleSmall = TextStyle(
        fontFamily = ClashDisplay,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = Satoshi,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = Satoshi,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp
    ),
    bodySmall = TextStyle(
        fontFamily = Satoshi,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 16.sp
    ),
    labelLarge = TextStyle(
        fontFamily = Satoshi,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.3.sp
    ),
    labelMedium = TextStyle(
        fontFamily = Satoshi,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.4.sp
    ),
    labelSmall = TextStyle(
        fontFamily = Satoshi,
        fontWeight = FontWeight.SemiBold,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.8.sp
    ),
)

// ─── Theme Composable ────────────────────────────────────────────────────────
/** Build a Material3 [ColorScheme] from a [SeismicPalette] so MaterialTheme-based widgets re-theme too. */
private fun colorSchemeFor(p: SeismicPalette, dark: Boolean): ColorScheme =
    if (dark) {
        darkColorScheme(
            primary = p.accent, onPrimary = p.base,
            primaryContainer = p.field, onPrimaryContainer = p.white,
            secondary = p.accentAlt, onSecondary = p.base,
            background = p.base, onBackground = p.white,
            surface = p.card, onSurface = p.white,
            surfaceVariant = p.field, onSurfaceVariant = p.label,
            outline = p.border, error = p.critical, onError = Color.White,
        )
    } else {
        lightColorScheme(
            primary = p.accent, onPrimary = Color.White,
            primaryContainer = p.field, onPrimaryContainer = p.white,
            secondary = p.accentAlt, onSecondary = Color.White,
            background = p.base, onBackground = p.white,
            surface = p.card, onSurface = p.white,
            surfaceVariant = p.field, onSurfaceVariant = p.label,
            outline = p.border, error = p.critical, onError = Color.White,
        )
    }

/**
 * App-wide theme. Resolves [appTheme] + [themeMode] to a concrete [SeismicPalette], keeps the global
 * [SeismicHot] tactical tokens in sync (so every existing `SeismicHot.*` reader recomposes), and
 * exposes the palette via [LocalSeismicPalette] for new code.
 */
@Composable
fun SeismicWatchTheme(
    appTheme: AppTheme = AppTheme.DEFAULT,
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val palette = SeismicPalettes.paletteFor(appTheme, dark)

    // Keep the legacy tactical token holder pointed at the active palette. This is applied
    // SYNCHRONOUSLY during composition (not in a SideEffect) on purpose: SeismicHot is a process-wide
    // singleton that defaults to a DARK palette, and a SideEffect only runs it *after* the first
    // composition commits. That left a window on cold start / every light↔dark switch where light-mode
    // screens painted near-white text (the stale dark palette's `white`) on the already-light Material
    // background — the "text is the same colour as the background / unreadable in light mode" bug.
    // Applying here means children (composed after this line) read the correct palette on the very first
    // frame. apply() is a no-op when the palette is unchanged (data-class equality), so it's cheap and
    // won't cause recomposition churn.
    SeismicHot.apply(palette)

    CompositionLocalProvider(LocalSeismicPalette provides palette) {
        MaterialTheme(
            colorScheme = colorSchemeFor(palette, dark),
            typography = SeismicTypography,
            content = content
        )
    }
}
