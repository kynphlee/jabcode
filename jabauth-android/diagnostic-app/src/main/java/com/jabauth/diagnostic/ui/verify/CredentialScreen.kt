package com.jabauth.diagnostic.ui.verify

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jabauth.diagnostic.verify.JwtDetail
import com.jabauth.diagnostic.verify.WithheldClaim
import com.jabauth.ui.theme.JABAuthBgBase
import com.jabauth.ui.theme.JABAuthBgCard
import com.jabauth.ui.theme.JABAuthBorder
import com.jabauth.ui.theme.JABAuthPrimary
import com.jabauth.ui.theme.JABAuthTextCode
import com.jabauth.ui.theme.JABAuthTextDim
import com.jabauth.ui.theme.JABAuthTextPrimary
import com.jabauth.ui.theme.JABAuthTextSecondary
import com.jabauth.ui.theme.ModJwt
import com.jabauth.ui.theme.Spacing

/**
 * The **Credential / selective-disclosure** drill-down (Phase 4, "Flow B").
 *
 * Renders a JWT credential's disclosed-vs-withheld claims from a [JwtDetail]: a title row (back +
 * `credential` + the [ModJwt] "JWT" module tag + the `token_class` at top-right), the disclosure header
 * ("N of M claims disclosed"), a REVEALED section (each disclosed claim in its own rounded **card** row),
 * a WITHHELD · SEALED section (each withheld claim as a card — a lock glyph + a short SD-JWT digest hint,
 * which verified: not missing, not tampered), and the remaining envelope meta rows (`issuer` / `issued /
 * expiry`). The content itself is computed by the pure [CredentialContent] object (unit-tested on the JVM);
 * this composable only lays it out in the design-system idiom ([com.jabauth.ui.theme] tokens, the sibling
 * [VerificationSummary] card-row pattern).
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
            .background(JABAuthBgBase)
            .padding(Spacing.md)
            .verticalScroll(rememberScrollState())
    ) {
        // Title row: back + "credential" + JWT module tag on the left, token_class at top-right.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = JABAuthTextPrimary,
                )
            }
            Text(
                "credential",
                color = JABAuthTextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(Spacing.xs))
            ModuleTag("JWT")
            Spacer(Modifier.weight(1f))
            detail.tokenClass?.let { tokenClass ->
                Text(
                    "token_class: $tokenClass",
                    color = ModJwt,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }

        // Disclosure header, e.g. "2 of 3 claims disclosed".
        Text(
            CredentialContent.header(detail),
            color = JABAuthTextSecondary,
            fontSize = 13.sp,
            modifier = Modifier.padding(start = Spacing.xs, top = Spacing.xxs),
        )

        // REVEALED — the claims the holder disclosed, each in its own card row.
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
            revealed.forEach { (label, value) -> RevealedCard(label, value) }
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
            sealed.forEach { SealedCard(it) }
            Spacer(Modifier.height(Spacing.xxs))
            Text(
                "digests verified against the signed _sd array — withheld, not missing, not tampered.",
                color = JABAuthTextDim,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = Spacing.xxs),
            )
        }

        // Envelope meta — issuer / issued-expiry. token_class is surfaced at the top-right of the title
        // row (the mock's placement), so drop it here to avoid rendering it twice.
        val meta = CredentialContent.metaRows(detail).filterNot { it.first == "token_class" }
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

/** The small [ModJwt] module-identity tag next to the title (mock's "JWT" chip). */
@Composable
private fun ModuleTag(text: String) {
    Text(
        text,
        color = ModJwt,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier
            .background(ModJwt.copy(alpha = 0.15f), RoundedCornerShape(3.dp))
            .padding(horizontal = Spacing.xs, vertical = Spacing.xxxs),
    )
}

/**
 * One REVEALED claim as a rounded card row: the claim key left (dim), the disclosed value right
 * (monospace). Card surface + 1dp border per the DS card spec, compact padding for a list row.
 */
@Composable
private fun RevealedCard(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xxxs)
            .background(JABAuthBgCard, RoundedCornerShape(8.dp))
            .border(1.dp, JABAuthBorder, RoundedCornerShape(8.dp))
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = JABAuthTextSecondary, fontSize = 12.sp)
        Text(value, color = JABAuthTextPrimary, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
    }
}

/**
 * One sealed (withheld) claim as a card: a lock glyph + a neutral label, and a SHORT SD-JWT digest hint
 * (`first…last`) on the right in monospace. HONEST framing: an SD-JWT withheld claim exposes only its
 * digest, not a name — so a blank [WithheldClaim.name] falls back to "sealed claim" rather than inventing
 * one. The row reads as *withheld-and-proven*, never missing or tampered (see the section caption).
 */
@Composable
private fun SealedCard(claim: WithheldClaim) {
    val label = claim.name.takeIf { it.isNotBlank() } ?: "sealed claim"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xxxs)
            .background(JABAuthBgCard, RoundedCornerShape(8.dp))
            .border(1.dp, JABAuthBorder, RoundedCornerShape(8.dp))
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("🔒 $label", color = JABAuthTextSecondary, fontSize = 12.sp)
        Text(
            "(${digestHint(claim.digest)})",
            color = JABAuthTextCode,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
}

/** The `DiagnosticRow` label/value idiom: dim label left, monospace value right (for meta rows). */
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
 * A short display hint for an SD-JWT digest: `first…last`, e.g. `a3f2…8c` — enough to disambiguate a
 * sealed claim without implying its full opaque value carries meaning. Digests too short to abbreviate
 * are returned whole.
 */
internal fun digestHint(digest: String): String =
    if (digest.length <= 8) digest else "${digest.take(4)}…${digest.takeLast(2)}"
