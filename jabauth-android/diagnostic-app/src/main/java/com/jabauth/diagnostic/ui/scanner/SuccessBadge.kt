package com.jabauth.diagnostic.ui.scanner

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// --- Rolling success badge ---

@Composable
internal fun SuccessBadge(stats: ScannerViewModel.ScanStats, modifier: Modifier = Modifier) {
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
