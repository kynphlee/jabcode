package com.jabauth.diagnostic.ui.verify

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.jabauth.diagnostic.verify.StageResult
import com.jabauth.diagnostic.verify.StageState
import com.jabauth.diagnostic.verify.VerificationStage

/**
 * The four-segment pipeline strip — one status dot per stage in canonical order (DECODE · PKI · JWT · ABE),
 * showing exactly where a failure short-circuited (a `SKIPPED` stage reads dim). Always renders four dots;
 * a stage absent from [stages] is treated as [StageState.SKIPPED]. Reuses the per-`Nc` stat-strip idiom.
 */
@Composable
fun PipelineStageStrip(stages: List<StageResult>, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        VerificationStage.values().forEach { stage ->
            val state = stages.firstOrNull { it.stage == stage }?.state ?: StageState.SKIPPED
            Box(Modifier.size(8.dp).clip(CircleShape).background(VerdictVisuals.dotColor(state)))
        }
    }
}
