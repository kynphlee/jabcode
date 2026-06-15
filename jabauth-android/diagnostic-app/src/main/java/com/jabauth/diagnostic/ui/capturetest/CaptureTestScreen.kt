package com.jabauth.diagnostic.ui.capturetest

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jabauth.diagnostic.benchmark.BenchmarkResult

/**
 * Capture Test screen - Stream validation and quality metrics
 * 
 * Displays real-time camera stream quality metrics including:
 * - Focus score (Laplacian variance)
 * - Brightness levels
 * - Contrast measurements
 * - Frame rate performance
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureTestScreen(
    modifier: Modifier = Modifier,
    viewModel: CaptureTestViewModel = viewModel()
) {
    val streamState by viewModel.streamState.collectAsState()
    val frameMetrics by viewModel.frameMetrics.collectAsState()
    val captureStats by viewModel.captureStats.collectAsState()
    val benchmarkState by viewModel.benchmarkState.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Capture Test") }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    when (streamState) {
                        is StreamState.Stopped -> viewModel.startStream()
                        is StreamState.Running -> viewModel.stopStream()
                    }
                }
            ) {
                Icon(
                    imageVector = when (streamState) {
                        is StreamState.Stopped -> Icons.Filled.PlayArrow
                        is StreamState.Running -> Icons.Filled.Stop
                    },
                    contentDescription = when (streamState) {
                        is StreamState.Stopped -> "Start Stream"
                        is StreamState.Running -> "Stop Stream"
                    }
                )
            }
        },
        modifier = modifier
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Stream Status
            StreamStatusCard(streamState = streamState)
            
            // Real-time Metrics
            if (frameMetrics != null) {
                Text(
                    text = "REAL-TIME METRICS",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                
                MetricsCard(metrics = frameMetrics!!)
            }
            
            // Aggregate Statistics
            if (captureStats.framesProcessed > 0) {
                Text(
                    text = "AGGREGATE STATISTICS",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                
                StatsCard(stats = captureStats)
            }
            
            // Instructions
            if (streamState is StreamState.Stopped && frameMetrics == null) {
                InstructionsCard()
            }

            // Codec benchmark ("Suite B") — camera-free decode sweep across
            // all 8 colour modes using bundled fixtures.
            Text(
                text = "CODEC BENCHMARK",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )

            val benchmarkRunning = benchmarkState is BenchmarkState.Running
            Button(
                onClick = { viewModel.runCodecBenchmark(context) },
                enabled = !benchmarkRunning,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (benchmarkRunning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Running benchmark…")
                } else {
                    Text("Run codec benchmark")
                }
            }

            when (val state = benchmarkState) {
                is BenchmarkState.Done -> BenchmarkResultsCard(results = state.results)
                else -> { /* Idle / Running: button state conveys progress. */ }
            }

            // Bottom clearance so the floating action button doesn't obscure
            // the last results row when scrolled to the end.
            Spacer(modifier = Modifier.height(88.dp))
        }
    }
}

/**
 * Per-mode codec benchmark summary. Looks up each Nc's decode and encode
 * [BenchmarkResult] (matched by the `decode_ncN` / `encode_ncN` benchmark
 * names emitted by `CodecBenchmark`) and lists their median ms side by side.
 * A cell that failed has no result and shows "—".
 */
@Composable
private fun BenchmarkResultsCard(
    results: List<BenchmarkResult>,
    modifier: Modifier = Modifier
) {
    if (results.isEmpty()) {
        Card(
            modifier = modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Text(
                text = "Benchmark produced no results — check logcat (tag CodecBenchmark).",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
        return
    }

    val byName = results.associateBy { it.benchmarkName }
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Mode",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Decode ms",
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.End,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Encode ms",
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.End,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )
            }
            for (nc in 0..7) {
                val colorCount = 1 shl (nc + 1)
                BenchmarkModeRow(
                    label = "Nc $nc (${colorCount}c)",
                    decodeMedianMs = byName["decode_nc$nc"]?.medianTimeMs,
                    encodeMedianMs = byName["encode_nc$nc"]?.medianTimeMs
                )
            }
        }
    }
}

