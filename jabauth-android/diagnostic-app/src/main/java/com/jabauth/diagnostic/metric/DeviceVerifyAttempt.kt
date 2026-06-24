package com.jabauth.diagnostic.metric

/**
 * Stage 1b — the device leg of the shared, cross-leg COA verify-attempt metric record.
 *
 * <h2>One schema, mirrored on two legs</h2>
 * This is the Kotlin mirror of the host-side {@code org.nexus.jabauth.coa.metric.VerifyAttempt}
 * (a Jackson-serialized Java record in jab-auth-spring-integration). The host emits this record from
 * its authoritative verify sweep and from the JMH {@code e2e} suite; the device emits the same record
 * from its codec/capture path. Because the JSON column names + shape match byte-for-byte on the shared
 * columns, a device run and a host sweep diff cleanly column-for-column.
 *
 * <h2>Device is codec/capture-only — by design</h2>
 * The device decodes and measures capture quality; it does NOT perform the authoritative crypto verify
 * (PKI chain / JWT signature+replay / ABE decapsulate) — that is host-side. So on the device leg:
 *  - [colorNumber]/[version]/[ecc]/[imagePx]/[quality]/[outcome] are populated from the decode + frame.
 *  - [failAttr] uses only the decode subset of the shared vocabulary: [FailAttr.NONE], [FailAttr.DETECT],
 *    [FailAttr.CLASSIFICATION].
 *  - [StageTimingsMs.decode] carries the decode wall-clock; [StageTimingsMs.pki]/[jwt]/[abe] are 0.
 *  - [authOutcome] is always all-[StageOutcome.SKIP] — the device ran none of the crypto stages.
 *
 * <h2>Wire format — pinned to the host record</h2>
 * The host record is `@JsonInclude(NON_NULL)` and Jackson serializes its enums by `.name()` (UPPERCASE),
 * its record components in declaration order. This model reproduces that exactly via [toJson]:
 * key order `colorNumber, version, ecc, imagePx, quality?, outcome, failAttr, stageTimingsMs, authOutcome`;
 * a null [quality] block is omitted (NON_NULL); enum values emit as their UPPERCASE names. The repo's
 * diagnostic-app has no JSON dependency (see CodecBenchmark, which also hand-rolls JSON), so this stays
 * dependency-free and matches that convention rather than adding kotlinx.serialization / Moshi / Gson.
 *
 * <h2>Telemetry only — never a credential</h2>
 * Like the host record, this carries only measurements and small categorical outcomes. It MUST NOT
 * contain any decoded payload, key, certificate, JWT, plaintext, or ABE secret. Every field here is a
 * timing, a quality score, a carrier shape number, or an outcome enum.
 *
 * @property colorNumber    carrier colour count (2, 4, 8, 16, 32, 64, 128, 256)
 * @property version        JABCode symbol version, or 0 if unknown/not probed (the device decode path
 *                          does not surface symbol version)
 * @property ecc            error-correction level, or 0 if unknown/not probed (likewise not surfaced)
 * @property imagePx        carrier image edge length in pixels (square symbol assumed), or 0 if N/A
 * @property quality        capture-quality metrics; null when there is no live frame (e.g. the Suite-B
 *                          fixture-decode benchmark, or a picked-image decode)
 * @property outcome        overall PASS/FAIL of the decode attempt
 * @property failAttr       why it failed (or [FailAttr.NONE] on PASS) — decode subset only on this leg
 * @property stageTimingsMs per-stage wall-clock; decode populated, pki/jwt/abe = 0 on this leg
 * @property authOutcome    per-stage PKI/JWT/ABE outcome — always all-SKIP on this leg
 */
