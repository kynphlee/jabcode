package com.jabauth.diagnostic.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.jabauth.diagnostic.ui.dashboard.DashboardScreen
import com.jabauth.diagnostic.ui.scanner.ScannerScreen
import com.jabauth.diagnostic.ui.settings.SettingsScreen

/**
 * Navigation host for Diagnostic App
 * 
 * Defines navigation graph between Dashboard, Scanner, and Settings screens.
 */
@Composable
fun DiagnosticNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    startDestination: String = DiagnosticDestination.Dashboard.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable(DiagnosticDestination.Dashboard.route) {
            DashboardScreen(
                onNavigateToScanner = {
                    navController.navigate(DiagnosticDestination.Scanner.route)
                },
                onNavigateToSettings = {
                    navController.navigate(DiagnosticDestination.Settings.route)
                }
            )
        }
        
        composable(DiagnosticDestination.Scanner.route) {
            ScannerScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToDashboard = {
                    navController.navigate(DiagnosticDestination.Dashboard.route) {
                        popUpTo(DiagnosticDestination.Dashboard.route) { inclusive = true }
                    }
                }
            )
        }
        
        composable(DiagnosticDestination.Settings.route) {
            SettingsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
