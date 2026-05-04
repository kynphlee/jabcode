package com.jabauth.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = JABAuthPrimary,
    onPrimary = JABAuthOnPrimary,
    primaryContainer = JABAuthPrimaryContainer,
    onPrimaryContainer = JABAuthOnPrimaryContainer,
    error = JABAuthError,
    onError = JABAuthOnError,
    errorContainer = JABAuthErrorContainer,
    onErrorContainer = JABAuthOnErrorContainer,
    background = JABAuthBackground,
    onBackground = JABAuthOnBackground,
    surface = JABAuthSurface,
    onSurface = JABAuthOnSurface
)

private val DarkColorScheme = darkColorScheme(
    primary = JABAuthPrimaryDark,
    onPrimary = JABAuthOnPrimaryDark,
    primaryContainer = JABAuthPrimaryContainerDark,
    onPrimaryContainer = JABAuthOnPrimaryContainerDark,
    error = JABAuthError,
    onError = JABAuthOnError,
    errorContainer = JABAuthErrorContainer,
    onErrorContainer = JABAuthOnErrorContainer,
    background = JABAuthBackgroundDark,
    onBackground = JABAuthOnBackgroundDark,
    surface = JABAuthSurfaceDark,
    onSurface = JABAuthOnSurfaceDark
)

/**
 * JABAuth Material 3 Theme
 * 
 * Applies JABAuth branding and design system tokens.
 * 
 * @param darkTheme Whether to use dark theme
 * @param content Composable content
 */
@Composable
fun JABAuthTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = JABAuthTypography,
        content = content
    )
}