data class DeviceVerifyAttempt(
    val colorNumber: Int,
    val version: Int,
    val ecc: Int,
    val imagePx: Int,
    val quality: Quality?,
    val outcome: Outcome,
    val failAttr: FailAttr,
    val stageTimingsMs: StageTimingsMs,
    val authOutcome: AuthOutcome
) {

    /** Overall verdict for the attempt. Mirrors host `VerifyAttempt.Outcome`. */
    enum class Outcome { PASS, FAIL }

    /**
     * Per-stage verdict. `SKIP` means the stage never ran. On the device leg every auth stage is
     * SKIP. Mirrors host `VerifyAttempt.StageOutcome`.
     */
    enum class StageOutcome { PASS, FAIL, SKIP }

    /**
     * Failure attribution in the shared cross-leg vocabulary. The full host vocabulary spans decode
     * AND auth codes; the device leg can only ever emit the decode subset, since it never runs the
     * crypto stages. The auth codes are retained here so the enum stays a faithful mirror of the host
     * `VerifyAttempt.FailAttr` (and so a device record diffs cleanly against a host record that used
     * an auth code). The wire string is the UPPERCASE enum name (matching Jackson's default), e.g.
     * `"CLASSIFICATION"`. The lowercase [code] (e.g. `"classification"`) mirrors the host's `code()`
     * accessor for cross-referencing the C-side FAIL_ATTR codes; it is NOT the JSON wire value.
     */
    enum class FailAttr(val code: String) {
        // Decode subset — the only values the device leg emits.
        /** Success — no failure. */
        NONE("none"),
        /** Decode: the detector never found / locked the symbol (status=0 no FP found). */
        DETECT("detect"),
        /** Decode: symbol found but colour classification / ECC could not recover the payload. */
        CLASSIFICATION("classification"),

        // Auth subset — host-only; kept for a faithful cross-leg mirror, never emitted device-side.
        /** Auth (host-only): JWT replay — a single-use token presented a second time. */
        REPLAY("replay"),
        /** Auth (host-only): the JTI has been revoked / blacklisted. */
        REVOKED("revoked"),
        /** Auth (host-only): token or certificate is outside its validity window. */
        EXPIRED("expired"),
        /** Auth (host-only): PKI chain did not validate to a trust anchor. */
        UNTRUSTED("untrusted"),
        /** Auth (host-only): cryptographic signature verification failed. */
        BAD_SIG("bad_sig")
    }

    /**
     * Capture-quality metrics from the imaging frontend, sourced from
     * `ImageQualityAnalyzer.QualityMetrics`. Values are unitless 0..1-ish scores. Mirrors host
     * `VerifyAttempt.Quality`. The host stores these as `double`; the device's analyzer produces
     * `Float`, widened on construction.
     *
     * @property focus      focus / sharpness score
     * @property brightness mean-brightness score
     * @property contrast   contrast score
     */
    data class Quality(val focus: Double, val brightness: Double, val contrast: Double)

    /**
     * Per-stage wall-clock timings in milliseconds. On the device leg only [decode] is populated;
     * the crypto stages do not run here and are left at 0. Mirrors host `VerifyAttempt.StageTimingsMs`.
     *
     * @property decode JABCode decode wall-clock
     * @property pki    certificate chain validation — 0 on device (not run)
     * @property jwt    JWT verify — 0 on device (not run)
     * @property abe    ABE decapsulate — 0 on device (not run)
     */
    data class StageTimingsMs(
        val decode: Double,
        val pki: Double = 0.0,
        val jwt: Double = 0.0,
        val abe: Double = 0.0
    )

    /**
     * Per-stage authenticated-decision outcome. On the device leg this is always all-SKIP — the
     * device performs no authoritative auth verify. Mirrors host `VerifyAttempt.AuthOutcome`.
     */
    data class AuthOutcome(
        val pki: StageOutcome,
        val jwt: StageOutcome,
        val abe: StageOutcome
    ) {
        companion object {
            /** The device leg never runs the crypto stages, so every auth outcome is SKIP. */
            val ALL_SKIP = AuthOutcome(StageOutcome.SKIP, StageOutcome.SKIP, StageOutcome.SKIP)
        }
    }

    /**
     * Serialize to a single-line JSON object byte-compatible with the host record's
     * `ObjectMapper().writeValueAsString(verifyAttempt)` on the shared columns.
     *
     * Reproduces Jackson's defaults for the host record: record components in declaration order,
     * `@JsonInclude(NON_NULL)` so a null [quality] is omitted, and enums by UPPERCASE `.name()`.
     * Doubles are emitted with [jsonNumber] so integral values render as Jackson does (e.g. `0.0`,
     * `1.2`) without a locale-dependent or trailing-zero-padded form.
     */
    fun toJson(): String = buildString {
        append('{')
        append("\"colorNumber\":").append(colorNumber).append(',')
        append("\"version\":").append(version).append(',')
        append("\"ecc\":").append(ecc).append(',')
        append("\"imagePx\":").append(imagePx).append(',')
        // NON_NULL: emit the quality block only when present (host-side null is omitted).
        quality?.let {
            append("\"quality\":{")
            append("\"focus\":").append(jsonNumber(it.focus)).append(',')
            append("\"brightness\":").append(jsonNumber(it.brightness)).append(',')
            append("\"contrast\":").append(jsonNumber(it.contrast))
            append("},")
        }
        append("\"outcome\":\"").append(outcome.name).append("\",")
        append("\"failAttr\":\"").append(failAttr.name).append("\",")
        append("\"stageTimingsMs\":{")
        append("\"decode\":").append(jsonNumber(stageTimingsMs.decode)).append(',')
        append("\"pki\":").append(jsonNumber(stageTimingsMs.pki)).append(',')
        append("\"jwt\":").append(jsonNumber(stageTimingsMs.jwt)).append(',')
        append("\"abe\":").append(jsonNumber(stageTimingsMs.abe))
        append("},")
        append("\"authOutcome\":{")
        append("\"pki\":\"").append(authOutcome.pki.name).append("\",")
        append("\"jwt\":\"").append(authOutcome.jwt.name).append("\",")
        append("\"abe\":\"").append(authOutcome.abe.name).append('"')
        append('}')
        append('}')
    }

    companion object {
        /** CSV header for the flattened columns emitted by [toCsvRow], in declaration order. */
        const val CSV_HEADER =
            "colorNumber,version,ecc,imagePx,focus,brightness,contrast,outcome,failAttr," +
                "decodeMs,pkiMs,jwtMs,abeMs,authPki,authJwt,authAbe"

        /**
         * Render a Double the way Jackson renders a `double`: an integral value keeps a single
         * trailing `.0` (e.g. `0.0`), everything else uses the shortest round-tripping decimal that
         * `Double.toString` already provides. Kotlin's `Double.toString` matches Java's, which is
         * what Jackson uses under the hood, so the shared timing/quality columns stay byte-identical.
         */
        internal fun jsonNumber(d: Double): String = d.toString()
    }

    /**
     * Flatten to a CSV row matching [CSV_HEADER]. The nested quality / timing / auth blocks are
     * flattened into scalar columns; an absent [quality] leaves its three columns empty. This is the
     * spreadsheet-friendly companion to [toJson]; the JSONL output is the byte-exact host mirror.
     */
    fun toCsvRow(): String {
        val q = quality
        return listOf(
            colorNumber.toString(),
            version.toString(),
            ecc.toString(),
            imagePx.toString(),
            q?.let { jsonNumber(it.focus) } ?: "",
            q?.let { jsonNumber(it.brightness) } ?: "",
            q?.let { jsonNumber(it.contrast) } ?: "",
            outcome.name,
            failAttr.name,
            jsonNumber(stageTimingsMs.decode),
            jsonNumber(stageTimingsMs.pki),
            jsonNumber(stageTimingsMs.jwt),
            jsonNumber(stageTimingsMs.abe),
            authOutcome.pki.name,
            authOutcome.jwt.name,
            authOutcome.abe.name
        ).joinToString(",")
    }
}
