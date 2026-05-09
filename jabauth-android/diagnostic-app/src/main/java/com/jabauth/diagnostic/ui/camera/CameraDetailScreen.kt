package com.jabauth.diagnostic.ui.camera

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jabauth.jabcode.camera.CameraDeviceProfiler

/**
 * Camera Detail screen - Deep-dive characteristics inspector
 * 
 * Displays comprehensive camera information:
 * - Hardware level and facing direction
 * - Sensor characteristics (size, resolution, orientation)
 * - Manual control capabilities (focus, exposure)
 * - ISO and exposure ranges
 * - Focus calibration status
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraDetailScreen(
    cameraId: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    context: Context = LocalContext.current
) {
    val profiler = CameraDeviceProfiler(context)
    val profile = profiler.getProfileById(cameraId)
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Camera $cameraId") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        },
        modifier = modifier
    ) { padding ->
        if (profile == null) {
            EmptyState(
                message = "Camera not found",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )
        } else {
            CameraDetailContent(
                profile = profile,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )
        }
    }
}

@Composable
private fun EmptyState(
    message: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CameraDetailContent(
    profile: CameraDeviceProfiler.DeviceProfile,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hardware Overview Section
        item {
            DetailSection(title = "Hardware Overview") {
                DetailRow(label = "Camera ID", value = profile.cameraId)
                DetailRow(label = "Hardware Level", value = profile.hardwareLevel.name)
                DetailRow(label = "Facing", value = profile.facing.name)
            }
        }
        
        // Sensor Characteristics Section
        item {
            DetailSection(title = "Sensor Characteristics") {
                profile.physicalSize?.let { (width, height) ->
                    DetailRow(
                        label = "Sensor Size",
                        value = String.format("%.2f × %.2f mm", width, height)
                    )
                }
                profile.pixelArraySize?.let { (width, height) ->
                    DetailRow(
                        label = "Pixel Array",
                        value = "$width × $height"
                    )
                }
                DetailRow(
                    label = "Sensor Orientation",
                    value = "${profile.sensorOrientation}°"
                )
            }
        }
        
        // Manual Controls Section
        item {
            DetailSection(title = "Manual Controls") {
                DetailRow(
                    label = "Manual Focus",
                    value = if (profile.supportsManualFocus) "Supported" else "Not Supported"
                )
                DetailRow(
                    label = "Manual Exposure",
                    value = if (profile.supportsManualExposure) "Supported" else "Not Supported"
                )
                DetailRow(
                    label = "Manual ISO",
                    value = if (profile.supportsManualISO) "Supported" else "Not Supported"
                )
                
                if (profile.supportsManualFocus) {
                    DetailRow(
                        label = "Focus Calibration",
                        value = profile.focusDistanceCalibration.name
                    )
                }
            }
        }
        
        // Exposure & ISO Section
        if (profile.supportsManualExposure) {
            item {
                DetailSection(title = "Exposure & ISO") {
                    profile.exposureTimeRange?.let { (min, max) ->
                        DetailRow(
                            label = "Exposure Time Range",
                            value = formatExposureRange(min, max)
                        )
                    }
                    profile.isoRange?.let { (min, max) ->
                        DetailRow(
                            label = "ISO Range",
                            value = "$min - $max"
                        )
                    }
                }
            }
        }
        
        // Additional Capabilities Section
        item {
            DetailSection(title = "Additional Capabilities") {
                DetailRow(
                    label = "Auto Exposure",
                    value = "Supported"
                )
                DetailRow(
                    label = "Auto Focus",
                    value = "Supported"
                )
            }
        }
    }
}

@Composable
private fun DetailSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Divider()
            
            content()
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun formatExposureRange(min: Long, max: Long): String {
    return when {
        max < 1000 -> "$min - $max ns"
        max < 1_000_000 -> "${min / 1000} - ${max / 1000} μs"
        max < 1_000_000_000 -> "${min / 1_000_000} - ${max / 1_000_000} ms"
        else -> "${min / 1_000_000_000} - ${max / 1_000_000_000} s"
    }
}
