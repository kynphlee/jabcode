package com.jabauth.diagnostic.ui.scanner

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import com.jabauth.diagnostic.ui.verify.AbePolicyScreen
import com.jabauth.diagnostic.ui.verify.CredentialScreen
import com.jabauth.diagnostic.ui.verify.PipelineStageStrip
import com.jabauth.diagnostic.ui.verify.TrustAnchorScreen
import com.jabauth.diagnostic.ui.verify.TrustVerdictBadge
import com.jabauth.diagnostic.verify.AbeDetail
import com.jabauth.diagnostic.verify.CertChainDetail
import com.jabauth.diagnostic.verify.JwtDetail
import com.jabauth.diagnostic.verify.VerificationStage
import com.jabauth.ui.components.Badge
import com.jabauth.ui.theme.JABAuthBgBase
import com.jabauth.ui.theme.JABAuthPrimary
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jabauth.diagnostic.ui.permissions.CameraPermissionHandler
import com.jabauth.ui.scanner.Camera2Preview
import kotlin.math.abs

@Composable
fun ScannerScreen(
    viewModel: ScannerViewModel = viewModel()
) {
    CameraPermissionHandler(
        onPermissionGranted = {
            ScannerScreenContent(viewModel = viewModel)
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScannerScreenContent(
    viewModel: ScannerViewModel
) {
    val scanResult by viewModel.scanResult.collectAsState()
    val verificationResult by viewModel.verificationResult.collectAsState()
    val scanError by viewModel.scanError.collectAsState()
    val aeLocked by viewModel.aeLocked.collectAsState()
    val scanCount by viewModel.scanCount.collectAsState()
    val currentZoom by viewModel.currentZoom.collectAsState()
    val recentStats by viewModel.recentStats.collectAsState()
    val workloadPct by viewModel.workloadPct.collectAsState()
    val decodeTimeStats by viewModel.decodeTimeStats.collectAsState()
    val perNcStats by viewModel.perNcStats.collectAsState()
    val decodeHistory by viewModel.decodeHistory.collectAsState()
    val settings by viewModel.settings.collectAsState(
        initial = com.jabauth.diagnostic.data.SettingsRepository.Settings()
    )

    // Zoom slider is the source of truth for SETTING zoom; pinch updates
    // currentZoom which we fold back into the slider so the two stay in sync.
    var sliderZoom by remember { mutableStateOf(1f) }
    // While the user is actively dragging the slider, don't let the camera's
    // reported (quantized) zoom yank the thumb back — that fight is the jitter.
    var sliderActive by remember { mutableStateOf(false) }
    LaunchedEffect(currentZoom) {
        if (!sliderActive && abs(currentZoom - sliderZoom) > 0.05f) {
            sliderZoom = currentZoom.coerceIn(1f, MAX_ZOOM)
        }
    }
    var panelExpanded by remember { mutableStateOf(false) }
    var drillDownStage by remember { mutableStateOf<VerificationStage?>(null) }
    // True only while the user is actively long-press-resizing the reticle —
    // gates the workload coach (reference-app behaviour: coach shows during
    // resize only, never persistent).
    var isResizing by remember { mutableStateOf(false) }

    // "Scan image" → pick a PNG and decode it camera-free (decoder isolation test).
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { viewModel.decodeImage(it) } }

    // Full-screen camera with floating controls (QR-Scanner layout).
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ScanBg)
    ) {
        Camera2Preview(
            onFrameAvailable = { reader -> viewModel.analyzeFrame(reader) },
            autoFocus = settings.autoFocus,
            exposureCompensation = 0, // neutral AE — +1 over-exposed the bright screen (colour washout)
            zoom = sliderZoom,
            onZoomChanged = { viewModel.onZoomChanged(it) },
            onLowLightBoostSupported = { viewModel.onLowLightBoostSupported(it) },
            onLowLightBoostStateChanged = { viewModel.onLowLightBoostStateChanged(it) },
            onSensorOrientation = { viewModel.onSensorOrientation(it) },
            aeLocked = aeLocked,
            modifier = Modifier.fillMaxSize()
        )

        // Decode-ROI reticle: drag to aim, ⤡ resizes, ↺ resets. The analyzer
        // crops each frame to this rect before decoding (excludes the surround).
        RoiReticle(
            onRoiChange = { l, t, r, b, va -> viewModel.setRoi(l, t, r, b, va) },
            onResetZoom = { sliderZoom = 1f },
            onResizingChange = { isResizing = it },
            modifier = Modifier.fillMaxSize()
        )

        // Floating sub-toolbar (Light / Scan image / Help) at the top.
        SubToolbar(
            onScanImage = { imagePicker.launch("image/*") },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(top = 8.dp)
        )

        // Workload coach — appears ONLY while resizing the reticle (long-press
        // the ⤡ handle), mirroring the reference QR app; never persistent.
        AnimatedVisibility(
            visible = isResizing,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 84.dp)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            WorkloadCoach(workloadPct = workloadPct)
        }

        // Transient result chip (bottom-left) + rolling success badge (bottom-right).
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ResultChip(result = scanResult, error = scanError, stats = recentStats)
            // The trust verdict runs automatically on a decode (Flow A: auto trigger) and sits beside the
            // classic decode result; tap it to open the Verification HUD.
            verificationResult?.let { verdict ->
                Column(
                    modifier = Modifier.clickable { panelExpanded = true },
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    TrustVerdictBadge(verdict = verdict.verdict)
                    PipelineStageStrip(stages = verdict.stages)
                }
            }
        }
        if (recentStats.total >= 3) {
            SuccessBadge(
                stats = recentStats,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 120.dp)
            )
        }

        // Zoom slider pinned at the bottom (− ──●── +).
        ZoomSliderBar(
            value = sliderZoom,
            onValueChange = { sliderZoom = it; sliderActive = true },
            onValueChangeFinished = { sliderActive = false },
            scanCount = scanCount,
            onTogglePanel = { panelExpanded = !panelExpanded },
            panelExpanded = panelExpanded,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        )

        // HUD-toggled diagnostic panel (slides up over the camera on demand).
        AnimatedVisibility(
            visible = panelExpanded,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            DiagnosticPanel(
                lastResult = scanResult,
                lastError = scanError,
                decodeHistory = decodeHistory,
                decodeTimeStats = decodeTimeStats,
                perNcStats = perNcStats,
                verificationResult = verificationResult,
                onStageClick = { drillDownStage = it },
                onClose = { panelExpanded = false },
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.6f)
            )
        }

        // Flow-B drill-down overlay — opened by tapping a HUD stage section. The detail comes straight
        // from the current verification result (already in scope), so no nav args are needed.
        val drillDetail = drillDownStage?.let { verificationResult?.stage(it)?.detail }
        if (drillDetail != null) {
            Box(Modifier.fillMaxSize().background(JABAuthBgBase)) {
                when (drillDetail) {
                    is CertChainDetail -> TrustAnchorScreen(drillDetail, onBack = { drillDownStage = null })
                    is JwtDetail -> CredentialScreen(drillDetail, onBack = { drillDownStage = null })
                    is AbeDetail -> AbePolicyScreen(drillDetail, onBack = { drillDownStage = null })
                    else -> {}
                }
            }
        }
    }
}
