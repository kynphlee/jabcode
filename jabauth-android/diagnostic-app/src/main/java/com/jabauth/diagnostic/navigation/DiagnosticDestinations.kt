package com.jabauth.diagnostic.navigation

/**
 * Navigation destinations for Diagnostic App
 * 
 * Defines routes for the three main screens.
 */
sealed class DiagnosticDestination(val route: String) {
    
    /**
     * Dashboard screen - performance monitoring and module health
     */
    data object Dashboard : DiagnosticDestination("dashboard")
    
    /**
     * Scanner screen - JABCode scanning and authentication
     */
    data object Scanner : DiagnosticDestination("scanner")
    
    /**
     * Settings screen - app configuration
     */
    data object Settings : DiagnosticDestination("settings")
}
