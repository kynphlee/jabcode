package com.jabauth.diagnostic.ui.verify

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.jabauth.diagnostic.verify.AbeDetail
import com.jabauth.diagnostic.verify.PolicyAttribute
import com.jabauth.ui.components.Badge
import com.jabauth.ui.components.JABAuthCard
import com.jabauth.ui.theme.JABAuthBgBase
import com.jabauth.ui.theme.JABAuthBorder
import com.jabauth.ui.theme.JABAuthTextCode
import com.jabauth.ui.theme.JABAuthTextPrimary
import com.jabauth.ui.theme.JABAuthTextSecondary
import com.jabauth.ui.theme.Spacing
import com.jabauth.ui.theme.VerdictFailed
import com.jabauth.ui.theme.VerdictVerified

/**
 * The **ABE Policy / access** drill-down (Phase 4, "Flow B").
 *
 * Renders, top to bottom: a back affordance, the CP-ABE access policy (`POLICY`, monospace), the verifier's
 * attributes as satisfied/missing chips (`VERIFIER ATTRIBUTES`), and a prominent plain-language verdict.
 *
 * Colour is load-bearing and reuses the DS verdict tokens: green ([VerdictVerified]) for a satisfied
 * attribute / a granted verdict, magenta ([VerdictFailed], Principle D — scarce) for a missing attribute /
 * a denied verdict. The deny line *names* the failing clause (Principle A), computed by
 * [AbePolicyContent.verdictLine] — the wording is unit-tested there, not here.
 *
 * A leaf composable: it takes only [detail] + [onBack] (no ViewModel / NavController) so it renders from a
 * plain [AbeDetail] and is trivial to preview and instrument.
 *
 * @param detail the ABE stage's forensic payload (policy, verifier attributes, verdict, failing clause)
 * @param onBack invoked when the back affordance is tapped
 * @param modifier outer modifier (size / layout)
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AbePolicyScreen(
    detail: AbeDetail,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val granted = detail.granted
    val verdictColor = if (granted) VerdictVerified else VerdictFailed

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(JABAuthBgBase)
            .verticalScroll(rememberScrollState())
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        // Back affordance.
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.Filled.ArrowBack,
                contentDescription = "Back",
                tint = JABAuthTextPrimary,
            )
        }

        // POLICY — the CP-ABE access expression, rendered monospace so operators can parse the clauses.
        PolicySection(title = "POLICY") {
            Text(
                text = detail.policyExpression,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                color = JABAuthTextCode,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // VERIFIER ATTRIBUTES — one chip per attribute; green = satisfied, magenta = missing.
        PolicySection(title = "VERIFIER ATTRIBUTES") {
            val attributes = AbePolicyContent.attributes(detail)
            if (attributes.isEmpty()) {
                Text(
                    text = "No verifier attributes presented",
                    style = MaterialTheme.typography.bodyMedium,
                    color = JABAuthTextSecondary,
                )
            } else {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    attributes.forEach { attribute -> AttributeChip(attribute) }
                }
            }
        }

        // The prominent plain-language verdict — green if granted, magenta if denied. Names the failing
        // clause on deny (Principle A); the exact wording lives in AbePolicyContent.verdictLine.
        Text(
            text = AbePolicyContent.verdictLine(detail),
            style = MaterialTheme.typography.headlineLarge,
            color = verdictColor,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** One attribute rendered as a [Badge]: green when satisfied, magenta when missing (Principle D). */
@Composable
private fun AttributeChip(attribute: PolicyAttribute) {
    Badge(
        text = attribute.name,
        color = if (attribute.satisfied) VerdictVerified else VerdictFailed,
    )
}

/**
 * A titled section: an uppercase-tracked header, a hairline divider, then [content] — mirrors the
 * `DetailSection` idiom of the sibling drill-downs so the drill-down screens read consistently.
 */
@Composable
private fun PolicySection(
    title: String,
    content: @Composable () -> Unit,
) {
    JABAuthCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = JABAuthTextSecondary,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .padding(vertical = Spacing.sm)
                .background(JABAuthBorder),
        )
        content()
    }
}
