package com.jabauth.diagnostic.ui.verify

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.jabauth.diagnostic.verify.TrustVerdict
import com.jabauth.ui.components.Badge

/**
 * The one-glance trust verdict, shown on the Scanner **beside** the decode `ResultChip` (never replacing
 * it — open-Q1: the classic decode result is never lost). Reuses the [Badge] idiom; label + colour come
 * from [VerdictVisuals].
 */
@Composable
fun TrustVerdictBadge(verdict: TrustVerdict, modifier: Modifier = Modifier) {
    Badge(text = VerdictVisuals.label(verdict), color = VerdictVisuals.color(verdict), modifier = modifier)
}
