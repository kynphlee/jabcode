package com.jabauth.diagnostic.ui.scanner

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jabauth.jabcode.DecodeResult

// --- Transient result chip ---

@Composable
internal fun ResultChip(
    result: DecodeResult?,
    error: String?,
    stats: ScannerViewModel.ScanStats,
    modifier: Modifier = Modifier
) {
    val text: String? = when {
        result != null -> "✓ ${decodeSummary(result)}"
        error != null && stats.failCount >= 5 -> "⚠ ${failureHint(stats)}"
        stats.total == 0 -> "Hold camera over a JABCode"
        else -> null
    }
    text?.let {
        Surface(color = Color.Black.copy(alpha = 0.6f), shape = RoundedCornerShape(8.dp), modifier = modifier) {
            Text(it, color = TextPrimary, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
        }
    }
}
