package com.jabauth.diagnostic.ui.scanner

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// --- Workload coach: decode-region load (% of frame) + guidance ---

@Composable
internal fun WorkloadCoach(
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
        Text("Drag to aim · long-press ⤡ to resize · ↺ reset.", color = TextSecondary, fontSize = 13.sp)
    }
}
