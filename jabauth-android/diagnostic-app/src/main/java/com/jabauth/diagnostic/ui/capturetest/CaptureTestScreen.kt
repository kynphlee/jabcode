package com.jabauth.diagnostic.ui.capturetest

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jabauth.diagnostic.benchmark.BenchmarkResult
import com.jabauth.diagnostic.benchmark.CarrierComparisonBenchmark
import com.jabauth.diagnostic.benchmark.CarrierComparisonReport
import com.jabauth.diagnostic.benchmark.CarrierComparisonRow
import com.jabauth.diagnostic.benchmark.VerifyLatencyReport
import com.jabauth.diagnostic.benchmark.VerifyLatencyRow
import com.jabauth.ui.components.Badge
import com.jabauth.ui.components.JABAuthCard
import com.jabauth.ui.components.StatusIndicator
import com.jabauth.ui.theme.JABAuthBgBase
import com.jabauth.ui.theme.JABAuthError
import com.jabauth.ui.theme.JABAuthOnPrimary
import com.jabauth.ui.theme.JABAuthPrimary
import com.jabauth.ui.theme.JABAuthSuccess
import com.jabauth.ui.theme.JABAuthTextDim
import com.jabauth.ui.theme.JABAuthTextPrimary
import com.jabauth.ui.theme.JABAuthTextSecondary
import com.jabauth.ui.theme.JABAuthWarning
import com.jabauth.ui.theme.Spacing

/**
 * Capture Test screen - Stream validation and quality metrics
 *
 * Displays real-time camera stream quality metrics including:
 * - Focus score (Laplacian variance)
 * - Brightness levels
 * - Contrast measurements
 * - Frame rate performance
 *
 * Styled with the JABAuth design system (DESIGN_SYSTEM.md v1.0.0): stepped navy
 * surfaces, neon accents, monospace data type, and the staggered card-entrance
 * motion (100ms/index delay, 600ms decelerate fade + 24dp upward slide).
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
    val verifyLatencyState by viewModel.verifyLatencyState.collectAsState()
    val carrierState by viewModel.carrierState.collectAsState()
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
                },
                containerColor = JABAuthPrimary,
                contentColor = JABAuthOnPrimary
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
                .background(JABAuthBgBase)
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            // Each top-level section is wrapped in a StaggeredReveal so it fades +
            // slides up in sequence on first composition. A running index keeps the
            // 100ms/index cadence across the conditionally-present sections.
            var revealIndex = 0

            // Stream Status
            StaggeredReveal(index = revealIndex++) {
                StreamStatusCard(streamState = streamState)
            }

            // Real-time Metrics
            if (frameMetrics != null) {
                StaggeredReveal(index = revealIndex++) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        SectionHeader("REAL-TIME METRICS")
                        MetricsCard(metrics = frameMetrics!!)
                    }
                }
            }

            // Aggregate Statistics
            if (captureStats.framesProcessed > 0) {
                StaggeredReveal(index = revealIndex++) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        SectionHeader("AGGREGATE STATISTICS")
                        StatsCard(stats = captureStats)
                    }
                }
            }

            // Instructions
            if (streamState is StreamState.Stopped && frameMetrics == null) {
                StaggeredReveal(index = revealIndex++) {
                    InstructionsCard()
                }
            }

            // Verify latency by profile — the on-device pre-check pipeline's per-stage
            // latency (dec/pki/jwt/abe + Σ), grouped by COA capture profile. Sits above
            // the codec benchmark because it *consumes* the codec decode numbers.
            StaggeredReveal(index = revealIndex++) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    SectionHeader("VERIFY LATENCY BY PROFILE · MS")

                    val verifyRunning = verifyLatencyState is VerifyLatencyState.Running
                    Button(
                        onClick = { viewModel.runVerifyLatencyBenchmark(context) },
                        enabled = !verifyRunning,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = JABAuthPrimary,
                            contentColor = JABAuthOnPrimary
                        )
                    ) {
                        if (verifyRunning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = JABAuthOnPrimary
                            )
                            Spacer(modifier = Modifier.width(Spacing.xs))
                            Text("Measuring…")
                        } else {
                            Text("Run verify-latency benchmark")
                        }
                    }

                    when (val state = verifyLatencyState) {
                        is VerifyLatencyState.Done -> VerifyLatencyCard(report = state.report)
                        else -> { /* Idle / Running: button state conveys progress. */ }
                    }
                }
            }

            // Carrier A/B — the string(base64url text) vs binary(raw v2 blob) comparison:
            // one canonical COA, both wrappings, all four profiles, in-memory encode→decode.
            // The mobile half of E2eCarrierBenchmark (server JMH).
            StaggeredReveal(index = revealIndex++) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    SectionHeader("CARRIER A/B · STRING VS BINARY")

                    val carrierRunning = carrierState is CarrierComparisonState.Running
                    Button(
                        onClick = { viewModel.runCarrierComparisonBenchmark() },
                        enabled = !carrierRunning,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = JABAuthPrimary,
                            contentColor = JABAuthOnPrimary
                        )
                    ) {
                        if (carrierRunning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = JABAuthOnPrimary
                            )
                            Spacer(modifier = Modifier.width(Spacing.xs))
                            Text("Comparing…")
                        } else {
                            Text("Run carrier A/B benchmark")
                        }
                    }

                    when (val state = carrierState) {
                        is CarrierComparisonState.Done -> CarrierComparisonCard(report = state.report)
                        else -> { /* Idle / Running: button state conveys progress. */ }
                    }
                }
            }

            // Codec benchmark ("Suite B") — camera-free decode sweep across
            // all 8 colour modes using bundled fixtures. Last section: read the
            // index without incrementing (add `++` here if a section follows).
            StaggeredReveal(index = revealIndex) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    SectionHeader("CODEC BENCHMARK")

                    val benchmarkRunning = benchmarkState is BenchmarkState.Running
                    Button(
                        onClick = { viewModel.runCodecBenchmark(context) },
                        enabled = !benchmarkRunning,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = JABAuthPrimary,
                            contentColor = JABAuthOnPrimary
                        )
                    ) {
                        if (benchmarkRunning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = JABAuthOnPrimary
                            )
                            Spacer(modifier = Modifier.width(Spacing.xs))
                            Text("Running benchmark…")
                        } else {
                            Text("Run codec benchmark")
                        }
                    }

                    when (val state = benchmarkState) {
                        is BenchmarkState.Done -> BenchmarkResultsCard(results = state.results)
                        else -> { /* Idle / Running: button state conveys progress. */ }
                    }
                }
            }

            // Bottom clearance so the floating action button doesn't obscure
            // the last results row when scrolled to the end.
            Spacer(modifier = Modifier.height(88.dp))
        }
    }
}

