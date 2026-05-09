package com.jabauth.diagnostic.navigation

/**
 * Navigation route definitions for diagnostic app
 * 
 * Defines all screen routes and navigation paths
 */
object Routes {
    /** Dashboard home screen (default) */
    const val Dashboard = "dashboard"
    
    /** JABCode scanner with live preview */
    const val Scanner = "scanner"
    
    /** Camera detail characteristics inspector */
    const val CameraDetailRoute = "camera_detail/{cameraId}"
    
    /** Error log viewer */
    const val ErrorLog = "error_log"
    
    /** Stream capture test screen */
    const val CaptureTest = "capture_test"
    
    /** App settings */
    const val Settings = "settings"
    
    /** Error state display */
    const val ErrorState = "error_state"
    
    /**
     * Create camera detail route with specific camera ID
     * 
     * @param cameraId Camera identifier
     * @return Complete route path
     */
    fun cameraDetail(cameraId: String): String {
        return "camera_detail/$cameraId"
    }
}

/**
 * Bottom navigation bar item definition
 * 
 * @property route Navigation route
 * @property label Display label
 * @property icon Icon resource name (to be mapped to actual icons)
 */
data class BottomNavItem(
    val route: String,
    val label: String,
    val icon: String
) {
    companion object {
        /**
         * Bottom navigation items (5 primary screens)
         */
        val items = listOf(
            BottomNavItem(
                route = Routes.Dashboard,
                label = "Dashboard",
                icon = "dashboard"
            ),
            BottomNavItem(
                route = Routes.Scanner,
                label = "Scanner",
                icon = "qr_code_scanner"
            ),
            BottomNavItem(
                route = Routes.ErrorLog,
                label = "Errors",
                icon = "error"
            ),
            BottomNavItem(
                route = Routes.CaptureTest,
                label = "Test",
                icon = "science"
            ),
            BottomNavItem(
                route = Routes.Settings,
                label = "Settings",
                icon = "settings"
            )
        )
    }
}
