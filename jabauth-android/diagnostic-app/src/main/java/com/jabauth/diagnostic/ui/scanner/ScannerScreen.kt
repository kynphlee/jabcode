package com.jabauth.diagnostic.ui.scanner

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.jabauth.ui.scanner.QualityIndicator
import com.jabauth.ui.scanner.ScanStatus
import com.jabauth.ui.scanner.ScanStatusOverlay
import com.jabauth.ui.scanner.ScanTargetOverlay
import com.jabauth.ui.scanner.ScannerHeader

/**
 * Scanner Screen - JABCode scanner with CameraX
 * 
 * Phase 3 Day 1: Camera Integration
 * - CameraX live preview
 * - Runtime permissions (accompanist)
 * - Quality indicators
 * - Scan status overlay
 * 
 * Testing: Camera permission auto-granted via GrantPermissionRule in tests
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ScannerScreen(
    onNavigateBack: () -> Unit,
    onNavigateToDashboard: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cameraPermissionState = rememberPermissionState(android.Manifest.permission.CAMERA)
    var scanStatus by remember { mutableStateOf(ScanStatus.SCANNING) }
    
    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // Using UI components from framework
        ScannerHeader(
            title = "JABCode Scanner",
            onBackClick = onNavigateBack,
            onSettingsClick = { /* Settings would open here */ }
        )
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            when {
                cameraPermissionState.status.isGranted -> {
                    // Camera permission granted - show live preview with scan target
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(4f/3f),
                        contentAlignment = Alignment.Center
                    ) {
                        // Camera preview layer
                        CameraPreview(
                            modifier = Modifier.fillMaxSize()
                        )
                        
                        // Scan target overlay
                        ScanTargetOverlay(
                            size = 280.dp,
                            isScanning = scanStatus == ScanStatus.SCANNING,
                            isDetected = scanStatus == ScanStatus.SUCCESS,
                            primaryColor = MaterialTheme.colorScheme.primary,
                            successColor = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
                cameraPermissionState.status.shouldShowRationale -> {
                    // Show rationale
                    CameraPermissionRationale(
                        onRequestPermission = { cameraPermissionState.launchPermissionRequest() }
                    )
                }
                else -> {
                    // Permission not yet requested - show initial state
                    CameraPermissionDenied(
                        onRequestPermission = { cameraPermissionState.launchPermissionRequest() }
                    )
                }
            }
            
            // Status overlay from UI components
            ScanStatusOverlay(
                status = scanStatus,
                message = when (scanStatus) {
                    ScanStatus.SCANNING -> "Position JABCode in frame"
                    ScanStatus.SUCCESS -> "Scan successful!"
                    ScanStatus.ERROR -> "Scan failed"
                    ScanStatus.WARNING -> "Poor quality detected"
                }
            )
        }
        
        // Quality indicators from UI components
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Quality Metrics",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                
                QualityIndicator(
                    label = "Brightness",
                    value = 0.75f
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                QualityIndicator(
                    label = "Focus",
                    value = 0.90f
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                QualityIndicator(
                    label = "Contrast",
                    value = 0.65f
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Button(
                    onClick = onNavigateToDashboard,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Return to Dashboard")
                }
            }
        }
    }
}
