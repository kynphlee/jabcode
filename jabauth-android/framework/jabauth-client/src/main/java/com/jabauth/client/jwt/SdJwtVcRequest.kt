package com.jabauth.client.jwt

import java.time.Duration

/**
 * Request to issue an SD-JWT VC (SD-JWT-based Verifiable Credential, RFC 9901 +
 * draft-ietf-oauth-sd-jwt-vc). Mirrors the server `SdJwtVcRequest`.
 *
 * [alwaysVisible] claims are signed in the clear (every verifier sees them).
 * [selectivelyDisclosable] claims are replaced by salted-hash digests in the
 * `_sd` array — the issuer ships their Disclosures, and a holder later chooses
 * which to present. [vct] is the verifiable-credential type. [algorithm]
 * defaults to ES256, the SD-JWT VC / eIDAS2 baseline.
 */
data class SdJwtVcRequest(
    val issuer: String?,
    val subject: String?,
    val vct: String? = null,
    val expiration: Duration = Duration.ofMinutes(15),
    val alwaysVisible: Map<String, Any> = emptyMap(),
    val selectivelyDisclosable: Map<String, Any> = emptyMap(),
    val algorithm: String = JwtAlgorithms.DEFAULT_ALGORITHM,
) {
    companion object {
        @JvmStatic
        fun builder(): Builder = Builder()
    }

    class Builder {
        private var issuer: String? = null
        private var subject: String? = null
        private var vct: String? = null
        private var expiration: Duration = Duration.ofMinutes(15)
        private var alwaysVisible: Map<String, Any> = emptyMap()
        private var selectivelyDisclosable: Map<String, Any> = emptyMap()
        private var algorithm: String = JwtAlgorithms.DEFAULT_ALGORITHM

        fun issuer(v: String?) = apply { this.issuer = v }
        fun subject(v: String?) = apply { this.subject = v }
        fun vct(v: String?) = apply { this.vct = v }
        fun expiration(v: Duration) = apply { this.expiration = v }
        fun alwaysVisible(v: Map<String, Any>) = apply { this.alwaysVisible = v }
        fun selectivelyDisclosable(v: Map<String, Any>) = apply { this.selectivelyDisclosable = v }
        fun algorithm(v: String) = apply { this.algorithm = v }

        fun build(): SdJwtVcRequest = SdJwtVcRequest(
            issuer, subject, vct, expiration, alwaysVisible, selectivelyDisclosable, algorithm
        )
    }
}
