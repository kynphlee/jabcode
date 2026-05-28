package com.jabauth.diagnostic.ui.scanner

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jabauth.jabcode.DecodeResult
import com.jabauth.ui.scanner.Camera2Preview
import com.jabauth.diagnostic.ui.permissions.CameraPermissionHandler

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
    val llbSupported by viewModel.llbSupported.collectAsState()
    val llbState by viewModel.llbState.collectAsState()
    val recentStats by viewModel.recentStats.collectAsState()
    val decodeTimeStats by viewModel.decodeTimeStats.collectAsState()
    val perNcStats by viewModel.perNcStats.collectAsState()
    val decodeHistory by viewModel.decodeHistory.collectAsState()
    val settings by viewModel.settings.collectAsState(
        initial = com.jabauth.diagnostic.data.SettingsRepository.Settings()
    )

    var detailExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("JABCode Scanner")
                        Text(
                            text = "Scans: $scanCount",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Camera preview + HUD overlay (70% normally, 30% when detail expanded)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(if (detailExpanded) 0.3f else 0.7f)
            ) {
                Camera2Preview(
                    onFrameAvailable = { reader -> viewModel.analyzeFrame(reader) },
                    autoFocus = settings.autoFocus,
                    exposureCompensation = +1,
                    onZoomChanged = { viewModel.onZoomChanged(it) },
                    onLowLightBoostSupported = { viewModel.onLowLightBoostSupported(it) },
                    onLowLightBoostStateChanged = { viewModel.onLowLightBoostStateChanged(it) },
                    modifier = Modifier.fillMaxSize()
                )

                HudOverlay(
                    zoom = currentZoom,
                    llbSupported = llbSupported,
                    llbState = llbState,
                    recentStats = recentStats,
                    lastResult = scanResult,
                    lastError = scanError,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                )
            }

            // Compact bottom panel (30% normally, 70% when expanded)
            BottomPanel(
                lastResult = scanResult,
                lastError = scanError,
                decodeHistory = decodeHistory,
                decodeTimeStats = decodeTimeStats,
                perNcStats = perNcStats,
                expanded = detailExpanded,
                onToggleExpanded = { detailExpanded = !detailExpanded },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(if (detailExpanded) 0.7f else 0.3f)
            )
        }
    }
}

// --- HUD overlay (chips + inline guidance) ---

