package com.jabauth.client.jwt

/**
 * Semantic class of a JWT, controlling replay (single-use) policy on the
 * verify path. Emitted as the signed `token_class` claim so the semantics are
 * tamper-proof and travel with the token (mirrors the server, issue #61).
 *
 * Security default is [SESSION]. An un-marked token is a single-use
 * session/access token: its JTI is consumed on first verify and a second
 * verify is rejected as a replay. Only an explicit [ARTIFACT] token is
 * re-verifiable. Re-verifiability is therefore opt-in — never the default — so
 * a forgotten or mis-set class can only ever be *more* restrictive
 * (single-use), never less.
 *
 * [ARTIFACT] relaxes ONLY the single-use replay consumption. Every other check
 * — signature, exp/nbf, issuer/audience, and critically blacklist/revocation —
 * stays unconditional.
 */
enum class TokenClass(
    /** The exact, wire-stable value written to / read from the `token_class` claim. */
    val claimValue: String,
) {

    /**
     * Single-use session/access token (the secure default). The JTI is consumed
     * on first verify; a second verify of the same token is a replay and is
     * rejected.
     */
    SESSION("session"),

    /**
     * Re-verifiable artifact token, e.g. the JWT embedded in a long-lived COA /
     * credential. Re-presentation is legitimate — scanning the same physical
     * artifact again is the whole point — so the single-use replay consumption
     * is skipped. Anti-counterfeit security rests on the PKI signature, ABE
     * policy and the physical artifact, plus the still-enforced
     * blacklist/revocation checks — not on token single-use.
     */
    ARTIFACT("artifact");

    companion object {
        /** The signed `token_class` claim name. */
        const val CLAIM_NAME: String = "token_class"

        /**
         * Resolve a `token_class` claim value to a class, failing safe to
         * [SESSION] (single-use) for null, blank, or any unrecognised value. A
         * token is treated as re-verifiable only when its verified claim is
         * exactly `"artifact"` — so neither a missing claim (legacy tokens) nor
         * a malformed one can grant re-verifiability.
         */
        @JvmStatic
        fun fromClaim(claimValue: String?): TokenClass =
            if (ARTIFACT.claimValue == claimValue) ARTIFACT else SESSION
    }
}

/**
 * Replay policy for single-use [TokenClass.SESSION] tokens. Artifact-class
 * tokens are re-verifiable and never consumed here.
 *
 * The store is a set of consumed JTIs. `consumeIfSession` returns true when the
 * token may proceed and false when it is a replay of an already-consumed
 * session token. This mirrors the server's "artifact re-verifies, session
 * single-use" semantics; a durable/expiring store can be substituted where a
 * process-lifetime set is insufficient.
 */
class ReplayPolicy(
    private val consumedJtis: MutableSet<String> = java.util.Collections.synchronizedSet(HashSet())
) {

    /**
     * Enforce single-use for a [TokenClass.SESSION] token identified by [jti].
     *
     * @return true if the token may proceed; false if it is a replay of an
     *   already-consumed session token. [TokenClass.ARTIFACT] tokens always
     *   return true (re-verifiable). A session token with no JTI cannot be
     *   tracked and is rejected (false) to fail closed.
     */
    fun consumeIfSession(tokenClass: TokenClass, jti: String?): Boolean {
        if (tokenClass == TokenClass.ARTIFACT) {
            return true
        }
        if (jti.isNullOrBlank()) {
            return false
        }
        // add() returns false if the JTI was already present -> replay.
        return consumedJtis.add(jti)
    }
}
