package com.jabauth.diagnostic.benchmark

import android.util.Log
import com.jabauth.client.jwt.SdJwtVcRequest
import com.jabauth.client.jwt.SdJwtVcService
import com.jabauth.client.pki.CertificateChainValidatorImpl
import com.jabauth.client.pki.TrustStoreManagerImpl
import com.jabauth.client.v2.PayloadFormatV2
import com.jabauth.client.v2.PayloadFormatV2.Section
import com.jabauth.client.v2.PayloadFormatV2.SectionType
import com.jabauth.diagnostic.verify.AbeStageRunner
import com.jabauth.diagnostic.verify.DecodedSymbol
import com.jabauth.diagnostic.verify.FormatProfile
import com.jabauth.diagnostic.verify.JwtStageRunner
import com.jabauth.diagnostic.verify.PkiStageRunner
import com.jabauth.diagnostic.verify.ScanVerifier
import com.jabauth.diagnostic.verify.StageState
import com.jabauth.jabcode.ColorMode
import com.jabauth.jabcode.CoaProfile
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec
import java.time.Duration

/**
 * In-app, tap-to-run **verify-latency-by-profile** benchmark — the "VERIFY LATENCY BY PROFILE · MS" table
 * on the Capture Test screen, sitting above the [CodecBenchmark] "CODEC BENCHMARK" table.
 *
 * <h2>What is really measured vs derived — the honesty contract</h2>
 * The on-device pre-check pipeline is `DECODE → PKI → JWT → ABE` ([ScanVerifier]). Only the **DECODE** cost
 * varies by [CoaProfile] — a profile is just a colour/ECC choice, so a denser symbol (more colours) decodes
 * faster while carrying the *same* sealed credential. The three crypto stages run the *same* SD-JWT / trust /
 * CP-ABE work regardless of how the COA was encoded, so they are **profile-independent**.
 *
 * Accordingly this benchmark:
 *  - measures the crypto columns (**pki / jwt / abe**) **once**, by running the real [ScanVerifier] pipeline
 *    over a canonical v2 COA built in-code (a real SD-JWT VC signed with a freshly-generated EC key, whose
 *    public key rides in the `TRUST_CHAIN` section, plus a real `ABE_SEALED` policy the verifier satisfies).
 *    Each stage is timed individually via the [BenchmarkSuite] warmup+measure loop. These medians are then
 *    repeated across every profile row, because they genuinely do not change per profile.
 *  - sources the **dec** column per profile from the already-run [CodecBenchmark] decode results, mapping each
 *    profile's [CoaProfile.colorMode] to the codec benchmark's `decode_ncN` row. A profile whose decode row is
 *    absent (fixture missing / decode failed) is rendered with a null decode (and therefore a null Σ) — never
 *    a fabricated number.
 *
 * `Σ` is the row sum (`dec + pki + jwt + abe`), or null when the decode cell is null.
 *
 * <h2>PKI posture</h2>
 * The `pki` column reflects what the **device actually does today**: the offline interim path (no network for
 * OCSP/CRL, so revocation is `UNKNOWN·offline` → `WARN`), exactly as [ScanVerifier]'s default orchestrator
 * runs it. Full path-validation-to-a-trusted-anchor with revocation is server-authoritative (Principle F).
 * Whatever that real on-device PKI stage costs is what this measures — it is not modelled or padded.
 *
 * The crypto work runs off the camera hot path (invoked from a coroutine on a background dispatcher, exactly
 * like [CodecBenchmark]). Pure JDK crypto — no camera, no native, no BouncyCastle.
 */
class VerifyLatencyBenchmark : BenchmarkSuite() {

    /**
     * Build the full report: measure the profile-independent crypto stage latencies once, then attach the
     * per-profile decode latency pulled from [codecResults] (the [CodecBenchmark] output).
     *
     * @param codecResults the results returned by [CodecBenchmark.run]; the `decode_ncN` rows supply the
     *   per-profile decode column. Pass an empty list to render crypto-only rows (decode + Σ null).
     * @return a [VerifyLatencyReport] with one row per [CoaProfile], in enum order.
     */
    fun run(codecResults: List<BenchmarkResult>): VerifyLatencyReport {
        val crypto = measureCryptoStages()
        val decodeByNc = decodeMediansByNc(codecResults)
        val rows = CoaProfile.entries.map { profile ->
            val nc = ncOf(profile.colorMode)
            VerifyLatencyRow(
                profile = profile,
                decodeMs = decodeByNc[nc],
                pkiMs = crypto.pkiMs,
                jwtMs = crypto.jwtMs,
                abeMs = crypto.abeMs,
            )
        }
        val report = VerifyLatencyReport(rows = rows, crypto = crypto)
        logJson(report)
        return report
    }