@Composable
private fun HudOverlay(
    zoom: Float,
    llbSupported: Boolean,
    llbState: Int,
    recentStats: ScannerViewModel.ScanStats,
    lastResult: DecodeResult?,
    lastError: String?,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // Top-right chip row: Zoom + LLB
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            if (zoom > 1.01f) {
                HudChip(
                    text = "Zoom %.1f×".format(zoom),
                    bg = Color(0xFF1976D2).copy(alpha = 0.85f),
                    fg = Color.White
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            if (llbSupported) {
                val active = llbState == 1
                HudChip(
                    text = if (active) "LLB active" else "LLB inactive",
                    bg = if (active) Color(0xFFFFA000).copy(alpha = 0.9f)
                         else Color(0xFF455A64).copy(alpha = 0.7f),
                    fg = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Middle: inline guidance / status text
        val guidance: String? = when {
            lastResult != null -> "✓ ${decodeSummary(lastResult)}"
            lastError != null && recentStats.failCount >= 5 ->
                "⚠ ${failureHint(recentStats)}"
            lastResult == null && recentStats.total == 0 ->
                "Hold camera over a JABCode"
            else -> null
        }
        guidance?.let { text ->
            Surface(
                color = Color.Black.copy(alpha = 0.55f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = text,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Bottom chip row: mini-stats (module size chip is Phase-1B)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.width(1.dp))

            if (recentStats.total >= 3) {
                val (bg, fg) = when {
                    recentStats.successRate > 0.5f -> Color(0xFF388E3C).copy(alpha = 0.85f) to Color.White
                    recentStats.successRate >= 0.1f -> Color(0xFFFB8C00).copy(alpha = 0.85f) to Color.White
                    else -> Color(0xFFD32F2F).copy(alpha = 0.85f) to Color.White
                }
                HudChip(
                    text = "${recentStats.okCount}/${recentStats.total} in 30s",
                    bg = bg,
                    fg = fg
                )
            }
        }
    }
}

@Composable
private fun HudChip(text: String, bg: Color, fg: Color) {
    Surface(
        color = bg,
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = text,
            color = fg,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

private fun decodeSummary(result: DecodeResult): String {
    val s = result.asString()
    return if (s.length > 40) "${s.take(40)}…" else s
}

private fun failureHint(stats: ScannerViewModel.ScanStats): String {
    return if (stats.successRate < 0.1f)
        "Couldn't decode — try pinching to zoom in, or move closer"
    else
        "Detected but borderline — try holding steadier"
}

// --- Compact bottom panel (collapsed summary + expandable details) ---

@Composable
private fun BottomPanel(
    lastResult: DecodeResult?,
    lastError: String?,
    decodeHistory: List<DecodeResult>,
    decodeTimeStats: ScannerViewModel.DecodeTimeStats?,
    perNcStats: Map<Int, ScannerViewModel.DecodeTimeStats>,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            // Single-line summary + expand toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    if (lastResult != null) {
                        Text(
                            text = "✓ ${lastResult.colorMode} · ${decodeSummary(lastResult)} · ${lastResult.decodeTimeMs}ms",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        // Show rolling-window stats if we have at least 2 samples.
                        // A single sample isn't informative — min/max/avg/Δ all
                        // equal the current decode time.
                        decodeTimeStats?.takeIf { it.sampleCount >= 2 }?.let { s ->
                            Text(
                                text = "ms: min ${s.minMs} · max ${s.maxMs} · avg ${s.avgMs} · Δ${s.deltaMs} (n=${s.sampleCount})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    } else {
                        Text(
                            text = "Searching…",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Aim at a JABCode; pinch to zoom",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (lastResult != null) {
                    IconButton(onClick = onToggleExpanded) {
                        Icon(
                            imageVector = if (expanded)
                                Icons.Default.KeyboardArrowDown
                            else
                                Icons.Default.KeyboardArrowUp,
                            contentDescription = if (expanded) "Collapse details" else "Expand details"
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            HistoryStrip(history = decodeHistory)

            AnimatedVisibility(visible = expanded && lastResult != null) {
                lastResult?.let { result ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(top = 8.dp)
                    ) {
                        DiagnosticRow("Color Mode", result.colorMode.toString())
                        DiagnosticRow("Decode Time", "${result.decodeTimeMs}ms")
                        decodeTimeStats?.takeIf { it.sampleCount >= 2 }?.let { s ->
                            DiagnosticRow("ms (30s) min/max", "${s.minMs} / ${s.maxMs}")
                            DiagnosticRow("ms (30s) avg / Δ", "${s.avgMs} / ${s.deltaMs}  (n=${s.sampleCount})")
                        }
                        DiagnosticRow("Position", "${result.position.width()}×${result.position.height()}px")
                        DiagnosticRow("Data Size", "${result.data.size} bytes")

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "DECODE-TIME BY Nc (30s window)",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        PerNcStatsTable(perNcStats = perNcStats)

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "DECODED DATA",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Text(
                                text = result.asString(),
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "HEX DUMP",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = Color.Black.copy(alpha = 0.05f)
                            )
                        ) {
                            Text(
                                text = result.data.joinToString(" ") { "%02X".format(it) },
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            // Error card when waiting and no result yet
            if (lastResult == null && lastError != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = lastError,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryStrip(history: List<DecodeResult>) {
    if (history.isEmpty()) {
        Text(
            text = "History: (none yet)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "History: ",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(4.dp))
        LazyRow {
            items(history) { item ->
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.padding(end = 4.dp)
                ) {
                    Text(
                        text = item.colorMode.toString().removePrefix("COLOR_"),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

/**
 * Annotations for n=0 Nc rows that reference the relevant open hypothesis
 * in docs/cassandra-register/. When an Nc has zero successes in the
 * rolling window, the annotation explains why — turning silent absence
 * into actionable signal cross-referenced against the bug register.
 *
 * Keep in sync with ScannerViewModel.NC_ANNOTATIONS.
 */
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
private fun PerNcStatsTable(
    perNcStats: Map<Int, ScannerViewModel.DecodeTimeStats>
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Header
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
            Text(
                text = "Nc",
                modifier = Modifier.weight(0.6f),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "n",
                modifier = Modifier.weight(0.4f),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "min / max / avg / Δ ms",
                modifier = Modifier.weight(2.0f),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        // 8 rows — one per Nc, always present
        for (nc in 0..7) {
            val s = perNcStats[nc]
            val colorLabel = NC_COLOR_LABELS[nc] ?: ""
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$nc ($colorLabel)",
                    modifier = Modifier.weight(0.6f),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = if (s != null) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = (s?.sampleCount ?: 0).toString(),
                    modifier = Modifier.weight(0.4f),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = if (s != null) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (s != null) {
                    Text(
                        text = "${s.minMs} / ${s.maxMs} / ${s.avgMs} / ${s.deltaMs}",
                        modifier = Modifier.weight(2.0f),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                } else {
                    Text(
                        text = NC_ANNOTATIONS[nc] ?: "no successes in window",
                        modifier = Modifier.weight(2.0f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun DiagnosticRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