/**
 * Staggered card-entrance wrapper (DESIGN_SYSTEM.md v1.0.0, Motion System).
 *
 * Fades the [content] in while sliding it up 24dp, over 600ms on the Decelerate
 * curve, after a per-[index] delay of 100ms — so a column of sections reveals in
 * sequence on first composition.
 */
@Composable
private fun StaggeredReveal(
    index: Int,
    content: @Composable () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    val slideOffsetPx = with(LocalDensity.current) { 24.dp.roundToPx() }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay((index * STAGGER_DELAY_MS).toLong())
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(
            animationSpec = tween(durationMillis = ENTRANCE_DURATION_MS, easing = DecelerateEasing)
        ) + slideInVertically(
            initialOffsetY = { slideOffsetPx },
            animationSpec = tween(durationMillis = ENTRANCE_DURATION_MS, easing = DecelerateEasing)
        )
    ) {
        content()
    }
}

/** 100ms per index — staggered-entrance cadence. */
private const val STAGGER_DELAY_MS = 100
/** 600ms — entrance ("Slow") animation duration. */
private const val ENTRANCE_DURATION_MS = 600
/** Decelerate easing (0.0, 0.0, 0.2, 1.0) — enter-screen curve. */
private val DecelerateEasing = CubicBezierEasing(0.0f, 0.0f, 0.2f, 1.0f)

