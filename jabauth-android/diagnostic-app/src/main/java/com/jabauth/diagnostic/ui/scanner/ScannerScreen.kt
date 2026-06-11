package com.jabauth.diagnostic.ui.scanner

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.jabauth.jabcode.DecodeResult
import com.jabauth.ui.scanner.Camera2Preview
import com.jabauth.diagnostic.ui.permissions.CameraPermissionHandler
import kotlin.math.abs
import kotlin.math.roundToInt

// --- QR-Scanner-style palette (adopted from reference videos) ---
private val Orange = Color(0xFFE8A23D)
private val ScanBg = Color(0xFF0E0E10)
private val PanelBg = Color(0xFF1C1C1E)
private val TextPrimary = Color(0xFFFFFFFF)
private val TextSecondary = Color(0xFF9A9A9E)
private val WorkloadGood = Color(0xFF5CC46C)
private val WorkloadHard = Color(0xFFE5534B)
private const val MAX_ZOOM = 8f

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
    val scanError by viewModel.scanError.collectAsState()
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
    LaunchedEffect(currentZoom) {
        if (abs(currentZoom - sliderZoom) > 0.05f) sliderZoom = currentZoom.coerceIn(1f, MAX_ZOOM)
    }
    var panelExpanded by remember { mutableStateOf(false) }

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
            modifier = Modifier.fillMaxSize()
        )

        // Decode-ROI reticle: drag to aim, ⤡ resizes, ↺ resets. The analyzer
        // crops each frame to this rect before decoding (excludes the surround).
        RoiReticle(
            onRoiChange = { l, t, r, b, va -> viewModel.setRoi(l, t, r, b, va) },
            onResetZoom = { sliderZoom = 1f },
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

        // Workload coach + zoom badge just under the toolbar.
        WorkloadCoach(
            workloadPct = workloadPct,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 84.dp)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )

        // Transient result chip (bottom-left) + rolling success badge (bottom-right).
        ResultChip(
            result = scanResult,
            error = scanError,
            stats = recentStats,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, bottom = 120.dp)
        )
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
            onValueChange = { sliderZoom = it },
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
                onClose = { panelExpanded = false },
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.6f)
            )
        }
    }
}

// --- Decode-ROI reticle: draggable + resizable; reports a normalized rect ---
//
// The rect (in container px) is the single source of truth. Dragging the body
// MOVES it; dragging the ⤡ corner RESIZES it; ↺ resets it (and zoom). On every
// change it publishes the rect normalized to 0..1 of the view, plus the view's
// aspect ratio, which the analyzer uses to crop the frame 1:1 to the reticle.

