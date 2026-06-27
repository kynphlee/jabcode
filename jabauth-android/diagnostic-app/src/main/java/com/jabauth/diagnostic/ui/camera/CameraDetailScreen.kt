package com.jabauth.diagnostic.ui.camera

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.jabauth.jabcode.camera.CameraDeviceProfiler
import com.jabauth.ui.components.Badge
import com.jabauth.ui.components.JABAuthCard
import com.jabauth.ui.theme.JABAuthBgBase
import com.jabauth.ui.theme.JABAuthBorder
import com.jabauth.ui.theme.JABAuthError
import com.jabauth.ui.theme.JABAuthSuccess
import com.jabauth.ui.theme.JABAuthTextDim
import com.jabauth.ui.theme.JABAuthTextPrimary
import com.jabauth.ui.theme.JABAuthTextSecondary
import com.jabauth.ui.theme.Spacing
import kotlinx.coroutines.delay

/**
 * Camera Detail screen - Deep-dive characteristics inspector
 *
 * Displays comprehensive camera information:
 * - Hardware level and facing direction
 * - Sensor characteristics (size, resolution, orientation)
 * - Manual control capabilities (focus, exposure)
 * - ISO and exposure ranges
 * - Focus calibration status
 *
 * Read-only sensor-spec surface styled to the JABAuth design system
 * (DESIGN_SYSTEM.md v1.0.0): each spec section is a [JABAuthCard], capability
 * flags read as [Badge]s, and the section list reveals with the staggered
 * entrance from the motion spec.
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
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = JABAuthBgBase,
                    titleContentColor = JABAuthTextPrimary,
                    navigationIconContentColor = JABAuthTextPrimary
                )
            )
        },
        containerColor = JABAuthBgBase,
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
            color = JABAuthTextSecondary
        )
    }
}

@Composable
private fun CameraDetailContent(
    profile: CameraDeviceProfiler.DeviceProfile,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        // Sections are revealed with a staggered entrance; the running index
        // drives the per-card delay so each card animates in 100ms after the
        // previous one.
        var revealIndex = 0

        // Hardware Overview Section
        item {
            StaggeredReveal(index = revealIndex++) {
                DetailSection(title = "Hardware Overview") {
                    DetailRow(label = "Camera ID", value = profile.cameraId)
                    DetailRow(label = "Hardware Level", value = profile.hardwareLevel.name)
                    DetailRow(label = "Facing", value = profile.facing.name)
                }
            }
        }

        // Sensor Characteristics Section
        item {
            StaggeredReveal(index = revealIndex++) {
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
        }

        // Manual Controls Section
        item {
            StaggeredReveal(index = revealIndex++) {
                DetailSection(title = "Manual Controls") {
                    CapabilityRow(label = "Manual Focus", supported = profile.supportsManualFocus)
                    CapabilityRow(label = "Manual Exposure", supported = profile.supportsManualExposure)
                    CapabilityRow(label = "Manual ISO", supported = profile.supportsManualISO)

                    if (profile.supportsManualFocus) {
                        DetailRow(
                            label = "Focus Calibration",
                            value = profile.focusDistanceCalibration.name
                        )
                    }
                }
            }
        }

        // Exposure & ISO Section
        if (profile.supportsManualExposure) {
            item {
                StaggeredReveal(index = revealIndex++) {
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
        }

        // Additional Capabilities Section
        item {
            StaggeredReveal(index = revealIndex++) {
                DetailSection(title = "Additional Capabilities") {
                    CapabilityRow(label = "Auto Exposure", supported = true)
                    CapabilityRow(label = "Auto Focus", supported = true)
                }
            }
        }
    }
}

/**
 * Staggered entrance wrapper (DESIGN_SYSTEM.md v1.0.0 Motion System).
 *
 * Fades and slides each item up 24dp over 600ms with the Decelerate easing,
 * delayed 100ms per [index] so list/card items reveal in sequence.
 */
@Composable
private fun StaggeredReveal(
    index: Int,
    content: @Composable () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(index * STAGGER_DELAY_MS)
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(
            animationSpec = tween(
                durationMillis = REVEAL_DURATION_MS,
                easing = DecelerateEasing
            )
        ) + slideInVertically(
            initialOffsetY = { SLIDE_OFFSET_PX },
            animationSpec = tween(
                durationMillis = REVEAL_DURATION_MS,
                easing = DecelerateEasing
            )
        )
    ) {
        content()
    }
}

@Composable
private fun DetailSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    JABAuthCard(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = JABAuthTextPrimary
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .padding(vertical = Spacing.sm)
                .background(JABAuthBorder)
        )

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
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
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = JABAuthTextSecondary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = JABAuthTextPrimary
        )
    }
}

/**
 * A [DetailRow] whose value is a [Badge] reading the capability state:
 * green "SUPPORTED" vs. a dim "NOT SUPPORTED".
 */
@Composable
private fun CapabilityRow(
    label: String,
    supported: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = JABAuthTextSecondary
        )
        Badge(
            text = if (supported) "Supported" else "Not Supported",
            color = if (supported) JABAuthSuccess else JABAuthTextDim
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

// --- Motion spec constants (DESIGN_SYSTEM.md v1.0.0 Motion System) ----------

/** 100ms per index — staggered entrance cadence. */
private const val STAGGER_DELAY_MS = 100L
/** 600ms — "Slow" entrance duration. */
private const val REVEAL_DURATION_MS = 600
/** 24dp upward slide, expressed as the px offset the spec passes literally. */
private const val SLIDE_OFFSET_PX = 24

/** Decelerate easing — enter-screen curve (0.0, 0.0, 0.2, 1.0). */
private val DecelerateEasing = CubicBezierEasing(0.0f, 0.0f, 0.2f, 1.0f)
