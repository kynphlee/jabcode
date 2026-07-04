package com.jabauth.diagnostic.ui.verify

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jabauth.diagnostic.verify.CertChainDetail
import com.jabauth.diagnostic.verify.CertNode
import com.jabauth.diagnostic.verify.RevocationStatus
import com.jabauth.ui.components.Badge
import com.jabauth.ui.theme.JABAuthBgBase
import com.jabauth.ui.theme.JABAuthPrimary
import com.jabauth.ui.theme.JABAuthTextPrimary
import com.jabauth.ui.theme.JABAuthTextSecondary
import com.jabauth.ui.theme.VerdictFailed
import com.jabauth.ui.theme.VerdictNeutral
import com.jabauth.ui.theme.VerdictUntrusted
import com.jabauth.ui.theme.VerdictVerified

/**
 * The Trust Anchor / certificate-detail drill-down (Phase 4, "Flow B"): the forensic view behind the
 * PKI stage. Renders the X.509 chain leaf → root (one CERTIFICATE section per [CertNode]), the leaf's
 * REVOCATION result (a status-coloured chip + method/result/checked rows), and a plain-language
 * trust-store statement for the root anchor (Principle B — the verdict is adjudicated against the app's
 * *local* trust store).
 *
 * Presentation-only and self-contained: it takes the already-computed [detail] plus an [onBack] callback
 * and owns no ViewModel / NavController. Row content comes from [TrustAnchorContent] (unit-tested on the
 * JVM); this composable only renders it in the `DiagnosticRow` label/value idiom against the emulator-DS
 * tokens. Magenta ([VerdictFailed]) is reserved for the REVOKED chip only (Principle D — scarce).
 */
@Composable
fun TrustAnchorScreen(
    detail: CertChainDetail,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(JABAuthBgBase)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // --- Back affordance + title ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = JABAuthTextSecondary,
                )
            }
            Text(
                "TRUST ANCHOR",
                color = JABAuthTextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        // --- One CERTIFICATE section per node (leaf → root) ---
        detail.nodes.forEachIndexed { index, node ->
            Spacer(Modifier.height(12.dp))
            SectionHeader("CERTIFICATE · ${chainRole(index, detail.nodes.size)}")
            TrustAnchorContent.certRows(node).forEach { (label, value) -> DetailRow(label, value) }
        }

        // --- REVOCATION section: status chip + rows ---
        Spacer(Modifier.height(16.dp))
        SectionHeader("REVOCATION")
        Spacer(Modifier.height(4.dp))
        Badge(
            text = detail.revocation.status.name,
            color = revocationColor(detail.revocation.status),
        )
        Spacer(Modifier.height(4.dp))
        TrustAnchorContent.revocationRows(detail).forEach { (label, value) -> DetailRow(label, value) }

        // --- Trust-store statement ---
        Spacer(Modifier.height(16.dp))
        SectionHeader("TRUST STORE")
        Text(
            TrustAnchorContent.trustStatement(detail),
            color = if (detail.rootTrusted) VerdictVerified else VerdictUntrusted,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** The chain position label for a node at [index] of [size] (leaf → intermediate → root). */
private fun chainRole(index: Int, size: Int): String = when {
    index == 0 -> "leaf"
    index == size - 1 -> "root"
    else -> "intermediate"
}

/** The revocation-status signal colour (green VALID, magenta REVOKED, amber UNKNOWN_OFFLINE, neutral NOT_CHECKED). */
private fun revocationColor(status: RevocationStatus): Color = when (status) {
    RevocationStatus.VALID -> VerdictVerified
    RevocationStatus.REVOKED -> VerdictFailed
    RevocationStatus.UNKNOWN_OFFLINE -> VerdictUntrusted
    RevocationStatus.NOT_CHECKED -> VerdictNeutral
}

@Composable
private fun SectionHeader(text: String) {
    Text(text, color = JABAuthPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = JABAuthTextSecondary, fontSize = 12.sp)
        Text(value, color = JABAuthTextPrimary, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
    }
}
