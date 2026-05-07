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
import com.jabauth.ui.scanner.*

/**
 * Scanner Screen - JABCode scanner with CameraX
 * 
 * Phase 3 Day 1-5: Scanner UI + Integration Complete
 * - CameraX live preview with JABCode decoding
 * - Runtime permissions (accompanist)
 * - Real-time quality metrics (brightness, focus, contrast)
 * - Scan target overlay with animations
 * - Scan status overlay
 * - Result Panel (bottom sheet) for scan results
 * - JABCode SDK integration for real decoding
 * - JABAuth Client integration for authentication
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
    val authenticationResult by viewModel.authenticationResult.collectAsState()
    
    // Create JABCode analyzer with full decode + auth pipeline
    val jabCodeAnalyzer = remember {
        viewModel.createAnalyzer()
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
                        // Camera preview layer with JABCode decoding
                        CameraPreview(
                            imageAnalyzer = jabCodeAnalyzer,
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
        
        // Test buttons for Result Panel (Phase 3 Day 4 development)
        // TODO: Remove in Phase 3 Day 5 when real scanning is integrated
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 1.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { viewModel.showMockSuccessResult() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary
                    )
                ) {
                    Text("Test Success", style = MaterialTheme.typography.labelSmall)
                }
                
                Button(
                    onClick = { viewModel.showMockFailureResult() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Test Failure", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
    
    // Result Panel (bottom sheet)
    ResultPanel(
        result = authenticationResult,
        onDismiss = { viewModel.dismissResult() },
        onAccept = { viewModel.acceptResult() },
        onScanAgain = { viewModel.dismissResult() }
    )
}
