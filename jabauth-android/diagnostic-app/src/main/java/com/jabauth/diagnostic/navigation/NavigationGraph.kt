package com.jabauth.diagnostic.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.jabauth.diagnostic.ui.dashboard.DashboardScreen
import com.jabauth.diagnostic.ui.camera.CameraDetailScreen
import com.jabauth.diagnostic.ui.scanner.ScannerScreen
import com.jabauth.diagnostic.ui.errorlog.ErrorLogScreen
import com.jabauth.diagnostic.ui.capturetest.CaptureTestScreen
import com.jabauth.diagnostic.ui.settings.SettingsScreen
import com.jabauth.diagnostic.ui.errorstate.ErrorStateScreen

/**
 * Main navigation graph for diagnostic app
 * 
 * Defines navigation structure and screen destinations
 * 
 * @param navController Navigation controller for app navigation
 * @param modifier Modifier for layout customization
 * @param startDestination Initial screen route (default: Dashboard)
 */
@Composable
fun NavigationGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    startDestination: String = Routes.Dashboard
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        // Dashboard - Device overview and camera enumeration
        composable(Routes.Dashboard) {
            DashboardScreen(
                onCameraClick = { cameraId ->
                    navController.navigate(Routes.cameraDetail(cameraId))
                }
            )
        }
        
        // Scanner - JABCode scanning with live preview
        composable(Routes.Scanner) {
            ScannerScreen()
        }
        
        // Camera Detail - Deep-dive characteristics inspector
        composable(
            route = Routes.CameraDetailRoute,
            arguments = listOf(
                navArgument("cameraId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val cameraId = backStackEntry.arguments?.getString("cameraId") ?: "0"
            CameraDetailScreen(
                cameraId = cameraId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        // Error Log - Timestamped error history
        composable(Routes.ErrorLog) {
            ErrorLogScreen()
        }
        
        // Capture Test - Stream validation and testing
        composable(Routes.CaptureTest) {
            CaptureTestScreen()
        }
        
        // Settings - App configuration
        composable(Routes.Settings) {
            SettingsScreen()
        }
        
        // Error State - Error display screen
        composable(Routes.ErrorState) {
            ErrorStateScreen(
                onRetry = { /* Retry logic */ },
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
