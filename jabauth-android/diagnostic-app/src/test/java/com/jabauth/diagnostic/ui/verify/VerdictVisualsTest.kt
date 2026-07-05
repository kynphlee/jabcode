package com.jabauth.diagnostic.ui.verify

import com.google.common.truth.Truth.assertThat
import com.jabauth.diagnostic.verify.StageState
import com.jabauth.diagnostic.verify.TrustVerdict
import com.jabauth.ui.theme.JABAuthTextDim
import com.jabauth.ui.theme.VerdictFailed
import com.jabauth.ui.theme.VerdictNeutral
import com.jabauth.ui.theme.VerdictTrusted
import com.jabauth.ui.theme.VerdictUntrusted
import com.jabauth.ui.theme.VerdictVerified
import org.junit.Test

/**
 * Presentation-logic tests for the Scanner verdict surface (label + signal colour per verdict/state).
 * Pure JVM — no rendering (this module's convention; rendering is instrumented).
 */
class VerdictVisualsTest {

    @Test fun `each verdict has a human-readable label`() {
        assertThat(VerdictVisuals.label(TrustVerdict.VERIFIED)).isEqualTo("Verified")
        assertThat(VerdictVisuals.label(TrustVerdict.TRUSTED_OFFLINE)).isEqualTo("Trusted · offline")
        assertThat(VerdictVisuals.label(TrustVerdict.UNTRUSTED)).isEqualTo("Untrusted")
        assertThat(VerdictVisuals.label(TrustVerdict.FAILED)).isEqualTo("Failed")
        assertThat(VerdictVisuals.label(TrustVerdict.NOT_A_COA)).isEqualTo("Not a COA")
    }

    @Test fun `verdict colours map to the emulator-DS tokens`() {
        assertThat(VerdictVisuals.color(TrustVerdict.VERIFIED)).isEqualTo(VerdictVerified)
        assertThat(VerdictVisuals.color(TrustVerdict.TRUSTED_OFFLINE)).isEqualTo(VerdictTrusted)
        assertThat(VerdictVisuals.color(TrustVerdict.UNTRUSTED)).isEqualTo(VerdictUntrusted)
        assertThat(VerdictVisuals.color(TrustVerdict.FAILED)).isEqualTo(VerdictFailed)
        assertThat(VerdictVisuals.color(TrustVerdict.NOT_A_COA)).isEqualTo(VerdictNeutral)
    }

    @Test fun `magenta is load-bearing — only a FAILED verdict is magenta (Principle D)`() {
        assertThat(TrustVerdict.values().filter { VerdictVisuals.color(it) == VerdictFailed })
            .containsExactly(TrustVerdict.FAILED)
    }

    @Test fun `the offline-trusted tier is visually distinct from VERIFIED (never the same green)`() {
        // The council's one live failure mode: users misreading TRUSTED_OFFLINE as VERIFIED. Colour must differ.
        assertThat(VerdictVisuals.color(TrustVerdict.TRUSTED_OFFLINE))
            .isNotEqualTo(VerdictVisuals.color(TrustVerdict.VERIFIED))
    }

    @Test fun `stage dots map pass-green, warn-amber, fail-magenta, skipped-dim`() {
        assertThat(VerdictVisuals.dotColor(StageState.PASS)).isEqualTo(VerdictVerified)
        assertThat(VerdictVisuals.dotColor(StageState.WARN)).isEqualTo(VerdictUntrusted)
        assertThat(VerdictVisuals.dotColor(StageState.FAIL)).isEqualTo(VerdictFailed)
        assertThat(VerdictVisuals.dotColor(StageState.SKIPPED)).isEqualTo(JABAuthTextDim)
    }
}
