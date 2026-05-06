package com.jabauth.diagnostic.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.jabauth.diagnostic.ui.dashboard.DashboardScreen
import com.jabauth.diagnostic.ui.scanner.ScannerScreen
import com.jabauth.diagnostic.ui.settings.SettingsScreen

/**
 * Navigation host for Diagnostic App with bottom navigation
 * 
 * Defines navigation graph between Dashboard, Scanner, and Settings screens.
 * Includes bottom navigation bar for easy screen switching.
 */
@Composable
fun DiagnosticNavHost(
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()
    
    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                
                bottomNavItems.forEach { item ->
                    NavigationBarItem(
                        icon = {
                            if (item.icon != null) {
                                Icon(item.icon, contentDescription = item.label)
                            } else {
                                Text(item.emoji, style = MaterialTheme.typography.titleLarge)
                            }
                        },
                        label = { Text(item.label) },
                        selected = currentDestination?.hierarchy?.any { it.route == item.route } == true,
                        onClick = {
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        modifier = Modifier.testTag("nav_${item.label.lowercase()}")
                    )
                }
            }
        },
        modifier = modifier
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = DiagnosticDestination.Dashboard.route,
            modifier = Modifier.padding(paddingValues)
        ) {
            composable(
                route = DiagnosticDestination.Dashboard.route,
                enterTransition = { EnterTransition.None },
                exitTransition = { ExitTransition.None },
                popEnterTransition = { EnterTransition.None },
                popExitTransition = { ExitTransition.None }
            ) {
                DashboardScreen(
                    onNavigateToScanner = {
                        navController.navigate(DiagnosticDestination.Scanner.route)
                    },
                    onNavigateToSettings = {
                        navController.navigate(DiagnosticDestination.Settings.route)
                    }
                )
            }
            
            composable(
                route = DiagnosticDestination.Scanner.route,
                enterTransition = { EnterTransition.None },
                exitTransition = { ExitTransition.None },
                popEnterTransition = { EnterTransition.None },
                popExitTransition = { ExitTransition.None }
            ) {
                ScannerScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    onNavigateToDashboard = {
                        navController.navigate(DiagnosticDestination.Dashboard.route)
                    }
                )
            }
            
            composable(
                route = DiagnosticDestination.Settings.route,
                enterTransition = { EnterTransition.None },
                exitTransition = { ExitTransition.None },
                popEnterTransition = { EnterTransition.None },
                popExitTransition = { ExitTransition.None }
            ) {
                SettingsScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }
        }
    }
}

/**
 * Bottom navigation item definition
 */
private data class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector? = null,
    val emoji: String = ""
)

/**
 * Bottom navigation items
 */
private val bottomNavItems = listOf(
    BottomNavItem(
        route = DiagnosticDestination.Dashboard.route,
        label = "Dashboard",
        icon = Icons.Default.Home
    ),
    BottomNavItem(
        route = DiagnosticDestination.Scanner.route,
        label = "Scanner",
        emoji = "📷"
    ),
    BottomNavItem(
        route = DiagnosticDestination.Settings.route,
        label = "Settings",
        icon = Icons.Default.Settings
    )
)
