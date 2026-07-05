package com.jabauth.diagnostic.ui.verify

import com.google.common.truth.Truth.assertThat
import com.jabauth.diagnostic.verify.TrustVerdict
import org.junit.Test

/**
 * Presentation-logic tests for the collapsed [VerdictCard]'s subtitle copy. Pure JVM — no rendering
 * (this module's convention). The point of these is honesty: the subtitle must be verdict-appropriate and
 * must never claim "trusted" for a verdict that is not VERIFIED.
 */
class VerdictCopyTest {

    @Test fun `each verdict maps to its design-mock subtitle`() {
        assertThat(VerdictCopy.subtitle(TrustVerdict.VERIFIED)).isEqualTo("credential trusted · verified on-device")
        assertThat(VerdictCopy.subtitle(TrustVerdict.TRUSTED_OFFLINE)).isEqualTo("trusted offline · revocation unchecked")
        assertThat(VerdictCopy.subtitle(TrustVerdict.UNTRUSTED)).isEqualTo("untrusted · tap to see why")
        assertThat(VerdictCopy.subtitle(TrustVerdict.FAILED)).isEqualTo("verification failed · tap for detail")
        assertThat(VerdictCopy.subtitle(TrustVerdict.NOT_A_COA)).isEqualTo("not a COA credential")
    }

    @Test fun `only the VERIFIED verdict makes a positive trusted claim`() {
        // Guards the honesty rule: "credential trusted" is the affirmative claim and must be VERIFIED-only.
        // (UNTRUSTED's copy contains the substring "trusted" inside the word "untrusted" — not a claim.)
        assertThat(TrustVerdict.values().filter { VerdictCopy.subtitle(it).contains("credential trusted") })
            .containsExactly(TrustVerdict.VERIFIED)
        assertThat(VerdictCopy.subtitle(TrustVerdict.UNTRUSTED)).contains("untrusted")
    }

    @Test fun `the offline-trusted subtitle carries the revocation caveat and never says verified`() {
        // A′ honesty: the teal tier is a positive signal, but it must name its reservation and never claim VERIFIED.
        val s = VerdictCopy.subtitle(TrustVerdict.TRUSTED_OFFLINE)
        assertThat(s).contains("revocation")
        assertThat(s).doesNotContain("verified")
        assertThat(s).doesNotContain("credential trusted")
    }

    @Test fun `every verdict has a non-blank subtitle`() {
        TrustVerdict.values().forEach { assertThat(VerdictCopy.subtitle(it)).isNotEmpty() }
    }
}
