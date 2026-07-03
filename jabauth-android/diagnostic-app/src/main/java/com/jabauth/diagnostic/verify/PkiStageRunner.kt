package com.jabauth.diagnostic.verify

/**
 * The PKI verification stage — **INTERIM**. On-device X.509 chain validation and OCSP/CRL revocation are
 * interface-only in the framework today (no production impl), so this stage cannot yet assert trust. The
 * honest, first-class state is therefore [StageState.WARN] with [RevocationStatus.UNKNOWN_OFFLINE] —
 * degraded/indeterminate (amber), which drives [TrustVerdict.UNTRUSTED], never a hard fail.
 *
 * Phase 6 replaces this with a real `CertificateChainValidator` + `TrustStoreManager` + OCSP/CRL, at
 * which point the stage returns PASS/FAIL with a populated chain and the interim WARN disappears. The UI
 * built against this already renders `UNKNOWN_OFFLINE` as first-class, so Phase 6 is a data swap.
 */
class PkiStageRunner : VerificationOrchestrator.StageRunner {
    override fun run(symbol: DecodedSymbol): StageResult = StageResult(
        stage = VerificationStage.PKI,
        state = StageState.WARN,
        reason = "On-device certificate-chain validation and OCSP/CRL revocation are not yet implemented " +
            "(Phase 6); trust anchor and revocation status are indeterminate offline.",
        detail = CertChainDetail(
            nodes = emptyList(),
            revocation = RevocationInfo(
                method = "OCSP + CRL",
                status = RevocationStatus.UNKNOWN_OFFLINE,
                checkedLabel = null,
            ),
        ),
    )
}
