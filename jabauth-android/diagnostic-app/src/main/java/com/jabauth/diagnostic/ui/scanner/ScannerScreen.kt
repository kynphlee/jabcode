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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jabauth.diagnostic.camera.CameraAnalyzer
import com.jabauth.ui.scanner.QualityIndicator
import com.jabauth.ui.scanner.QualityIndicators
import com.jabauth.ui.scanner.ScanStatus
import com.jabauth.ui.scanner.ScanStatusOverlay
import com.jabauth.ui.scanner.ScanTargetOverlay
import com.jabauth.ui.scanner.ScannerHeader

/**
 * Scanner Screen - JABCode scanner with CameraX
 * 
 * Phase 3 Day 1-3: Camera Integration + Quality Indicators
 * - CameraX live preview with image analysis
 * - Runtime permissions (accompanist)
 * - Real-time quality metrics (brightness, focus, contrast)
 * - Scan target overlay with animations
 * - Scan status overlay
 * 
 * Testing: Camera permission auto-granted via GrantPermissionRule in tests
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ScannerScreen(
    onNavigateBack: () -> Unit,
    onNavigateToDashboard: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ScannerViewModel = viewModel()
) {
    val cameraPermissionState = rememberPermissionState(android.Manifest.permission.CAMERA)
    val scanStatus by viewModel.scanStatus.collectAsState()
    val isTorchOn by viewModel.isTorchOn.collectAsState()
    val qualityMetrics by viewModel.qualityMetrics.collectAsState()
    
    // Create camera analyzer for quality metrics
    val cameraAnalyzer = remember {
        CameraAnalyzer { brightness, focus, contrast ->
            viewModel.updateQualityMetrics(brightness, focus, contrast)
        }
    }
    
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
                        // Camera preview layer with image analysis
                        CameraPreview(
                            imageAnalyzer = cameraAnalyzer,
                            isTorchOn = isTorchOn,
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
                        
                        // Quality indicators overlay at bottom
                        QualityIndicators(
                            metrics = qualityMetrics,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 20.dp)
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
        
        // Action buttons
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { viewModel.toggleTorch() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (isTorchOn) "Torch Off" else "Torch On")
                }
                
                Button(
                    onClick = onNavigateToDashboard,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Dashboard")
                }
            }
        }
    }
}
