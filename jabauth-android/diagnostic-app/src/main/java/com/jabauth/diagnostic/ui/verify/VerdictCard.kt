package com.jabauth.diagnostic.ui.verify

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jabauth.diagnostic.verify.StageResult
import com.jabauth.diagnostic.verify.StageState
import com.jabauth.diagnostic.verify.TrustVerdict
import com.jabauth.diagnostic.verify.VerificationStage
import com.jabauth.ui.theme.JABAuthBgCard
import com.jabauth.ui.theme.JABAuthTextSecondary

/**
 * The Scanner's collapsed verdict surface — the compact card that overlays the camera preview and, on tap,
 * opens the full Verification HUD. It replaces the bare [TrustVerdictBadge] + [PipelineStageStrip] pair with
 * the design mock's card: verdict dot + label, a verdict-appropriate subtitle, a **labeled** stage strip
 * (`DECODE ● · PKI ● · JWT ● · ABE ●`), and a `TAP → HUD` affordance.
 *
 * All colours and labels are reused from the existing presentation helpers ([VerdictVisuals],
 * [VerificationSummaryContent]) — this composable adds layout only, never a parallel colour or label system.
 */
@Composable
fun VerdictCard(
    verdict: TrustVerdict,
    stages: List<StageResult>,
    modifier: Modifier = Modifier,
) {
    val verdictColor = VerdictVisuals.color(verdict)
    Column(
        modifier = modifier
            .background(JABAuthBgCard.copy(alpha = 0.92f), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Verdict dot + label.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(verdictColor))
            Spacer(Modifier.width(8.dp))
            Text(
                VerdictVisuals.label(verdict).uppercase(),
                color = verdictColor, fontSize = 14.sp, fontWeight = FontWeight.Bold,
            )
        }
        // Verdict-appropriate, honest subtitle (never hardcoded "trusted").
        Text(VerdictCopy.subtitle(verdict), color = JABAuthTextSecondary, fontSize = 11.sp)
        // Labeled stage strip — tag + coloured dot per stage.
        LabeledStageStrip(stages)
        // Affordance hint.
        Text(
            "TAP → HUD",
            color = JABAuthTextSecondary, fontSize = 9.sp, letterSpacing = 0.1.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
}

/**
 * The labeled pipeline strip: one `TAG ●` segment per stage in canonical order (DECODE · PKI · JWT · ABE).
 * The tag comes from [VerificationSummaryContent.tag] and the dot colour from [VerdictVisuals.dotColor];
 * a stage absent from [stages] reads as [StageState.SKIPPED] (dim), matching [PipelineStageStrip].
 */
@Composable
private fun LabeledStageStrip(stages: List<StageResult>, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        VerificationStage.values().forEach { stage ->
            val state = stages.firstOrNull { it.stage == stage }?.state ?: StageState.SKIPPED
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    VerificationSummaryContent.tag(stage),
                    color = JABAuthTextSecondary, fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(4.dp))
                Box(Modifier.size(7.dp).clip(CircleShape).background(VerdictVisuals.dotColor(state)))
            }
        }
    }
}

/**
 * Pure verdict → subtitle copy for the collapsed [VerdictCard]. Kept separate from the composable (this
 * module's convention — see [VerdictVisuals]/[VerificationSummaryContent]) so the honesty of the wording is
 * unit-tested on the JVM. Each line is verdict-appropriate: it states the trust outcome and, when not
 * VERIFIED, points the user at the HUD for the reason — it never claims "trusted" for a non-verified result.
 */
object VerdictCopy {
    fun subtitle(verdict: TrustVerdict): String = when (verdict) {
        TrustVerdict.VERIFIED -> "credential trusted · verified on-device"
        TrustVerdict.UNTRUSTED -> "untrusted · tap to see why"
        TrustVerdict.FAILED -> "verification failed · tap for detail"
        TrustVerdict.NOT_A_COA -> "not a COA credential"
    }
}
