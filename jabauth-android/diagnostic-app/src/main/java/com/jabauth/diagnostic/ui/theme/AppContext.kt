package com.jabauth.diagnostic.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Application contexts with different color schemes
 * 
 * Each context represents a different use case domain:
 * - Healthcare: Medical device authentication
 * - Legal: Document verification
 * - IoT: Device-to-device communication
 */
enum class AppContext {
    Healthcare,
    Legal,
    IoT;
    
    /**
     * Primary brand color for this context
     */
    fun primaryColor(): Color = when (this) {
        Healthcare -> Color(0xFF00C9A7)  // Teal - medical/health
        Legal -> Color(0xFF4A90E2)       // Blue - trust/authority
        IoT -> Color(0xFFFF6B6B)         // Red - connectivity/alert
    }
    
    /**
     * Dimmed version of primary color for containers
     */
    fun primaryDim(): Color = when (this) {
        Healthcare -> Color(0xFF008B73)  // Darker teal
        Legal -> Color(0xFF2E5A8E)       // Darker blue
        IoT -> Color(0xFFCC4646)         // Darker red
    }
    
    /**
     * Secondary accent color
     */
    fun secondaryColor(): Color = when (this) {
        Healthcare -> Color(0xFF845EC2)  // Purple accent
        Legal -> Color(0xFF50C878)       // Green accent
        IoT -> Color(0xFFFFC75F)         // Orange accent
    }
}