/** Section label — tracked, dim-secondary monospace per the design system. */
@Composable
private fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.labelMedium,
        color = JABAuthTextSecondary
    )
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
        JABAuthCard(modifier = modifier.fillMaxWidth()) {
            Text(
                text = "Benchmark produced no results — check logcat (tag CodecBenchmark).",
                style = MaterialTheme.typography.bodyMedium,
                color = JABAuthError
            )
        }
        return
    }

    val byName = results.associateBy { it.benchmarkName }
    JABAuthCard(modifier = modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Mode",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    color = JABAuthTextSecondary
                )
                Text(
                    text = "Decode ms",
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.End,
                    style = MaterialTheme.typography.labelMedium,
                    color = JABAuthTextSecondary
                )
                Text(
                    text = "Encode ms",
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.End,
                    style = MaterialTheme.typography.labelMedium,
                    color = JABAuthTextSecondary
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
            style = MaterialTheme.typography.bodyMedium,
            color = JABAuthTextSecondary
        )
        Text(
            text = formatMedian(decodeMedianMs),
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End,
            style = MaterialTheme.typography.bodyLarge,
            color = JABAuthTextPrimary
        )
        Text(
            text = formatMedian(encodeMedianMs),
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End,
            style = MaterialTheme.typography.bodyLarge,
            color = JABAuthTextPrimary
        )
    }
}

private fun formatMedian(ms: Double?): String =
    if (ms == null) "—" else String.format("%.1f", ms)

/**
 * The "VERIFY LATENCY BY PROFILE · MS" table: one row per COA capture profile with the on-device pre-check
 * pipeline's per-stage medians — `dec` (decode) / `pki` / `jwt` / `abe` — and their sum `Σ`.
 *
 * **Honesty:** the `pki`/`jwt`/`abe` columns are a single real measurement over a canonical COA, repeated
 * across profiles because the crypto is profile-independent (only decode density varies by profile). The
 * `dec` column is the codec benchmark's per-Nc decode median for the profile's colour mode; a profile whose
 * decode has not been measured shows "—" and a "—" Σ (never a fabricated number). The FIELD_HOSTILE note
 * mirrors the design: it trades decode speed (4-colour, lowest density) for lighting/chroma robustness.
 */
@Composable
private fun VerifyLatencyCard(
    report: VerifyLatencyReport,
    modifier: Modifier = Modifier
) {
    JABAuthCard(modifier = modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                VerifyHeaderCell("profile", weight = 2.2f, alignEnd = false)
                VerifyHeaderCell("dec")
                VerifyHeaderCell("pki")
                VerifyHeaderCell("jwt")
                VerifyHeaderCell("abe")
                VerifyHeaderCell("Σ")
            }
            for (row in report.rows) {
                VerifyLatencyDataRow(row)
            }
            Text(
                text = "FIELD_HOSTILE trades decode speed for robustness",
                style = MaterialTheme.typography.bodySmall,
                color = JABAuthTextDim
            )
            Text(
                text = "pki · jwt · abe measured once (profile-independent crypto); " +
                    "dec from the codec benchmark per profile colour mode.",
                style = MaterialTheme.typography.bodySmall,
                color = JABAuthTextDim
            )
        }
    }
}

@Composable
private fun RowScope.VerifyHeaderCell(
    text: String,
    weight: Float = 1f,
    alignEnd: Boolean = true
) {
    Text(
        text = text,
        modifier = Modifier.weight(weight),
        textAlign = if (alignEnd) TextAlign.End else TextAlign.Start,
        style = MaterialTheme.typography.labelMedium,
        color = JABAuthTextSecondary
    )
}

@Composable
private fun VerifyLatencyDataRow(
    row: VerifyLatencyRow,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = row.profile.name,
            modifier = Modifier.weight(2.2f),
            style = MaterialTheme.typography.bodyMedium,
            color = JABAuthTextSecondary
        )
        VerifyLatencyValueCell(row.decodeMs)
        VerifyLatencyValueCell(row.pkiMs)
        VerifyLatencyValueCell(row.jwtMs)
        VerifyLatencyValueCell(row.abeMs)
        VerifyLatencyValueCell(row.totalMs)
    }
}

@Composable
private fun RowScope.VerifyLatencyValueCell(ms: Double?) {
    Text(
        text = formatLatencyCell(ms),
        modifier = Modifier.weight(1f),
        textAlign = TextAlign.End,
        style = MaterialTheme.typography.bodyLarge,
        color = JABAuthTextPrimary
    )
}

/** Latency cells render as whole ms (the pipeline budget is discussed in whole-ms terms); "—" when absent. */
private fun formatLatencyCell(ms: Double?): String =
    if (ms == null) "—" else "%.0f".format(ms)

/**
 * The "CARRIER A/B · STRING VS BINARY" table: one row per (profile, arm) with the carrier
 * payload size, the rendered symbol dimensions, and the encode/decode medians. Failed cells
 * (e.g. capacity exceeded at 4-colour) render "—" — never a fabricated number.
 */
