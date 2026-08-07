package com.jabauth.diagnostic.benchmark

import android.util.Base64
import android.util.Log
import com.jabauth.jabcode.CoaProfile
import com.jabauth.jabcode.DecodeOptions
import com.jabauth.jabcode.EncodeOptions
import com.jabauth.jabcode.JABCodeDecoderImpl
import com.jabauth.jabcode.JABCodeEncoderImpl

/**
 * In-app, tap-to-run **string-carrier vs binary-carrier** A/B — the mobile half of the
 * carrier comparison whose server half is `E2eCarrierBenchmark` (jab-auth-framework JMH).
 *
 * <h2>Fairness contract</h2>
 * ONE canonical v2 COA blob (the [VerifyLatencyBenchmark.canonicalV2Coa] builder — real
 * SD-JWT VC, bare-SPKI TRUST_CHAIN, ABE_SEALED policy) is built once. The two arms differ
 * ONLY in wrapping:
 *  - **binary** — the raw v2 blob (production A2 transport; native byte mode).
 *  - **string** — base64url(blob) as UTF-8 bytes ([Base64.URL_SAFE]|[Base64.NO_PADDING]|
 *    [Base64.NO_WRAP] — byte-identical to the server's `Base64.getUrlEncoder().withoutPadding()`;
 *    the legacy A1 transport, which the codec packs in text mode).
 *
 * Android has no carrier layer — the native decoder returns raw bytes and
 * `PayloadFormatV2.isV2` sniffs the JAC2 magic — so the arm distinction lives entirely in
 * the payload bytes handed to the encoder, exactly as it would on a printed label.
 *
 * <h2>What is measured</h2>
 * Per [CoaProfile] × arm: `carrier_encode_*` ([JABCodeEncoderImpl.encode] to an in-memory
 * bitmap) and `carrier_decode_*` ([JABCodeDecoderImpl.decode] of that bitmap), warmup 5 /
 * measurement 15 (matching [CodecBenchmark]). Each cell sanity-decodes ONCE and requires
 * `contentEquals` with the encoded payload before any timing — a lossy cell is reported
 * `ok=false`, never measured. Rows carry payload bytes and bitmap W×H (symbol-version
 * step-functions dominate decode cost). No camera involved.
 *
 * Results: one `BENCHMARK_JSON` logcat line per cell (`"op":"carrier"`) under [TAG], plus
 * the "CARRIER A/B" card on the Capture Test screen.
 */
class CarrierComparisonBenchmark : BenchmarkSuite() {

    override val defaultWarmupIterations: Int = 5
    override val defaultMeasurementIterations: Int = 15

    private val encoder = JABCodeEncoderImpl()
    private val decoder = JABCodeDecoderImpl()

    enum class CarrierArm { BINARY, STRING }

    fun run(): CarrierComparisonReport {
        val blob = VerifyLatencyBenchmark.canonicalV2Coa(VerifyLatencyBenchmark.ecKeyPair())
        val arms = mapOf(
            CarrierArm.BINARY to blob,
            CarrierArm.STRING to Base64.encode(blob, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP),
        )

        val rows = mutableListOf<CarrierComparisonRow>()
        for (profile in CoaProfile.entries) {
            for ((arm, payload) in arms) {
                rows.add(measureCell(profile, arm, payload, blob.size))
            }
        }
        val report = CarrierComparisonReport(blobBytes = blob.size, rows = rows)
        rows.forEach { Log.i(TAG, "BENCHMARK_JSON ${rowJson(it)}") }
        return report
    }

    private fun measureCell(
        profile: CoaProfile,
        arm: CarrierArm,
        payload: ByteArray,
        blobBytes: Int,
    ): CarrierComparisonRow {
        val cell = "${profile.name.lowercase()}_${arm.name.lowercase()}"
        return try {
            val opts = EncodeOptions(colorMode = profile.colorMode, errorCorrectionLevel = profile.eccLevel)
            val bitmap = encoder.encode(payload, opts)

            // Fairness guard: the cell must round-trip byte-identically before timing.
            val sanity = decoder.decode(bitmap, DecodeOptions(timeout = DECODE_TIMEOUT_MS))
            check(sanity != null && sanity.data.contentEquals(payload)) {
                "sanity round-trip diverged (payload ${payload.size}B, got ${sanity?.data?.size ?: "null"})"
            }

            val encode = runBenchmark("carrier_encode_$cell") { encoder.encode(payload, opts) }
            val decode = runBenchmark("carrier_decode_$cell") {
                decoder.decode(bitmap, DecodeOptions(timeout = DECODE_TIMEOUT_MS))
            }
            CarrierComparisonRow(
                profile = profile, arm = arm, ok = true,
                blobBytes = blobBytes, payloadBytes = payload.size,
                bitmapWidth = bitmap.width, bitmapHeight = bitmap.height,
                encodeMedianMs = encode.medianTimeMs, decodeMedianMs = decode.medianTimeMs,
                error = null,
            )
        } catch (t: Throwable) {
            Log.w(TAG, "carrier cell $cell failed: ${t.message}")
            CarrierComparisonRow(
                profile = profile, arm = arm, ok = false,
                blobBytes = blobBytes, payloadBytes = payload.size,
                bitmapWidth = null, bitmapHeight = null,
                encodeMedianMs = null, decodeMedianMs = null,
                error = t.message ?: t.javaClass.simpleName,
            )
        }
    }

    private fun rowJson(r: CarrierComparisonRow): String = buildString {
        append('{')
        append("\"op\":\"carrier\",")
        append("\"profile\":\"").append(r.profile.name).append("\",")
        append("\"colors\":").append(r.profile.colorMode.value).append(',')
        append("\"ecc\":").append(r.profile.eccLevel).append(',')
        append("\"arm\":\"").append(r.arm.name.lowercase()).append("\",")
        append("\"ok\":").append(r.ok).append(',')
        append("\"blob_bytes\":").append(r.blobBytes).append(',')
        append("\"payload_bytes\":").append(r.payloadBytes).append(',')
        append("\"img_w\":").append(r.bitmapWidth ?: "null").append(',')
        append("\"img_h\":").append(r.bitmapHeight ?: "null").append(',')
        append("\"encode_ms\":").append(r.encodeMedianMs?.let { "%.2f".format(it) } ?: "null").append(',')
        append("\"decode_ms\":").append(r.decodeMedianMs?.let { "%.2f".format(it) } ?: "null")
        r.error?.let { append(",\"error\":\"").append(it.replace('"', '\'')).append('"') }
        append('}')
    }

    companion object {
        const val TAG = "CarrierComparisonBenchmark"
        private const val DECODE_TIMEOUT_MS = 2000L
    }
}

/** One (profile, arm) cell of the carrier A/B: sizes, symbol dimensions, and medians. */
data class CarrierComparisonRow(
    val profile: CoaProfile,
    val arm: CarrierComparisonBenchmark.CarrierArm,
    val ok: Boolean,
    val blobBytes: Int,
    val payloadBytes: Int,
    val bitmapWidth: Int?,
    val bitmapHeight: Int?,
    val encodeMedianMs: Double?,
    val decodeMedianMs: Double?,
    val error: String?,
)

/** The full carrier A/B report: the canonical blob size plus one row per profile × arm. */
data class CarrierComparisonReport(
    val blobBytes: Int,
    val rows: List<CarrierComparisonRow>,
)
