package com.jabauth.diagnostic.ui.dashboard

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jabauth.jabcode.camera.CameraDeviceProfiler

/**
 * Dashboard screen - Device overview and camera enumeration
 * 
 * Displays:
 * - Device summary (total cameras by type)
 * - List of all camera devices with capabilities
 * - Hardware level indicators
 */
@Composable
fun DashboardScreen(
    onCameraClick: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    context: Context = LocalContext.current,
    viewModel: DashboardViewModel = viewModel {
        val profiler = CameraDeviceProfiler(context)
        DashboardViewModel { profiler.getAllCameraProfiles() }
    }
) {
    val uiState by viewModel.uiState.collectAsState()
    
    // Load cameras on first composition
    LaunchedEffect(Unit) {
        viewModel.loadCameras()
    }
    
    if (uiState.isLoading) {
        LoadingView(modifier = modifier)
    } else {
        DashboardContent(
            uiState = uiState,
            onCameraClick = onCameraClick,
            modifier = modifier
        )
    }
}

@Composable
private fun LoadingView(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun DashboardContent(
    uiState: DashboardUiState,
    onCameraClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Device Summary Section
        item {
            DeviceSummaryCard(summary = uiState.deviceSummary)
        }
        
        // Camera List Section
        item {
            Text(
                text = "Cameras",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }
        
        items(uiState.cameras) { camera ->
            CameraCard(
                profile = camera,
                onClick = { onCameraClick(camera.cameraId) }
            )
        }
    }
}

@Composable
private fun DeviceSummaryCard(
    summary: DeviceSummary,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Device Summary",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SummaryItem(label = "Total Cameras", count = summary.totalCameras)
                SummaryItem(label = "Back", count = summary.backCameras)
                SummaryItem(label = "Front", count = summary.frontCameras)
                SummaryItem(label = "External", count = summary.externalCameras)
            }
        }
    }
}

@Composable
private fun SummaryItem(
    label: String,
    count: Int,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CameraCard(
    profile: CameraDeviceProfiler.DeviceProfile,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Camera ${profile.cameraId}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                HardwareLevelBadge(level = profile.hardwareLevel)
            }
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FacingBadge(facing = profile.facing)
                
                if (profile.supportsManualFocus) {
                    Chip(text = "Manual Focus")
                }
                
                if (profile.supportsManualExposure) {
                    Chip(text = "Manual Exposure")
                }
            }
        }
    }
}

@Composable
private fun HardwareLevelBadge(
    level: CameraDeviceProfiler.HardwareLevel,
    modifier: Modifier = Modifier
) {
    val color = when (level) {
        CameraDeviceProfiler.HardwareLevel.LEVEL_3 -> MaterialTheme.colorScheme.primary
        CameraDeviceProfiler.HardwareLevel.FULL -> MaterialTheme.colorScheme.secondary
        CameraDeviceProfiler.HardwareLevel.LIMITED -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    
    Surface(
        color = color,
        shape = MaterialTheme.shapes.small,
        modifier = modifier
    ) {
        Text(
            text = level.name,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun FacingBadge(
    facing: CameraDeviceProfiler.Facing,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = MaterialTheme.shapes.small,
        modifier = modifier
    ) {
        Text(
            text = facing.name,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun Chip(
    text: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.small,
        modifier = modifier
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall
        )
    }
}
