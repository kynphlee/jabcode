package com.jabauth.diagnostic

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.jabauth.diagnostic.navigation.BottomNavItem
import com.jabauth.diagnostic.navigation.NavigationGraph

/**
 * Main diagnostic app composable
 * 
 * Provides navigation structure with bottom navigation bar
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticApp() {
    val navController = rememberNavController()
    
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                
                BottomNavItem.items.forEach { item ->
                    NavigationBarItem(
                        icon = { 
                            Icon(
                                imageVector = getIconForRoute(item.icon),
                                contentDescription = item.label
                            )
                        },
                        label = { Text(item.label) },
                        selected = currentDestination?.hierarchy?.any { 
                            it.route == item.route 
                        } == true,
                        onClick = {
                            navController.navigate(item.route) {
                                // Pop up to start destination to avoid large back stack
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                // Avoid multiple copies of same destination
                                launchSingleTop = true
                                // Restore state when reselecting item
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavigationGraph(
            navController = navController,
            modifier = Modifier.padding(innerPadding)
        )
    }
}

/**
 * Map icon name to Material Icon
 * 
 * @param iconName Icon identifier string
 * @return Corresponding Material Icon
 */
private fun getIconForRoute(iconName: String): ImageVector {
    return when (iconName) {
        "dashboard" -> Icons.Default.Dashboard
        "qr_code_scanner" -> Icons.Default.QrCodeScanner
        "error" -> Icons.Default.Error
        "science" -> Icons.Default.Science
        "settings" -> Icons.Default.Settings
        else -> Icons.Default.Info
    }
}
