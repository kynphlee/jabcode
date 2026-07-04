package com.jabauth.diagnostic.ui.verify

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.jabauth.diagnostic.verify.AbeDetail
import com.jabauth.diagnostic.verify.StageResult
import com.jabauth.diagnostic.verify.VerificationResult
import com.jabauth.diagnostic.verify.VerificationStage
import com.jabauth.ui.theme.JABAuthBgCard
import com.jabauth.ui.theme.JABAuthTextCode
import com.jabauth.ui.theme.JABAuthTextDim
import com.jabauth.ui.theme.JABAuthTextPrimary
import com.jabauth.ui.theme.JABAuthTextSecondary
import com.jabauth.ui.theme.ModAbe
import com.jabauth.ui.theme.ModJabcode
import com.jabauth.ui.theme.ModJwt
import com.jabauth.ui.theme.ModPki
import com.jabauth.ui.theme.VerdictFailed
import com.jabauth.ui.theme.VerdictVerified

/**
 * The verification-forward HUD summary (Flow A) — the panel led by its verdict, per the design mock:
 * `verification ● VERIFIED`, a `FORMAT · PROFILE · ON-DEVICE` line, the four stage rows
 * (dot · tag · name · right-value), and the ABE policy inline. Tapping a stage row (PKI/JWT/ABE) opens
 * its drill-down. This is the panel's primary content; the classic decode forensics sit below it.
 */
@Composable
fun VerificationSummary(
    result: VerificationResult,
    onStageClick: (VerificationStage) -> Unit,
    modifier: Modifier = Modifier,
) {
    val verdictColor = VerdictVisuals.color(result.verdict)
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("verification", color = JABAuthTextSecondary, fontSize = 14.sp)
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(8.dp).clip(CircleShape).background(verdictColor))
            Spacer(Modifier.width(6.dp))
            Text(VerdictVisuals.label(result.verdict).uppercase(), color = verdictColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Text(
            "FORMAT ${result.formatProfile.payloadVersion.uppercase()} · PROFILE ${result.formatProfile.coaProfile ?: "—"} · ON-DEVICE",
            color = JABAuthTextDim, fontSize = 10.sp, letterSpacing = 0.1.sp, modifier = Modifier.padding(top = 3.dp),
        )
        Spacer(Modifier.height(10.dp))
        result.stages.forEach { StageRow(it, onStageClick) }
        (result.stage(VerificationStage.ABE)?.detail as? AbeDetail)?.let {
            Spacer(Modifier.height(10.dp))
            AbePolicyInline(it)
        }
    }
}

@Composable
private fun StageRow(stage: StageResult, onStageClick: (VerificationStage) -> Unit) {
    val dot = VerdictVisuals.dotColor(stage.state)
    val tagColor = when (stage.stage) {
        VerificationStage.DECODE -> ModJabcode
        VerificationStage.PKI -> ModPki
        VerificationStage.JWT -> ModJwt
        VerificationStage.ABE -> ModAbe
    }
    Row(
        modifier = Modifier.fillMaxWidth()
            .clickable(enabled = stage.stage != VerificationStage.DECODE) { onStageClick(stage.stage) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(10.dp))
        Text(
            VerificationSummaryContent.tag(stage.stage), color = tagColor, fontSize = 10.sp,
            fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, modifier = Modifier.width(66.dp),
        )
        Text(VerificationSummaryContent.name(stage.stage), color = JABAuthTextPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(VerificationSummaryContent.value(stage), color = dot, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun AbePolicyInline(abe: AbeDetail) {
    Column(Modifier.fillMaxWidth()) {
        Text("POLICY", color = JABAuthTextSecondary, fontSize = 10.sp, letterSpacing = 0.1.sp)
        Box(
            Modifier.fillMaxWidth().padding(top = 4.dp)
                .background(JABAuthBgCard, RoundedCornerShape(4.dp)).padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Text(abe.policyExpression, color = JABAuthTextCode, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            abe.verifierAttributes.forEach { attr ->
                val c = if (attr.satisfied) VerdictVerified else VerdictFailed
                Text(
                    "${attr.name} ${if (attr.satisfied) "✓" else "✗"}", color = c, fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.background(c.copy(alpha = 0.15f), RoundedCornerShape(3.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            if (abe.granted) "ACCESS GRANTED · attributes satisfy policy" else "ACCESS DENIED · missing ${abe.failingClause}",
            color = if (abe.granted) VerdictVerified else VerdictFailed, fontSize = 12.sp, fontWeight = FontWeight.Bold,
        )
    }
}