@Composable
private fun BenchmarkModeRow(
    label: String,
    decodeMedianMs: Double?,
    encodeMedianMs: Double?,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = formatMedian(decodeMedianMs),
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = formatMedian(encodeMedianMs),
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun formatMedian(ms: Double?): String =
    if (ms == null) "—" else String.format("%.1f", ms)

@Composable
private fun StreamStatusCard(
    streamState: StreamState,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (streamState) {
                is StreamState.Running -> MaterialTheme.colorScheme.primaryContainer
                is StreamState.Stopped -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Stream Status",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = when (streamState) {
                        is StreamState.Running -> "Active - Processing frames"
                        is StreamState.Stopped -> "Stopped - Press play to start"
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            
            Surface(
                shape = MaterialTheme.shapes.small,
                color = when (streamState) {
                    is StreamState.Running -> MaterialTheme.colorScheme.primary
                    is StreamState.Stopped -> MaterialTheme.colorScheme.outline
                }
            ) {
                Text(
                    text = when (streamState) {
                        is StreamState.Running -> "ACTIVE"
                        is StreamState.Stopped -> "STOPPED"
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = when (streamState) {
                        is StreamState.Running -> MaterialTheme.colorScheme.onPrimary
                        is StreamState.Stopped -> MaterialTheme.colorScheme.onSurface
                    }
                )
            }
        }
    }
}

@Composable
private fun MetricsCard(
    metrics: FrameMetrics,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetricRow(
                label = "Focus Score",
                value = String.format("%.1f", metrics.focusScore),
                quality = getFocusQuality(metrics.focusScore)
            )
            MetricRow(
                label = "Brightness",
                value = String.format("%.1f%%", metrics.brightness * 100),
                quality = getBrightnessQuality(metrics.brightness)
            )
            MetricRow(
                label = "Contrast",
                value = String.format("%.2f", metrics.contrast),
                quality = getContrastQuality(metrics.contrast)
            )
            MetricRow(
                label = "Frame Rate",
                value = String.format("%.1f fps", metrics.frameRate),
                quality = getFrameRateQuality(metrics.frameRate)
            )
        }
    }
}

@Composable
private fun StatsCard(
    stats: CaptureStats,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatRow(
                label = "Frames Processed",
                value = stats.framesProcessed.toString()
            )
            StatRow(
                label = "Avg Focus Score",
                value = String.format("%.1f", stats.avgFocusScore)
            )
            StatRow(
                label = "Avg Brightness",
                value = String.format("%.1f%%", stats.avgBrightness * 100)
            )
            StatRow(
                label = "Avg Frame Rate",
                value = String.format("%.1f fps", stats.avgFrameRate)
            )
        }
    }
}

@Composable
private fun MetricRow(
    label: String,
    value: String,
    quality: MetricQuality,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium
        )
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            QualityIndicator(quality = quality)
        }
    }
}

@Composable
private fun StatRow(
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
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun QualityIndicator(
    quality: MetricQuality,
    modifier: Modifier = Modifier
) {
    val (color, text) = when (quality) {
        MetricQuality.GOOD -> MaterialTheme.colorScheme.primary to "●"
        MetricQuality.FAIR -> MaterialTheme.colorScheme.tertiary to "●"
        MetricQuality.POOR -> MaterialTheme.colorScheme.error to "●"
    }
    
    Text(
        text = text,
        modifier = modifier,
        color = color,
        style = MaterialTheme.typography.titleLarge
    )
}

@Composable
private fun InstructionsCard(
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Instructions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Press the play button to start stream validation. Metrics will update in real-time.",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "• Focus Score: Laplacian variance (>100 is good)\n" +
                      "• Brightness: Target 40-60% for optimal scanning\n" +
                      "• Contrast: Higher values indicate better image quality\n" +
                      "• Frame Rate: Target 30 fps for smooth operation",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

// Helper functions for quality assessment

private fun getFocusQuality(score: Double): MetricQuality = when {
    score >= 100.0 -> MetricQuality.GOOD
    score >= 50.0 -> MetricQuality.FAIR
    else -> MetricQuality.POOR
}

private fun getBrightnessQuality(brightness: Double): MetricQuality = when {
    brightness in 0.4..0.6 -> MetricQuality.GOOD
    brightness in 0.3..0.7 -> MetricQuality.FAIR
    else -> MetricQuality.POOR
}

private fun getContrastQuality(contrast: Double): MetricQuality = when {
    contrast >= 0.5 -> MetricQuality.GOOD
    contrast >= 0.3 -> MetricQuality.FAIR
    else -> MetricQuality.POOR
}

private fun getFrameRateQuality(fps: Double): MetricQuality = when {
    fps >= 25.0 -> MetricQuality.GOOD
    fps >= 15.0 -> MetricQuality.FAIR
    else -> MetricQuality.POOR
}

enum class MetricQuality {
    GOOD,
    FAIR,
    POOR
}
