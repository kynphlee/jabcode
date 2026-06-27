package com.jabauth.diagnostic.ui.dashboard

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jabauth.jabcode.camera.CameraDeviceProfiler
import com.jabauth.ui.components.Badge
import com.jabauth.ui.components.JABAuthCard
import com.jabauth.ui.theme.JABAuthBgBase
import com.jabauth.ui.theme.JABAuthBgCard
import com.jabauth.ui.theme.JABAuthBgHover
import com.jabauth.ui.theme.JABAuthBorder
import com.jabauth.ui.theme.JABAuthError
import com.jabauth.ui.theme.JABAuthPrimary
import com.jabauth.ui.theme.JABAuthSuccess
import com.jabauth.ui.theme.JABAuthTextPrimary
import com.jabauth.ui.theme.JABAuthTextSecondary
import com.jabauth.ui.theme.JABAuthWarning
import com.jabauth.ui.theme.Spacing
import kotlinx.coroutines.delay

/**
 * Dashboard screen - Device overview and camera enumeration
 *
 * Displays:
 * - Device summary (total cameras by type)
 * - List of all camera devices with capabilities
 * - Hardware level indicators
 *
 * Styled with the JABAuth design system (DESIGN_SYSTEM.md v1.0.0): stepped navy
 * surfaces, neon status accents, and a staggered reveal as the list paints in.
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

// ---------------------------------------------------------------------------
// Motion — DESIGN_SYSTEM.md v1.0.0 "Motion System"
// ---------------------------------------------------------------------------

/** Entrance-animation duration (ms). Spec: Slow tier for entrances. */
private const val AnimDurationSlow = 600
/** Per-index stagger delay (ms) for the card reveal. */
private const val StaggerStepMs = 100L
/** Decelerate easing (enter screen): cubic-bezier(0.0, 0.0, 0.2, 1.0). */
private val DecelerateEasing = CubicBezierEasing(0.0f, 0.0f, 0.2f, 1f)
/** Upward slide distance for the entrance (24dp, expressed in px at runtime). */
private val RevealSlideOffset = 24.dp

/**
 * Staggered card entrance per the motion spec: each item fades in and slides up
 * 24dp over 600ms with Decelerate easing, delayed by 100ms * [index] so a list
 * paints top-to-bottom.
 */
@Composable
private fun StaggeredCardReveal(
    index: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    val slidePx = with(LocalDensity.current) { RevealSlideOffset.roundToPx() }

    LaunchedEffect(Unit) {
        delay(index * StaggerStepMs)
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(
            animationSpec = tween(
                durationMillis = AnimDurationSlow,
                easing = DecelerateEasing
            )
        ) + slideInVertically(
            initialOffsetY = { slidePx },
            animationSpec = tween(
                durationMillis = AnimDurationSlow,
                easing = DecelerateEasing
            )
        )
    ) {
        content()
    }
}

@Composable
private fun LoadingView(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize().background(JABAuthBgBase),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = JABAuthPrimary)
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
            .background(JABAuthBgBase)
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        // Device Summary Section (reveal index 0).
        item {
            StaggeredCardReveal(index = 0) {
                DeviceSummaryCard(summary = uiState.deviceSummary)
            }
        }

        // Camera List header (reveal index 1).
        item {
            StaggeredCardReveal(index = 1) {
                Text(
                    text = "Cameras",
                    style = MaterialTheme.typography.headlineMedium,
                    color = JABAuthTextPrimary
                )
            }
        }

        // Camera cards continue the stagger after the summary + header.
        itemsIndexed(uiState.cameras) { index, camera ->
            StaggeredCardReveal(index = index + 2) {
                CameraCard(
                    profile = camera,
                    onClick = { onCameraClick(camera.cameraId) }
                )
            }
        }
    }
}

@Composable
private fun DeviceSummaryCard(
    summary: DeviceSummary,
    modifier: Modifier = Modifier
) {
    JABAuthCard(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Device Summary",
            style = MaterialTheme.typography.headlineSmall,
            color = JABAuthTextPrimary
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Spacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            SummaryItem(label = "Total Cameras", count = summary.totalCameras)
            SummaryItem(label = "Back", count = summary.backCameras)
            SummaryItem(label = "Front", count = summary.frontCameras)
            SummaryItem(label = "External", count = summary.externalCameras)
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
            style = MaterialTheme.typography.displaySmall,
            color = JABAuthPrimary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = JABAuthTextSecondary
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
    // Clickable card matching the JABAuthCard spec (card bg, 1dp border, 12dp
    // radius, 24dp padding, no elevation) plus the spec's hover state: bg shifts
    // to hover and the card lifts 2dp.
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val density = LocalDensity.current

    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .hoverable(interactionSource)
            .graphicsLayer {
                translationY = if (isHovered) with(density) { -2.dp.toPx() } else 0f
            },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isHovered) JABAuthBgHover else JABAuthBgCard
        ),
        border = BorderStroke(1.dp, JABAuthBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Camera ${profile.cameraId}",
                    style = MaterialTheme.typography.headlineSmall,
                    color = JABAuthTextPrimary
                )

                Badge(
                    text = profile.hardwareLevel.name,
                    color = hardwareLevelColor(profile.hardwareLevel)
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                Badge(text = profile.facing.name, color = JABAuthPrimary)

                if (profile.supportsManualFocus) {
                    Badge(text = "Manual Focus", color = JABAuthSuccess)
                }

                if (profile.supportsManualExposure) {
                    Badge(text = "Manual Exposure", color = JABAuthSuccess)
                }
            }
        }
    }
}

/**
 * Maps a camera hardware level onto a JABAuth status accent: higher capability
 * reads as a healthier (greener) state.
 */
private fun hardwareLevelColor(level: CameraDeviceProfiler.HardwareLevel): Color =
    when (level) {
        CameraDeviceProfiler.HardwareLevel.LEVEL_3 -> JABAuthSuccess
        CameraDeviceProfiler.HardwareLevel.FULL -> JABAuthPrimary
        CameraDeviceProfiler.HardwareLevel.LIMITED -> JABAuthWarning
        else -> JABAuthError
    }