@Composable
private fun RoiReticle(
    onRoiChange: (Float, Float, Float, Float, Float) -> Unit,
    onResetZoom: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val minPx = with(density) { 110.dp.toPx() }
    var box by remember { mutableStateOf(IntSize.Zero) }
    var roi by remember { mutableStateOf<Rect?>(null) }

    fun defaultRoi(size: IntSize): Rect {
        val w = size.width * 0.82f          // 82% of width, square (px)
        val left = (size.width - w) / 2f
        val top = (size.height - w) / 2f
        return Rect(left, top, left + w, top + w)
    }

    // Initialize the ROI once the container is measured.
    LaunchedEffect(box) {
        if (box != IntSize.Zero && roi == null) roi = defaultRoi(box)
    }
    // Publish the normalized ROI (+ view aspect) whenever it changes.
    LaunchedEffect(roi, box) {
        val r = roi ?: return@LaunchedEffect
        if (box.width == 0 || box.height == 0) return@LaunchedEffect
        onRoiChange(
            r.left / box.width, r.top / box.height,
            r.right / box.width, r.bottom / box.height,
            box.width.toFloat() / box.height
        )
    }

    Box(modifier = modifier.onSizeChanged { box = it }) {
        val r = roi
        if (r != null) {
            val handle = 46.dp
            val handlePx = with(density) { handle.toPx() }

            // Reticle body — drag anywhere inside to MOVE.
            Box(
                modifier = Modifier
                    .offset { IntOffset(r.left.roundToInt(), r.top.roundToInt()) }
                    .size(with(density) { r.width.toDp() }, with(density) { r.height.toDp() })
                    .pointerInput(box) {
                        detectDragGestures { change, drag ->
                            change.consume()
                            roi?.let { cur ->
                                val w = cur.width
                                val h = cur.height
                                val nl = (cur.left + drag.x).coerceIn(0f, (box.width - w).coerceAtLeast(0f))
                                val nt = (cur.top + drag.y).coerceIn(0f, (box.height - h).coerceAtLeast(0f))
                                roi = Rect(nl, nt, nl + w, nt + h)
                            }
                        }
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val len = size.minDimension * 0.16f
                    val sw = 6f
                    val c = Orange
                    drawLine(c, Offset(0f, 0f), Offset(len, 0f), strokeWidth = sw, cap = StrokeCap.Round)
                    drawLine(c, Offset(0f, 0f), Offset(0f, len), strokeWidth = sw, cap = StrokeCap.Round)
                    drawLine(c, Offset(size.width, 0f), Offset(size.width - len, 0f), strokeWidth = sw, cap = StrokeCap.Round)
                    drawLine(c, Offset(size.width, 0f), Offset(size.width, len), strokeWidth = sw, cap = StrokeCap.Round)
                    drawLine(c, Offset(0f, size.height), Offset(len, size.height), strokeWidth = sw, cap = StrokeCap.Round)
                    drawLine(c, Offset(0f, size.height), Offset(0f, size.height - len), strokeWidth = sw, cap = StrokeCap.Round)
                    drawLine(c, Offset(size.width, size.height), Offset(size.width - len, size.height), strokeWidth = sw, cap = StrokeCap.Round)
                    drawLine(c, Offset(size.width, size.height), Offset(size.width, size.height - len), strokeWidth = sw, cap = StrokeCap.Round)
                }
                // Reset (↺) bottom-left — resets the ROI to default + zoom to 1×.
                IconButton(
                    onClick = {
                        onResetZoom()
                        if (box != IntSize.Zero) roi = defaultRoi(box)
                    },
                    modifier = Modifier.align(Alignment.BottomStart)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Reset", tint = Orange)
                }
            }

            // Resize handle (⤡) at the bottom-right corner — drag to RESIZE.
            // Declared after the body ⇒ on top, so its drags don't race MOVE.
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (r.right - handlePx / 2f).roundToInt(),
                            (r.bottom - handlePx / 2f).roundToInt()
                        )
                    }
                    .size(handle)
                    .pointerInput(box) {
                        detectDragGestures { change, drag ->
                            change.consume()
                            roi?.let { cur ->
                                val nr = (cur.right + drag.x).coerceIn(cur.left + minPx, box.width.toFloat())
                                val nb = (cur.bottom + drag.y).coerceIn(cur.top + minPx, box.height.toFloat())
                                roi = Rect(cur.left, cur.top, nr, nb)
                            }
                        }
                    }
            ) {
                Text("⤡", color = Orange, fontSize = 24.sp, modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}

// --- Floating sub-toolbar: Light / Scan image / Help ---

@Composable
private fun SubToolbar(onScanImage: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        ToolbarItem("Light", onClick = {})
        ToolbarItem("Scan image", onClick = onScanImage)
        ToolbarItem("Help", onClick = {})
    }
}

