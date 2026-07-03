package com.jabauth.diagnostic.verify

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * PKI-stage interim behavior: with no on-device chain validator / trust store yet (Phase 6), the stage is
 * honestly WARN / UNKNOWN_OFFLINE — never a spurious PASS or FAIL. These assertions flip to real
 * VALID/REVOKED verdicts when the PKI impl lands.
 */
class PkiStageRunnerTest {

    private fun result() = PkiStageRunner().run(DecodedSymbol(byteArrayOf(1), true, 10L, FormatProfile("v2", "FIELD")))

    @Test fun `interim PKI is WARN, not a spurious pass or fail`() {
        assertThat(result().state).isEqualTo(StageState.WARN)
    }

    @Test fun `revocation is UNKNOWN_OFFLINE (first-class indeterminate)`() {
        val d = result().detail as CertChainDetail
        assertThat(d.revocation.status).isEqualTo(RevocationStatus.UNKNOWN_OFFLINE)
        assertThat(d.revocation.method).isEqualTo("OCSP + CRL")
        assertThat(d.revocation.checkedLabel).isNull()
    }

    @Test fun `reason names the Phase 6 gap so the WARN is explained`() {
        assertThat(result().reason).ignoringCase().contains("revocation")
    }

    @Test fun `no trust anchor can be asserted yet`() {
        val d = result().detail as CertChainDetail
        assertThat(d.nodes).isEmpty()
        assertThat(d.rootTrusted).isFalse()
    }
}
