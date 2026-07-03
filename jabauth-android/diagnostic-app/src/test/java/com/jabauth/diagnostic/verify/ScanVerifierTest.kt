package com.jabauth.diagnostic.verify

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The interim scan→verdict bridge. A plain symbol stays on the classic decode path (NOT_A_COA); a
 * credential-bearing symbol runs the pipeline and honestly reports the missing on-device trust anchor.
 * Pure JVM (real JWT/ABE crypto behind the orchestrator, no device).
 */
class ScanVerifierTest {

    private val verifier = ScanVerifier()

    @Test fun `a plain (non-credential) symbol is NOT_A_COA and skips the crypto stages`() {
        val r = verifier.verify("HELLO WORLD 4C".toByteArray(), decodeLatencyMs = 63)

        assertThat(r.verdict).isEqualTo(TrustVerdict.NOT_A_COA)
        assertThat(r.stage(VerificationStage.DECODE)!!.state).isEqualTo(StageState.PASS)
        assertThat(r.stage(VerificationStage.JWT)!!.state).isEqualTo(StageState.SKIPPED)
        assertThat(r.stage(VerificationStage.ABE)!!.state).isEqualTo(StageState.SKIPPED)
    }

    @Test fun `a credential-bearing symbol runs the pipeline and reports the missing trust anchor`() {
        // An "eyJ…"-prefixed payload trips the COA heuristic and drives the JWT stage.
        val r = verifier.verify("eyJhbGciOiJFUzI1NiJ9.e30.sig".toByteArray(), decodeLatencyMs = 100)

        // PKI is interim-WARN (no on-device validator yet); JWT hard-fails for lack of an issuer key.
        assertThat(r.stage(VerificationStage.PKI)!!.state).isEqualTo(StageState.WARN)
        val jwt = r.stage(VerificationStage.JWT)!!
        assertThat(jwt.state).isEqualTo(StageState.FAIL)
        assertThat(jwt.reason).contains("issuer public key")
        assertThat(r.stage(VerificationStage.ABE)!!.state).isEqualTo(StageState.SKIPPED) // short-circuit
        assertThat(r.verdict).isEqualTo(TrustVerdict.FAILED)
    }

    @Test fun `decode latency is carried into the result`() {
        assertThat(verifier.verify("HELLO".toByteArray(), decodeLatencyMs = 42).totalLatencyMs).isEqualTo(42)
    }

    @Test fun `the COA heuristic keys on the JWS header prefix`() {
        assertThat(ScanVerifier.looksLikeCoaCredential("eyJabc".toByteArray())).isTrue()
        assertThat(ScanVerifier.looksLikeCoaCredential("HELLO WORLD 4C".toByteArray())).isFalse()
    }
}