    /**
     * Time each real crypto stage over the canonical COA, using the [BenchmarkSuite] warmup+measure loop.
     * Medians only — the stages are pure/low-variance, and the table shows one number per cell.
     *
     * The three [com.jabauth.diagnostic.verify.VerificationOrchestrator.StageRunner]s are constructed with
     * the *same* extraction seams [ScanVerifier]'s default orchestrator wires — a real
     * [CertificateChainValidatorImpl]+[TrustStoreManagerImpl] behind PKI, the real [SdJwtVcService] behind
     * JWT, and the real ABE policy adjudicator — so `run(symbol)` executes the identical on-device work.
     * Each column is thus an isolated, real measurement of that stage against the canonical symbol.
     */
    fun measureCryptoStages(): CryptoStageLatency {
        val issuerKeys = ecKeyPair()
        val symbol = DecodedSymbol(
            payload = canonicalV2Coa(issuerKeys),
            isCoaFormat = true,
            decodeLatencyMs = 0L,
            formatProfile = FormatProfile("v2", CoaProfile.defaultProfile().name),
        )

        val trustStore = TrustStoreManagerImpl()
        // Exactly ScanVerifier.defaultOrchestrator's seams — the bare-SPKI TRUST_CHAIN yields no leaf cert, so
        // extractChain is null and PKI runs its real offline interim path (WARN / UNKNOWN·offline).
        val pki = PkiStageRunner(
            extractChain = { s -> ScanVerifier.v2Section(s.payload, SectionType.TRUST_CHAIN)?.let { ScanVerifier.parseLeafCertificate(it) }?.let { listOf(it) } },
            validator = CertificateChainValidatorImpl(trustStore),
            trustStore = trustStore,
        )
        val jwt = JwtStageRunner(
            extractToken = { s -> ScanVerifier.v2Section(s.payload, SectionType.SDJWT_VC)?.toString(Charsets.UTF_8) },
            resolveIssuerKey = { s -> ScanVerifier.v2Section(s.payload, SectionType.TRUST_CHAIN)?.let { ScanVerifier.resolveIssuerKey(it) } },
        )
        val abe = AbeStageRunner(
            extractPolicy = { s -> ScanVerifier.extractPolicy(s.payload) },
            verifierAttributes = { VERIFIER_ATTRS },
        )

        // Sanity: the canonical COA must exercise the crypto stages for real (JWT signature PASS, ABE grant).
        // A hollow run (no real verify) would make the numbers meaningless — fail loudly instead.
        require(jwt.run(symbol).state == StageState.PASS) {
            "canonical COA failed to produce a real JWT PASS — the SD-JWT verify is not exercising."
        }
        require(abe.run(symbol).state == StageState.PASS) {
            "canonical COA failed to produce a real ABE grant — the policy adjudication is not exercising."
        }

        val pkiMs = runBenchmark("verify_pki") { pki.run(symbol) }.medianTimeMs
        val jwtMs = runBenchmark("verify_jwt") { jwt.run(symbol) }.medianTimeMs
        val abeMs = runBenchmark("verify_abe") { abe.run(symbol) }.medianTimeMs
        return CryptoStageLatency(pkiMs = pkiMs, jwtMs = jwtMs, abeMs = abeMs)
    }

    /** A real v2 COA: SD-JWT VC (signed with [issuerKeys]), the issuer public key as a bare-SPKI TRUST_CHAIN,
     *  and an ABE_SEALED policy [VERIFIER_ATTRS] satisfies. Pure JDK — the bare SPKI is the legacy TRUST_CHAIN
     *  the verifier resolves the issuer key from (no BouncyCastle certificate needed on the app classpath). */
    private fun canonicalV2Coa(issuerKeys: KeyPair): ByteArray {
        val service = SdJwtVcService()
        val credential = SdJwtVcRequest.builder()
            .issuer("https://issuer.jabauth.example")
            .subject("coa-benchmark")
            .vct("coa")
            .expiration(Duration.ofHours(1))
            .algorithm("ES256")
            .alwaysVisible(mapOf("origin" to "ai-generated"))
            .selectivelyDisclosable(mapOf("licenseTerms" to "CC-BY-4.0"))
            .build()
        val sections = listOf(
            Section.of(SectionType.SDJWT_VC, service.issue(credential, issuerKeys.private).toByteArray()),
            Section.of(SectionType.TRUST_CHAIN, issuerKeys.public.encoded),
            Section.of(SectionType.ABE_SEALED, abeEnvelope(ABE_POLICY)),
            Section.of(SectionType.META, "{\"vct\":\"coa\"}".toByteArray()),
        )
        return PayloadFormatV2.encode(sections)
    }

    private fun ecKeyPair(): KeyPair =
        KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()

