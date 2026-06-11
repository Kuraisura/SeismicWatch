package com.gising.ui.theme

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * Swaps the OS launcher icon + cold-splash theme to match the selected [AppTheme] by enabling the
 * matching `<activity-alias>` and disabling the others.
 *
 * Platform caveats (limits, not bugs): the launcher icon updates within a few seconds and may
 * briefly flicker; some launchers reset the icon's home-screen placement back to the app drawer.
 * The in-app logo, splash and colors change INSTANTLY regardless of this.
 *
 * Every toggle is wrapped in `runCatching`, so if the aliases aren't present in the manifest yet the
 * call is a safe no-op and the app is never left without a launchable component.
 */
object ThemeAliasManager {

    // Aliases are declared in AndroidManifest.xml as `com.gising.Launcher<Suffix>` (see AppTheme.aliasSuffix).
    private const val ALIAS_PREFIX = "com.gising.Launcher"

    private fun aliasComponent(context: Context, theme: AppTheme) =
        ComponentName(context.packageName, "$ALIAS_PREFIX${theme.aliasSuffix}")

    /** Enable the alias for [selected], disable the rest. */
    fun apply(context: Context, selected: AppTheme) {
        val ctx = context.applicationContext
        val pm = ctx.packageManager
        AppTheme.entries.forEach { theme ->
            val state =
                if (theme == selected) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            runCatching {
                pm.setComponentEnabledSetting(
                    aliasComponent(ctx, theme), state, PackageManager.DONT_KILL_APP,
                )
            }
        }
    }
}
