package com.jabauth.diagnostic.navigation

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for diagnostic app navigation
 * 
 * Tests route definitions and navigation structure
 */
class NavigationTest {
    
    @Test
    fun routes_allScreensHaveUniqueRoutes() {
        val routes = setOf(
            Routes.Dashboard,
            Routes.Scanner,
            Routes.CameraDetailRoute,
            Routes.ErrorLog,
            Routes.CaptureTest,
            Routes.Settings,
            Routes.ErrorState
        )
        
        assertEquals("All routes should be unique", 7, routes.size)
    }
    
    @Test
    fun routes_dashboardIsDefault() {
        assertEquals("dashboard", Routes.Dashboard)
    }
    
    @Test
    fun routes_scannerRoute() {
        assertEquals("scanner", Routes.Scanner)
    }
    
    @Test
    fun routes_cameraDetailWithParameter() {
        val cameraId = "0"
        val route = Routes.cameraDetail(cameraId)
        assertEquals("camera_detail/0", route)
    }
    
    @Test
    fun routes_errorLogRoute() {
        assertEquals("error_log", Routes.ErrorLog)
    }
    
    @Test
    fun routes_captureTestRoute() {
        assertEquals("capture_test", Routes.CaptureTest)
    }
    
    @Test
    fun routes_settingsRoute() {
        assertEquals("settings", Routes.Settings)
    }
    
    @Test
    fun routes_errorStateRoute() {
        assertEquals("error_state", Routes.ErrorState)
    }
    
    @Test
    fun bottomNavItems_hasFiveItems() {
        val items = BottomNavItem.items
        assertEquals("Bottom nav should have 5 items", 5, items.size)
    }
    
    @Test
    fun bottomNavItems_containsDashboard() {
        val items = BottomNavItem.items
        assertTrue("Bottom nav should contain Dashboard", 
            items.any { it.route == Routes.Dashboard })
    }
    
    @Test
    fun bottomNavItems_containsScanner() {
        val items = BottomNavItem.items
        assertTrue("Bottom nav should contain Scanner",
            items.any { it.route == Routes.Scanner })
    }
    
    @Test
    fun bottomNavItems_allHaveLabels() {
        val items = BottomNavItem.items
        assertTrue("All items should have non-empty labels",
            items.all { it.label.isNotEmpty() })
    }
}
