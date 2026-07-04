package com.jabauth.diagnostic.ui.scanner

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jabauth.diagnostic.ui.verify.StageSummaries
import com.jabauth.diagnostic.ui.verify.VerificationSummary
import com.jabauth.diagnostic.verify.VerificationResult
import com.jabauth.diagnostic.verify.VerificationStage
import com.jabauth.jabcode.DecodeResult

// --- HUD-toggled diagnostic panel (preserves all prior diagnostic data) ---

@Composable
internal fun DiagnosticPanel(
    lastResult: DecodeResult?,
    lastError: String?,
    decodeHistory: List<DecodeResult>,
    decodeTimeStats: ScannerViewModel.DecodeTimeStats?,
    perNcStats: Map<Int, ScannerViewModel.DecodeTimeStats>,
    verificationResult: VerificationResult? = null,
    onStageClick: (VerificationStage) -> Unit = {},
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
            // For a COA the panel is verification-forward (mock board -1): the verdict + four stage rows lead,
            // and the JABCODE row already carries decode latency — so the verbose codec forensics collapse
            // behind a toggle rather than forming a second panel. For a non-COA / plain decode, the forensics
            // ARE the content, so they stay expanded.
            var codecExpanded by remember { mutableStateOf(verificationResult == null) }
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                verificationResult?.let { v ->
                    VerificationSummary(result = v, onStageClick = onStageClick)
                    Spacer(Modifier.height(14.dp))
                }
                if (verificationResult != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { codecExpanded = !codecExpanded }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(if (codecExpanded) "▾" else "▸", color = TextSecondary, fontSize = 12.sp, modifier = Modifier.width(16.dp))
                        Text("codec diagnostics", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.1.sp)
                        lastResult?.let {
                            Spacer(Modifier.width(8.dp))
                            Text("${it.colorMode} · ${it.decodeTimeMs}ms · ${it.data.size} B", color = TextSecondary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                    if (codecExpanded) {
                        Spacer(Modifier.height(8.dp))
                        DecodeForensics(lastResult, lastError, decodeHistory, decodeTimeStats, perNcStats)
                    }
                } else {
                    DecodeForensics(lastResult, lastError, decodeHistory, decodeTimeStats, perNcStats)
                }
            }
        }
    }
}

/** The classic codec forensics — decode summary, history, per-Nc timings, decoded data + hex dump. */
@Composable
private fun DecodeForensics(
    lastResult: DecodeResult?,
    lastError: String?,
    decodeHistory: List<DecodeResult>,
    decodeTimeStats: ScannerViewModel.DecodeTimeStats?,
    perNcStats: Map<Int, ScannerViewModel.DecodeTimeStats>,
) {
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
