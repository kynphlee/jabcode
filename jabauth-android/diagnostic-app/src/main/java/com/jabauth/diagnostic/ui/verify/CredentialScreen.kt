package com.jabauth.diagnostic.ui.verify

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.jabauth.diagnostic.verify.JwtDetail
import com.jabauth.diagnostic.verify.WithheldClaim
import com.jabauth.ui.theme.JABAuthBgBase
import com.jabauth.ui.theme.JABAuthPrimary
import com.jabauth.ui.theme.JABAuthTextCode
import com.jabauth.ui.theme.JABAuthTextDim
import com.jabauth.ui.theme.JABAuthTextPrimary
import com.jabauth.ui.theme.JABAuthTextSecondary
import com.jabauth.ui.theme.Spacing

/**
 * The **Credential / selective-disclosure** drill-down (Phase 4, "Flow B").
 *
 * Renders a JWT credential's disclosed-vs-withheld claims from a [JwtDetail]: the disclosure header, a
 * REVEALED section (the claims the holder disclosed), a WITHHELD · SEALED section (the claims the holder
 * withheld — only the SD-JWT digest is shown, and it verified: not missing, not tampered), and the
 * envelope meta rows (`token_class` / `issuer` / `issued/expiry`). The content itself is computed by the
 * pure [CredentialContent] object (unit-tested on the JVM); this composable only lays it out in the
 * design-system idiom (the `DiagnosticRow` label/value pattern, [com.jabauth.ui.theme] tokens).
 *
 * A leaf drill-down: it takes [detail] + [onBack] only — no ViewModel, no NavController.
 */
@Composable
fun CredentialScreen(
    detail: JwtDetail,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(Spacing.md)
            .verticalScroll(rememberScrollState())
    ) {
        // Back affordance + title.
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = JABAuthTextPrimary,
                )
            }
            Text(
                "Credential",
                color = JABAuthTextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        // Disclosure header, e.g. "2 of 3 claims disclosed".
        Text(
            CredentialContent.header(detail),
            color = JABAuthTextSecondary,
            fontSize = 13.sp,
            modifier = Modifier.padding(start = Spacing.xs, top = Spacing.xxs),
        )

        // REVEALED — the claims the holder disclosed.
        SectionHeader("REVEALED")
        val revealed = CredentialContent.revealedRows(detail)
        if (revealed.isEmpty()) {
            Text(
                "no claims disclosed",
                color = JABAuthTextDim,
                fontSize = 12.sp,
                modifier = Modifier.padding(vertical = Spacing.xxs),
            )
        } else {
            revealed.forEach { (label, value) -> LabelValueRow(label, value) }
        }

        // WITHHELD · SEALED — the digests the holder withheld (verified, not disclosed).
        SectionHeader("WITHHELD · SEALED")
        val sealed = CredentialContent.sealedRows(detail)
        if (sealed.isEmpty()) {
            Text(
                "nothing withheld — fully disclosed",
                color = JABAuthTextDim,
                fontSize = 12.sp,
                modifier = Modifier.padding(vertical = Spacing.xxs),
            )
        } else {
            sealed.forEach { SealedRow(it) }
        }

        // Envelope meta — token_class / issuer / issued-expiry (each only when present).
        val meta = CredentialContent.metaRows(detail)
        if (meta.isNotEmpty()) {
            SectionHeader("CREDENTIAL")
            meta.forEach { (label, value) -> LabelValueRow(label, value) }
        }
    }
}

/** An orange, bold section header — matches the scanner diagnostic-panel section style. */
@Composable
private fun SectionHeader(text: String) {
    Spacer(Modifier.height(Spacing.sm))
    Text(text, color = JABAuthPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(Spacing.xxs))
}

/** The `DiagnosticRow` label/value idiom: dim label left, monospace value right. */
@Composable
private fun LabelValueRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xxxs),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = JABAuthTextSecondary, fontSize = 12.sp)
        Text(value, color = JABAuthTextPrimary, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
    }
}

/**
 * One sealed (withheld) claim: a lock glyph + the claim name, the digest in monospace, and the
 * "not disclosed by holder — digest verified" caption so the row reads as *withheld-and-proven*, never
 * missing or tampered.
 */
@Composable
private fun SealedRow(claim: WithheldClaim) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xxs)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("🔒 ${claim.name}", color = JABAuthTextSecondary, fontSize = 12.sp)
            Text(
                claim.digest,
                color = JABAuthTextCode,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
        Text(
            "not disclosed by holder — digest verified",
            color = JABAuthTextDim,
            fontSize = 11.sp,
        )
    }
}
