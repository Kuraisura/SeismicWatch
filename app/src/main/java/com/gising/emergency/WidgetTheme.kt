package com.gising.emergency

import android.content.Context
import android.content.res.Configuration
import com.gising.data.repository.SettingsStore
import com.gising.ui.theme.AppTheme
import com.gising.ui.theme.SeismicPalette
import com.gising.ui.theme.SeismicPalettes
import com.gising.ui.theme.ThemeMode

/**
 * Resolves the user's saved theme palette for home-screen widgets. Glance widgets render outside the
 * Compose tree (in a worker/broadcast), so they can't read [com.gising.ui.theme.SeismicHot]; instead
 * they read the same DataStore-backed [SettingsStore.Snapshot] the app uses and look the palette up
 * directly. For SYSTEM mode we fall back to the launcher's current night-mode config.
 */
internal suspend fun widgetPalette(context: Context): SeismicPalette {
    val snap = runCatching { SettingsStore(context).current() }.getOrNull()
    val theme = AppTheme.fromKey(snap?.appTheme)
    val dark = when (ThemeMode.fromKey(snap?.themeMode)) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> (context.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }
    return SeismicPalettes.paletteFor(theme, dark)
}
