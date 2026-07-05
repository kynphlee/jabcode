package com.jabauth.diagnostic.ui.verify

import androidx.compose.ui.graphics.Color
import com.jabauth.diagnostic.verify.StageState
import com.jabauth.diagnostic.verify.TrustVerdict
import com.jabauth.ui.theme.JABAuthTextDim
import com.jabauth.ui.theme.VerdictFailed
import com.jabauth.ui.theme.VerdictNeutral
import com.jabauth.ui.theme.VerdictTrusted
import com.jabauth.ui.theme.VerdictUntrusted
import com.jabauth.ui.theme.VerdictVerified

/**
 * Presentation mapping for the Scanner verdict surface: verdict / stage-state → label + signal colour.
 *
 * Kept as pure functions, separate from the composables, so the label and colour rules are unit-tested on
 * the JVM without rendering — matching this module's convention (full rendering is instrumented). Colours
 * are the emulator-DS verdict tokens; magenta ([com.jabauth.ui.theme.VerdictFailed]) is reserved for hard
 * fail only (Principle D — scarce and load-bearing).
 */
object VerdictVisuals {

    /** The one-glance label for each rollup verdict (natural case; [Badge] uppercases for display). */
    fun label(verdict: TrustVerdict): String = when (verdict) {
        TrustVerdict.VERIFIED -> "Verified"
        TrustVerdict.TRUSTED_OFFLINE -> "Trusted · offline"
        TrustVerdict.UNTRUSTED -> "Untrusted"
        TrustVerdict.FAILED -> "Failed"
        TrustVerdict.NOT_A_COA -> "Not a COA"
    }

    /** The signal colour for each rollup verdict. */
    fun color(verdict: TrustVerdict): Color = when (verdict) {
        TrustVerdict.VERIFIED -> VerdictVerified
        TrustVerdict.TRUSTED_OFFLINE -> VerdictTrusted
        TrustVerdict.UNTRUSTED -> VerdictUntrusted
        TrustVerdict.FAILED -> VerdictFailed
        TrustVerdict.NOT_A_COA -> VerdictNeutral
    }

    /** The pipeline-strip dot colour for each stage state. */
    fun dotColor(state: StageState): Color = when (state) {
        StageState.PASS -> VerdictVerified
        StageState.WARN -> VerdictUntrusted
        StageState.FAIL -> VerdictFailed
        StageState.SKIPPED -> JABAuthTextDim
    }
}
