package com.jabauth.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * The JABAuth colour scheme.
 *
 * The design system (DESIGN_SYSTEM.md v1.0.0) is dark-only: a stepped navy
 * surface hierarchy with neon accents. There is no light variant, so a single
 * [darkColorScheme] backs the theme and the design-system tokens map onto
 * Material slots as follows:
 *
 *  - primary / secondary / tertiary → the cyan, green and amber accents
 *  - background / surface           → base and elevated navy surfaces
 *  - surfaceVariant                 → card surface
 *  - outline / outlineVariant       → border and grid lines
 *  - onX                            → the design-system text + on-accent tokens
 */
private val JABAuthColorScheme = darkColorScheme(
    primary = JABAuthPrimary,
    onPrimary = JABAuthOnPrimary,
    primaryContainer = JABAuthPrimaryContainer,
    onPrimaryContainer = JABAuthOnPrimaryContainer,

    secondary = JABAuthSuccess,
    onSecondary = JABAuthOnSuccess,
    secondaryContainer = JABAuthSuccessContainer,
    onSecondaryContainer = JABAuthOnSuccessContainer,

    tertiary = JABAuthWarning,
    onTertiary = JABAuthOnWarning,
    tertiaryContainer = JABAuthWarningContainer,
    onTertiaryContainer = JABAuthOnWarningContainer,

    error = JABAuthError,
    onError = JABAuthOnError,
    errorContainer = JABAuthErrorContainer,
    onErrorContainer = JABAuthOnErrorContainer,

    background = JABAuthBgBase,
    onBackground = JABAuthOnBackground,
    surface = JABAuthBgBase,
    onSurface = JABAuthOnSurface,
    surfaceVariant = JABAuthBgCard,
    onSurfaceVariant = JABAuthOnSurfaceVariant,

    outline = JABAuthBorder,
    outlineVariant = JABAuthGrid
)
// NOTE: Material 3 1.1.2 (the pinned version) predates the surfaceContainer*
// roles. The stepped surfaces (elevated / card / hover) are exposed as direct
// design-system tokens (JABAuthBgElevated / JABAuthBgCard / JABAuthBgHover) and
// consumed by the JABAuth* composables rather than via Material slots.

/**
 * JABAuth Material 3 theme.
 *
 * Applies the JABAuth design-system colour scheme ([JABAuthColorScheme]) and
 * [JABAuthTypography]. The design system is dark-only, so the scheme is fixed
 * regardless of the system setting; the [darkTheme] parameter is retained for
 * API compatibility and future light-mode work but does not currently switch
 * palettes.
 *
 * @param darkTheme retained for API compatibility (no effect — design is dark-only)
 * @param content composable content
 */
@Composable
fun JABAuthTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = JABAuthColorScheme,
        typography = JABAuthTypography,
        content = content
    )
}
