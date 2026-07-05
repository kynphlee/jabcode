package com.jabauth.diagnostic.benchmark

import com.google.common.truth.Truth.assertThat
import com.jabauth.jabcode.ColorMode
import com.jabauth.jabcode.CoaProfile
import org.junit.Test

/**
 * Pure-JVM tests for the verify-latency-by-profile benchmark's testable core: the Σ row model, the
 * ColorMode→Nc mapping and profile→decode wiring, and the real (measured, not hardcoded) crypto-stage
 * timing over an in-code canonical COA. No Android, no camera, no fixtures.
 */
class VerifyLatencyBenchmarkTest {

    // ── Σ (row total) model ──────────────────────────────────────────────────────

    @Test fun `Sigma is the sum of decode plus the three crypto stages`() {
        val row = VerifyLatencyRow(CoaProfile.FIELD, decodeMs = 63.0, pkiMs = 41.0, jwtMs = 18.0, abeMs = 88.0)
        assertThat(row.totalMs).isEqualTo(210.0)
    }

    @Test fun `Sigma is null when the decode cell is unavailable`() {
        val row = VerifyLatencyRow(CoaProfile.FIELD, decodeMs = null, pkiMs = 41.0, jwtMs = 18.0, abeMs = 88.0)
        assertThat(row.totalMs).isNull()
    }

    // ── ColorMode → Nc mapping (the codec-benchmark row key) ─────────────────────

    @Test fun `ncOf maps each colour mode to its Nc index`() {
        assertThat(VerifyLatencyBenchmark.ncOf(ColorMode.COLOR_2)).isEqualTo(0)
        assertThat(VerifyLatencyBenchmark.ncOf(ColorMode.COLOR_4)).isEqualTo(1)
        assertThat(VerifyLatencyBenchmark.ncOf(ColorMode.COLOR_8)).isEqualTo(2)
        assertThat(VerifyLatencyBenchmark.ncOf(ColorMode.COLOR_16)).isEqualTo(3)
        assertThat(VerifyLatencyBenchmark.ncOf(ColorMode.COLOR_64)).isEqualTo(5)
        assertThat(VerifyLatencyBenchmark.ncOf(ColorMode.COLOR_256)).isEqualTo(7)
    }

    @Test fun `each profile maps to the Nc of its colour mode`() {
        // The documented profile→colour pins: FIELD=16c(Nc3), FIELD_HOSTILE=4c(Nc1), CONTROLLED=64c(Nc5), SERVER=256c(Nc7).
        assertThat(VerifyLatencyBenchmark.ncOf(CoaProfile.FIELD.colorMode)).isEqualTo(3)
        assertThat(VerifyLatencyBenchmark.ncOf(CoaProfile.FIELD_HOSTILE.colorMode)).isEqualTo(1)
        assertThat(VerifyLatencyBenchmark.ncOf(CoaProfile.CONTROLLED.colorMode)).isEqualTo(5)
        assertThat(VerifyLatencyBenchmark.ncOf(CoaProfile.SERVER.colorMode)).isEqualTo(7)
    }

    // ── decode-median wiring from codec-benchmark results ────────────────────────

    @Test fun `decodeMediansByNc indexes decode cells by Nc and ignores encode cells`() {
        val results = listOf(
            benchmarkResult("decode_nc1", 100.0),
            benchmarkResult("decode_nc3", 63.0),
            benchmarkResult("encode_nc3", 5.0), // must be ignored
        )
        val byNc = VerifyLatencyBenchmark.decodeMediansByNc(results)
        assertThat(byNc[1]).isEqualTo(100.0)
        assertThat(byNc[3]).isEqualTo(63.0)
        assertThat(byNc).doesNotContainKey(5)
    }

    // ── report shape + honesty (missing decode → null, never fabricated) ─────────

    @Test fun `run produces one row per profile in enum order`() {
        val report = VerifyLatencyBenchmark().run(codecResults = emptyList())
        assertThat(report.rows.map { it.profile }).containsExactlyElementsIn(CoaProfile.entries).inOrder()
    }

    @Test fun `a profile with no codec decode row renders a null decode and null Sigma`() {
        // Empty codec results ⇒ no decode data for any profile ⇒ decode + Σ null (honest), crypto still present.
        val report = VerifyLatencyBenchmark().run(codecResults = emptyList())
        val field = report.rows.first { it.profile == CoaProfile.FIELD }
        assertThat(field.decodeMs).isNull()
        assertThat(field.totalMs).isNull()
        assertThat(field.pkiMs).isAtLeast(0.0) // crypto is always measured
    }

    @Test fun `run attaches the codec decode median to each matching profile and computes Sigma`() {
        // Give FIELD (16c → Nc3) a decode row; its Σ = decode + the measured crypto medians.
        val codec = listOf(benchmarkResult("decode_nc3", 63.0))
        val report = VerifyLatencyBenchmark().run(codecResults = codec)
        val field = report.rows.first { it.profile == CoaProfile.FIELD }
        assertThat(field.decodeMs).isEqualTo(63.0)
        assertThat(field.totalMs).isEqualTo(63.0 + field.pkiMs + field.jwtMs + field.abeMs)
        // A profile with no matching codec row stays honest.
        assertThat(report.rows.first { it.profile == CoaProfile.SERVER }.decodeMs).isNull()
    }

    // ── the crypto columns are REAL measurements over a real canonical COA ───────

    @Test fun `crypto stage latencies are measured (finite, non-negative) not hardcoded`() {
        val crypto = VerifyLatencyBenchmark().measureCryptoStages()
        // Real timings: finite, >= 0. (We deliberately do NOT assert the mock's example ms — those are not real.)
        assertThat(crypto.pkiMs).isAtLeast(0.0)
        assertThat(crypto.jwtMs).isAtLeast(0.0)
        assertThat(crypto.abeMs).isAtLeast(0.0)
        assertThat(crypto.pkiMs.isFinite()).isTrue()
        assertThat(crypto.jwtMs.isFinite()).isTrue()
        assertThat(crypto.abeMs.isFinite()).isTrue()
    }

    @Test fun `every profile row carries the same measured crypto medians (profile-independent crypto)`() {
        val report = VerifyLatencyBenchmark().run(codecResults = emptyList())
        val pki = report.rows.map { it.pkiMs }.toSet()
        val jwt = report.rows.map { it.jwtMs }.toSet()
        val abe = report.rows.map { it.abeMs }.toSet()
        // One measurement, repeated across profiles — so exactly one distinct value per crypto column.
        assertThat(pki).hasSize(1)
        assertThat(jwt).hasSize(1)
        assertThat(abe).hasSize(1)
        assertThat(report.crypto.pkiMs).isEqualTo(pki.first())
    }

    private fun benchmarkResult(name: String, median: Double) = BenchmarkResult(
        benchmarkName = name,
        warmupIterations = 1,
        measurementIterations = 1,
        meanTimeMs = median,
        medianTimeMs = median,
        minTimeMs = median,
        maxTimeMs = median,
        stdDevMs = 0.0,
    )
}
