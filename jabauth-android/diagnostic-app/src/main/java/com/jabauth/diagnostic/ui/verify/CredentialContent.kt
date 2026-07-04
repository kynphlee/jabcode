package com.jabauth.diagnostic.ui.verify

import com.jabauth.diagnostic.verify.JwtDetail
import com.jabauth.diagnostic.verify.WithheldClaim

/**
 * Pure content logic for the **Credential / selective-disclosure** drill-down (Phase 4, "Flow B"): a
 * [JwtDetail] → the header line and the label/value rows the [CredentialScreen] renders. Kept separate
 * from the composable so the disclosure wording and row ordering are unit-tested on the JVM (module
 * convention: logic in unit tests, rendering is instrumented) — mirroring [StageSummaries].
 *
 * The credential's claims split three ways:
 *  - **revealed** — the claims the holder disclosed (their values are present).
 *  - **sealed** — the claims the holder *withheld*; only the SD-JWT digest is present, and it verified
 *    (not missing, not tampered — see [WithheldClaim]).
 *  - **meta** — the envelope fields (`token_class` / `issuer` / `issued/expiry`), each shown only when set.
 */
object CredentialContent {

    /** The disclosure header, e.g. `2 of 3 claims disclosed` ([JwtDetail.disclosedCount] of `totalClaims`). */
    fun header(d: JwtDetail): String = "${d.disclosedCount} of ${d.totalClaims} claims disclosed"

    /** The revealed claims as `key → value` rows, in map iteration order. */
    fun revealedRows(d: JwtDetail): List<Pair<String, String>> = d.revealedClaims.toList()

    /** The withheld ("sealed") claims — the SD-JWT digests the holder did not disclose. */
    fun sealedRows(d: JwtDetail): List<WithheldClaim> = d.withheldDigests

    /** The envelope meta rows: `token_class` / `issuer` / `issued/expiry`, each emitted only when non-null. */
    fun metaRows(d: JwtDetail): List<Pair<String, String>> = buildList {
        d.tokenClass?.let { add("token_class" to it) }
        d.issuer?.let { add("issuer" to it) }
        d.expiry?.let { add("issued/expiry" to it) }
    }
}