    /** An ABE1 envelope carrying [policy] cleartext (mirrors the server encoder; ct/policyData are demo bytes). */
    private fun abeEnvelope(policy: String): ByteArray {
        val p = policy.toByteArray(StandardCharsets.UTF_8)
        val ct = byteArrayOf(1, 2, 3, 4)
        val pd = byteArrayOf(5, 6)
        return ByteBuffer.allocate(14 + p.size + ct.size + pd.size).apply {
            put('A'.code.toByte()); put('B'.code.toByte()); put('E'.code.toByte()); put('1'.code.toByte())
            put(0x01); put(0x01); putShort(p.size.toShort()); putInt(ct.size); putShort(pd.size.toShort())
            put(p); put(ct); put(pd)
        }.array()
    }

    private fun logJson(report: VerifyLatencyReport) {
        for (row in report.rows) {
            Log.i(TAG, "BENCHMARK_JSON ${rowJson(row)}")
        }
    }

    private fun rowJson(row: VerifyLatencyRow): String = buildString {
        append('{')
        append("\"op\":\"verify\",")
        append("\"profile\":\"").append(row.profile.name).append("\",")
        append("\"colors\":").append(row.profile.colorMode.value).append(',')
        append("\"dec_ms\":").append(numOrNull(row.decodeMs)).append(',')
        append("\"pki_ms\":").append(fmt(row.pkiMs)).append(',')
        append("\"jwt_ms\":").append(fmt(row.jwtMs)).append(',')
        append("\"abe_ms\":").append(fmt(row.abeMs)).append(',')
        append("\"sum_ms\":").append(numOrNull(row.totalMs))
        append('}')
    }

    private fun numOrNull(v: Double?): String = if (v == null) "null" else fmt(v)
    private fun fmt(v: Double): String = "%.2f".format(v)

    companion object {
        const val TAG = "VerifyLatencyBenchmark"

        /** Access policy sealed into the canonical COA; [VERIFIER_ATTRS] satisfies it so ABE lands PASS (granted). */
        private const val ABE_POLICY = "role:inspector AND region:EU"
        private val VERIFIER_ATTRS = setOf("role:inspector", "region:EU")

        /**
         * The colour-mode "Nc" index (0..7) for a [ColorMode], i.e. `log2(value) - 1`:
         * COLOR_2→0, COLOR_4→1, COLOR_8→2, COLOR_16→3, … COLOR_256→7. Matches [CodecBenchmark]'s
         * `decode_ncN` naming (where `colorCount = 1 shl (nc + 1)`).
         */
        fun ncOf(mode: ColorMode): Int = Integer.numberOfTrailingZeros(mode.value) - 1

        /**
         * Index [codecResults] by their colour-mode Nc, taking the median decode ms of each `decode_ncN` cell.
         * Non-decode cells (`encode_ncN`) are ignored. Malformed names are skipped.
         */
        fun decodeMediansByNc(codecResults: List<BenchmarkResult>): Map<Int, Double> =
            codecResults.mapNotNull { r ->
                val nc = decodeNc(r.benchmarkName) ?: return@mapNotNull null
                nc to r.medianTimeMs
            }.toMap()

        /** The Nc of a `decode_ncN` benchmark name, or null for any other name. */
        private fun decodeNc(benchmarkName: String): Int? {
            if (!benchmarkName.startsWith("decode_nc")) return null
            return benchmarkName.removePrefix("decode_nc").toIntOrNull()
        }
    }
}

/**
 * One row of the "VERIFY LATENCY BY PROFILE · MS" table: the per-stage medians for a [CoaProfile].
 *
 * [decodeMs] is null when the codec benchmark has no decode measurement for this profile's colour mode (a
 * missing/failed fixture) — the table renders "—" and the Σ is likewise null. The crypto columns are always
 * present (the crypto stages always run in the benchmark).
 */
data class VerifyLatencyRow(
    val profile: CoaProfile,
    val decodeMs: Double?,
    val pkiMs: Double,
    val jwtMs: Double,
    val abeMs: Double,
) {
    /** Σ — the end-to-end median: `dec + pki + jwt + abe`, or null when [decodeMs] is unavailable. */
    val totalMs: Double? get() = decodeMs?.let { it + pkiMs + jwtMs + abeMs }
}

/** The profile-independent crypto-stage medians (ms), measured once over the canonical COA. */
data class CryptoStageLatency(
    val pkiMs: Double,
    val jwtMs: Double,
    val abeMs: Double,
)

/**
 * The full verify-latency-by-profile report: one [VerifyLatencyRow] per [CoaProfile] plus the shared
 * [crypto] medians they were built from.
 */
data class VerifyLatencyReport(
    val rows: List<VerifyLatencyRow>,
    val crypto: CryptoStageLatency,
)