@Composable
private fun CarrierComparisonCard(
    report: CarrierComparisonReport,
    modifier: Modifier = Modifier
) {
    JABAuthCard(modifier = modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                VerifyHeaderCell("profile", weight = 2.0f, alignEnd = false)
                VerifyHeaderCell("arm", weight = 1.2f, alignEnd = false)
                VerifyHeaderCell("bytes")
                VerifyHeaderCell("px")
                VerifyHeaderCell("enc")
                VerifyHeaderCell("dec")
            }
            for (row in report.rows) {
                CarrierComparisonDataRow(row)
            }
            Text(
                text = "One v2 blob (${report.blobBytes} B); string = base64url of the same blob. " +
                    "Same JNI decode path — only the wrapping (and thus symbol density) differs.",
                style = MaterialTheme.typography.bodySmall,
                color = JABAuthTextDim
            )
        }
    }
}

@Composable
private fun CarrierComparisonDataRow(
    row: CarrierComparisonRow,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = row.profile.name,
            modifier = Modifier.weight(2.0f),
            style = MaterialTheme.typography.bodyMedium,
            color = JABAuthTextSecondary
        )
        Text(
            text = if (row.arm == CarrierComparisonBenchmark.CarrierArm.BINARY) "bin" else "str",
            modifier = Modifier.weight(1.2f),
            style = MaterialTheme.typography.bodyMedium,
            color = JABAuthTextSecondary
        )
        VerifyLatencyValueCell(row.payloadBytes.toDouble())
        VerifyLatencyValueCell(row.bitmapWidth?.toDouble())
        VerifyLatencyValueCell(row.encodeMedianMs)
        VerifyLatencyValueCell(row.decodeMedianMs)
    }
}

@Composable
private fun StreamStatusCard(
    streamState: StreamState,
    modifier: Modifier = Modifier
) {
    val running = streamState is StreamState.Running
    val accent = if (running) JABAuthSuccess else JABAuthTextDim
    JABAuthCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusIndicator(color = accent, pulsing = running)
                Column {
                    Text(
                        text = "Stream Status",
                        style = MaterialTheme.typography.headlineSmall,
                        color = JABAuthTextPrimary
                    )
                    Text(
                        text = if (running) "Active - Processing frames"
                        else "Stopped - Press play to start",
                        style = MaterialTheme.typography.bodyMedium,
                        color = JABAuthTextSecondary
                    )
                }
            }

            Badge(
                text = if (running) "Active" else "Stopped",
                color = accent
            )
        }
    }
}

@Composable
private fun MetricsCard(
    metrics: FrameMetrics,
    modifier: Modifier = Modifier
) {
    JABAuthCard(modifier = modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
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
    JABAuthCard(modifier = modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
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
            style = MaterialTheme.typography.bodyMedium,
            color = JABAuthTextSecondary
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                color = JABAuthTextPrimary
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
            color = JABAuthTextSecondary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = JABAuthTextPrimary
        )
    }
}

@Composable
private fun QualityIndicator(
    quality: MetricQuality,
    modifier: Modifier = Modifier
) {
    val color = when (quality) {
        MetricQuality.GOOD -> JABAuthSuccess
        MetricQuality.FAIR -> JABAuthWarning
        MetricQuality.POOR -> JABAuthError
    }
    StatusIndicator(
        modifier = modifier,
        color = color,
        pulsing = false
    )
}

@Composable
private fun InstructionsCard(
    modifier: Modifier = Modifier
) {
    JABAuthCard(modifier = modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                text = "Instructions",
                style = MaterialTheme.typography.headlineSmall,
                color = JABAuthTextPrimary
            )
            Text(
                text = "Press the play button to start stream validation. Metrics will update in real-time.",
                style = MaterialTheme.typography.bodyMedium,
                color = JABAuthTextSecondary
            )
            Text(
                text = "• Focus Score: Laplacian variance (>100 is good)\n" +
                    "• Brightness: Target 40-60% for optimal scanning\n" +
                    "• Contrast: Higher values indicate better image quality\n" +
                    "• Frame Rate: Target 30 fps for smooth operation",
                style = MaterialTheme.typography.bodySmall,
                color = JABAuthTextDim
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
