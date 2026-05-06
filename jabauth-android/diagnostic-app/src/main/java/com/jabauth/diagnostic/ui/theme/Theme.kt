package com.jabauth.diagnostic.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * JABAuth Theme with context-specific colors
 * 
 * Supports three application contexts (Healthcare, Legal, IoT)
 * and both light/dark modes.
 * 
 * @param context Application domain context for color customization
 * @param darkTheme Whether to use dark theme (defaults to system preference)
 * @param content Composable content to theme
 */
@Composable
fun JABAuthTheme(
    context: AppContext = AppContext.Healthcare,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            // Primary colors - context-specific
            primary = context.primaryColor(),
            onPrimary = Color(0xFF0B1120),
            primaryContainer = context.primaryDim(),
            onPrimaryContainer = Color(0xFFE8F4F8),
            
            // Secondary colors
            secondary = context.secondaryColor(),
            onSecondary = Color(0xFF0B1120),
            secondaryContainer = context.secondaryColor().copy(alpha = 0.3f),
            onSecondaryContainer = Color(0xFFE8F4F8),
            
            // Tertiary colors - for alerts/warnings
            tertiary = Color(0xFFFFB547),  // Amber
            onTertiary = Color(0xFF0B1120),
            tertiaryContainer = Color(0xFFCC8844),
            onTertiaryContainer = Color(0xFFE8F4F8),
            
            // Error colors
            error = Color(0xFFFF6B6B),
            onError = Color(0xFF0B1120),
            errorContainer = Color(0xFFCC4646),
            onErrorContainer = Color(0xFFFFC9C9),
            
            // Background colors - dark diagnostic UI
            background = Color(0xFF0B1120),  // Deep navy
            onBackground = Color(0xFFE8F4F8),  // Light cyan
            
            // Surface colors
            surface = Color(0xFF1A2438),  // Elevated navy
            onSurface = Color(0xFFE8F4F8),
            surfaceVariant = Color(0xFF2A3448),  // Lighter surface
            onSurfaceVariant = Color(0xFFB8C8D8),  // Dimmed text
            
            // Outline colors
            outline = Color(0xFF3A4458),
            outlineVariant = Color(0xFF2A3448),
            
            // Inverse colors
            inverseSurface = Color(0xFFE8F4F8),
            inverseOnSurface = Color(0xFF1A2438),
            inversePrimary = context.primaryDim(),
            
            // Scrim for modals
            scrim = Color(0xFF000000).copy(alpha = 0.5f)
        )
    } else {
        lightColorScheme(
            // Primary colors - context-specific
            primary = context.primaryDim(),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = context.primaryColor().copy(alpha = 0.2f),
            onPrimaryContainer = Color(0xFF0B1120),
            
            // Secondary colors
            secondary = context.secondaryColor(),
            onSecondary = Color(0xFFFFFFFF),
            secondaryContainer = context.secondaryColor().copy(alpha = 0.15f),
            onSecondaryContainer = Color(0xFF0B1120),
            
            // Tertiary colors
            tertiary = Color(0xFFCC8844),
            onTertiary = Color(0xFFFFFFFF),
            tertiaryContainer = Color(0xFFFFE5CC),
            onTertiaryContainer = Color(0xFF0B1120),
            
            // Error colors
            error = Color(0xFFCC4646),
            onError = Color(0xFFFFFFFF),
            errorContainer = Color(0xFFFFCCC9),
            onErrorContainer = Color(0xFF410002),
            
            // Background colors - light diagnostic UI
            background = Color(0xFFF8FAFC),  // Soft white
            onBackground = Color(0xFF0B1120),
            
            // Surface colors
            surface = Color(0xFFFFFFFF),
            onSurface = Color(0xFF0B1120),
            surfaceVariant = Color(0xFFF1F5F9),
            onSurfaceVariant = Color(0xFF475569),
            
            // Outline colors
            outline = Color(0xFFCBD5E1),
            outlineVariant = Color(0xFFE2E8F0),
            
            // Inverse colors
            inverseSurface = Color(0xFF1A2438),
            inverseOnSurface = Color(0xFFE8F4F8),
            inversePrimary = context.primaryColor(),
            
            // Scrim for modals
            scrim = Color(0xFF000000).copy(alpha = 0.5f)
        )
    }
    
    MaterialTheme(
        colorScheme = colorScheme,
        typography = JABAuthTypography,
        content = content
    )
}