@Composable
private fun ToolbarItem(label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(label, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

// --- Workload coach: decode-region load (% of frame) + guidance ---

@Composable
private fun WorkloadCoach(
    workloadPct: Int,
    modifier: Modifier = Modifier
) {
    // workloadPct is the ROI's area as a % of the frame — exactly what the
    // decoder must chew through. Mirrors the reference app: bigger box, higher %.
    val pctColor = when {
        workloadPct <= 33 -> WorkloadGood
        workloadPct <= 66 -> Orange
        else -> WorkloadHard
    }
    val hint = when {
        workloadPct <= 20 -> "Box is small — keep the WHOLE code inside it, with margin."
        workloadPct <= 50 -> "Fit the whole code in the box, leaving a little margin."
        else -> "Large area — shrink toward the code, but leave a margin."
    }
    Column(modifier = modifier) {
        Row {
            Text("Scanner workload: ", color = TextPrimary, fontSize = 15.sp)
            Text("$workloadPct%", color = pctColor, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        Text(hint, color = TextSecondary, fontSize = 13.sp)
        Text("Drag the box to aim · ⤡ resize · ↺ reset.", color = TextSecondary, fontSize = 13.sp)
    }
}

// --- Transient result chip + rolling success badge ---

@Composable
private fun ResultChip(
    result: DecodeResult?,
    error: String?,
    stats: ScannerViewModel.ScanStats,
    modifier: Modifier = Modifier
) {
    val text: String? = when {
        result != null -> "✓ ${decodeSummary(result)}"
        error != null && stats.failCount >= 5 -> "⚠ ${failureHint(stats)}"
        result == null && stats.total == 0 -> "Hold camera over a JABCode"
        else -> null
    }
    text?.let {
        Surface(color = Color.Black.copy(alpha = 0.6f), shape = RoundedCornerShape(8.dp), modifier = modifier) {
            Text(it, color = TextPrimary, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
        }
    }
}

@Composable
private fun SuccessBadge(stats: ScannerViewModel.ScanStats, modifier: Modifier = Modifier) {
    val bg = when {
        stats.successRate > 0.5f -> WorkloadGood
        stats.successRate >= 0.1f -> Orange
        else -> WorkloadHard
    }
    Surface(color = bg, shape = RoundedCornerShape(10.dp), modifier = modifier) {
        Text(
            "${stats.okCount}/${stats.total} in 30s",
            color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
        )
    }
}

// --- Zoom slider bar + scans counter + diagnostics toggle ---

@Composable
private fun ZoomSliderBar(
    value: Float,
    onValueChange: (Float) -> Unit,
    scanCount: Int,
    onTogglePanel: () -> Unit,
    panelExpanded: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.35f))
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Scans: $scanCount", color = TextSecondary, fontSize = 12.sp)
            Spacer(Modifier.weight(1f))
            Text(
                "%.1f×".format(value),
                color = Orange, fontSize = 12.sp, fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.width(12.dp))
            Surface(
                color = if (panelExpanded) Orange else Color.White.copy(alpha = 0.15f),
                shape = CircleShape,
                modifier = Modifier.clickable { onTogglePanel() }
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = "Toggle diagnostics",
                    tint = if (panelExpanded) Color.Black else TextPrimary,
                    modifier = Modifier.padding(6.dp).size(18.dp)
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("−", color = TextPrimary, fontSize = 22.sp)
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = 1f..MAX_ZOOM,
                colors = SliderDefaults.colors(
                    thumbColor = Orange,
                    activeTrackColor = Orange,
                    inactiveTrackColor = TextSecondary.copy(alpha = 0.4f)
                ),
                modifier = Modifier.weight(1f).padding(horizontal = 10.dp)
            )
            Text("+", color = TextPrimary, fontSize = 22.sp)
        }
    }
}

// --- HUD-toggled diagnostic panel (preserves all prior diagnostic data) ---

@Composable
private fun DiagnosticPanel(
    lastResult: DecodeResult?,
    lastError: String?,
    decodeHistory: List<DecodeResult>,
    decodeTimeStats: ScannerViewModel.DecodeTimeStats?,
    perNcStats: Map<Int, ScannerViewModel.DecodeTimeStats>,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier, color = PanelBg, shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Diagnostics", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                }
            }
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                if (lastResult != null) {
                    Text(
                        "✓ ${lastResult.colorMode} · ${decodeSummary(lastResult)} · ${lastResult.decodeTimeMs}ms",
                        color = TextPrimary, fontSize = 14.sp
                    )
                    decodeTimeStats?.takeIf { it.sampleCount >= 2 }?.let { s ->
                        Text(
                            "ms: min ${s.minMs} · max ${s.maxMs} · avg ${s.avgMs} · Δ${s.deltaMs} (n=${s.sampleCount})",
                            color = TextSecondary, fontSize = 12.sp, fontFamily = FontFamily.Monospace
                        )
                    }
                } else {
                    Text("Searching… aim at a JABCode", color = TextPrimary, fontSize = 14.sp)
                }
                Spacer(Modifier.height(6.dp))
                HistoryStrip(history = decodeHistory)

                lastResult?.let { result ->
                    Spacer(Modifier.height(8.dp))
                    DiagnosticRow("Color Mode", result.colorMode.toString())
                    DiagnosticRow("Decode Time", "${result.decodeTimeMs}ms")
                    DiagnosticRow("Position", "${result.position.width()}×${result.position.height()}px")
                    DiagnosticRow("Data Size", "${result.data.size} bytes")
                    Spacer(Modifier.height(8.dp))
                    Text("DECODE-TIME BY Nc (30s window)", color = Orange, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    PerNcStatsTable(perNcStats = perNcStats)
                    Spacer(Modifier.height(8.dp))
                    Text("DECODED DATA", color = Orange, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(result.asString(), color = TextPrimary, fontSize = 13.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 4.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("HEX DUMP", color = Orange, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(result.data.joinToString(" ") { "%02X".format(it) }, color = TextSecondary, fontSize = 11.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 4.dp))
                }
                if (lastResult == null && lastError != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(lastError, color = WorkloadHard, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

@Composable
private fun HistoryStrip(history: List<DecodeResult>) {
    if (history.isEmpty()) {
        Text("History: (none yet)", color = TextSecondary, fontSize = 12.sp)
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("History: ", color = TextSecondary, fontSize = 12.sp)
        Spacer(Modifier.width(4.dp))
        LazyRow {
            items(history) { item ->
                Surface(color = Color.White.copy(alpha = 0.1f), shape = RoundedCornerShape(8.dp), modifier = Modifier.padding(end = 4.dp)) {
                    Text(
                        item.colorMode.toString().removePrefix("COLOR_"),
                        color = TextPrimary, fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

private val NC_ANNOTATIONS = mapOf(
    0 to "Mode 0 — H_mode0_partI_decode_failure",
    2 to "8-color — H_nc2_decode_failure",
    6 to "128-color — print gamut-limited",
    7 to "256-color — slave-decode + gamut"
)

private val NC_COLOR_LABELS = mapOf(
    0 to "2c", 1 to "4c", 2 to "8c", 3 to "16c",
    4 to "32c", 5 to "64c", 6 to "128c", 7 to "256c"
)

@Composable
private fun PerNcStatsTable(perNcStats: Map<Int, ScannerViewModel.DecodeTimeStats>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
            Text("Nc", modifier = Modifier.weight(0.6f), color = TextSecondary, fontSize = 11.sp)
            Text("n", modifier = Modifier.weight(0.4f), color = TextSecondary, fontSize = 11.sp)
            Text("min / max / avg / Δ ms", modifier = Modifier.weight(2.0f), color = TextSecondary, fontSize = 11.sp)
        }
        for (nc in 0..7) {
            val s = perNcStats[nc]
            val colorLabel = NC_COLOR_LABELS[nc] ?: ""
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("$nc ($colorLabel)", modifier = Modifier.weight(0.6f), color = if (s != null) TextPrimary else TextSecondary, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                Text((s?.sampleCount ?: 0).toString(), modifier = Modifier.weight(0.4f), color = if (s != null) TextPrimary else TextSecondary, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                if (s != null) {
                    Text("${s.minMs} / ${s.maxMs} / ${s.avgMs} / ${s.deltaMs}", modifier = Modifier.weight(2.0f), color = TextPrimary, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                } else {
                    Text(NC_ANNOTATIONS[nc] ?: "no successes in window", modifier = Modifier.weight(2.0f), color = TextSecondary, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun DiagnosticRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = TextSecondary, fontSize = 12.sp)
        Text(value, color = TextPrimary, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
    }
}

private fun decodeSummary(result: DecodeResult): String {
    val s = result.asString()
    return if (s.length > 40) "${s.take(40)}…" else s
}

private fun failureHint(stats: ScannerViewModel.ScanStats): String {
    return if (stats.successRate < 0.1f)
        "Couldn't decode — zoom in or move closer"
    else
        "Detected but borderline — hold steadier"
}
