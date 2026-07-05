package com.jabauth.diagnostic.ui.scanner

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jabauth.ui.theme.VerdictUntrusted

/**
 * Dismissible warning banner shown at the top of the Scanner when the local trust store has **no anchors**,
 * so nothing scanned can reach `VERIFIED`. This is the deliberate, non-blocking treatment of that condition:
 * the design reserves a full-screen error for hard blocks, but scanning-while-untrusted is useful in a
 * diagnostic app, so it degrades to an amber banner that the user can dismiss for the session. It never
 * gates the camera.
 *
 * @param onImport action for the `[Import]` affordance — wires to the trust-anchor import flow (follow-up).
 * @param onDismiss hides the banner for the session (× affordance).
 */
@Composable
internal fun TrustStoreBanner(
    onImport: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .background(VerdictUntrusted.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "⚠ no trust anchors configured — nothing can reach VERIFIED",
            color = VerdictUntrusted, fontSize = 12.sp, modifier = Modifier.weight(1f),
        )
        Text(
            "Import",
            color = VerdictUntrusted, fontSize = 12.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable { onImport() }.padding(horizontal = 4.dp),
        )
        Text(
            "×",
            color = VerdictUntrusted, fontSize = 18.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable { onDismiss() }.padding(horizontal = 4.dp),
        )
    }
}
