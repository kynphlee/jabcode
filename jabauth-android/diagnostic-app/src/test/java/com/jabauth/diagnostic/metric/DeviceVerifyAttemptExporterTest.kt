package com.jabauth.diagnostic.metric

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Tests for [DeviceVerifyAttemptExporter] — well-formed JSONL + CSV output to a plain directory, and
 * the mappings that promote the existing telemetry (live-scan attempts and Suite-B decode cells) into
 * the shared schema. Pure-JVM: a [TemporaryFolder] stands in for the on-device export directory, so no
 * Android `Context` / instrumentation / NDK is needed.
 */
class DeviceVerifyAttemptExporterTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun exporterIn(dir: File) = DeviceVerifyAttemptExporter(dir)

    @Test
    fun append_writesJsonlLine_andCsvWithHeaderOnce() {
        val dir = tmp.newFolder("export")
        val exporter = exporterIn(dir)

        val a = DeviceVerifyAttemptExporter.fromScanAttempt(
            isSuccess = true,
            decodeTimeMs = 4,
            colorNumber = 8,
            failAttr = DeviceVerifyAttempt.FailAttr.NONE,
            focus = 0.9f, brightness = 0.6f, contrast = 0.7f
        )
        val b = DeviceVerifyAttemptExporter.fromScanAttempt(
            isSuccess = false,
            decodeTimeMs = 2,
            colorNumber = 8,
            failAttr = DeviceVerifyAttempt.FailAttr.DETECT,
            focus = null, brightness = null, contrast = null
        )
        exporter.append(a)
        exporter.append(b)

        val jsonl = File(dir, DeviceVerifyAttemptExporter.JSONL_FILE).readLines()
        // One JSONL line per record, each byte-equal to the record's own toJson().
        assertThat(jsonl).hasSize(2)
        assertThat(jsonl[0]).isEqualTo(a.toJson())
        assertThat(jsonl[1]).isEqualTo(b.toJson())

        val csv = File(dir, DeviceVerifyAttemptExporter.CSV_FILE).readLines()
        // Header written exactly once, then one row per record.
        assertThat(csv).hasSize(3)
        assertThat(csv[0]).isEqualTo(DeviceVerifyAttempt.CSV_HEADER)
        assertThat(csv[1]).isEqualTo(a.toCsvRow())
        assertThat(csv[2]).isEqualTo(b.toCsvRow())
    }

    @Test
    fun appendAll_writesAllRows_inOrder() {
        val dir = tmp.newFolder("export")
        val exporter = exporterIn(dir)
        val records = listOf(
            DeviceVerifyAttemptExporter.fromSuiteBDecode(colorNumber = 2, medianDecodeMs = 1.5),
            DeviceVerifyAttemptExporter.fromSuiteBDecode(colorNumber = 8, medianDecodeMs = 3.25),
            DeviceVerifyAttemptExporter.fromSuiteBDecode(colorNumber = 256, medianDecodeMs = 12.0)
        )
        exporter.appendAll(records)

        val jsonl = File(dir, DeviceVerifyAttemptExporter.JSONL_FILE).readLines()
        assertThat(jsonl).hasSize(3)
        assertThat(jsonl.map { it }).containsExactly(
            records[0].toJson(), records[1].toJson(), records[2].toJson()
        ).inOrder()
    }

    @Test
    fun append_createsDir_ifMissing() {
        // Point at a not-yet-existent subdir; append must create it (the on-device first-write case).
        val dir = File(tmp.root, "nested/verify-attempts")
        assertThat(dir.exists()).isFalse()
        val exporter = exporterIn(dir)
        exporter.append(
            DeviceVerifyAttemptExporter.fromSuiteBDecode(colorNumber = 16, medianDecodeMs = 2.0)
        )
        assertThat(File(dir, DeviceVerifyAttemptExporter.JSONL_FILE).exists()).isTrue()
    }

    @Test
    fun fromScanAttempt_success_mapsToPass_none_decodeOnly_skipAuth() {
        val r = DeviceVerifyAttemptExporter.fromScanAttempt(
            isSuccess = true,
            decodeTimeMs = 7,
            colorNumber = 16,
            failAttr = DeviceVerifyAttempt.FailAttr.NONE,
            focus = 0.8f, brightness = 0.5f, contrast = 0.6f
        )
        assertThat(r.outcome).isEqualTo(DeviceVerifyAttempt.Outcome.PASS)
        assertThat(r.failAttr).isEqualTo(DeviceVerifyAttempt.FailAttr.NONE)
        assertThat(r.colorNumber).isEqualTo(16)
        // Decode-only timing; crypto stages zero.
        assertThat(r.stageTimingsMs.decode).isEqualTo(7.0)
        assertThat(r.stageTimingsMs.pki).isEqualTo(0.0)
        assertThat(r.stageTimingsMs.jwt).isEqualTo(0.0)
        assertThat(r.stageTimingsMs.abe).isEqualTo(0.0)
        // Device leg: auth always all-SKIP.
        assertThat(r.authOutcome).isEqualTo(DeviceVerifyAttempt.AuthOutcome.ALL_SKIP)
        // Quality present: the analyzer's Float metrics are widened to Double. Construct the
        // expected via the same Float→Double widening so the assertion documents that exact contract
        // (a literal 0.8 Double != 0.8f.toDouble(), which is 0.800000011920929).
        assertThat(r.quality)
            .isEqualTo(DeviceVerifyAttempt.Quality(0.8f.toDouble(), 0.5f.toDouble(), 0.6f.toDouble()))
        // version/ecc unknown device-side ⇒ host "not probed" sentinel.
        assertThat(r.version).isEqualTo(0)
        assertThat(r.ecc).isEqualTo(0)
    }

    @Test
    fun fromScanAttempt_detectFailure_mapsToFail_detect() {
        // A sample decode outcome: detector found nothing ⇒ FAIL / DETECT.
        val r = DeviceVerifyAttemptExporter.fromScanAttempt(
            isSuccess = false,
            decodeTimeMs = 0,
            colorNumber = 0, // auto-detect / unknown
            failAttr = DeviceVerifyAttempt.FailAttr.DETECT,
            focus = 0.2f, brightness = 0.4f, contrast = 0.1f
        )
        assertThat(r.outcome).isEqualTo(DeviceVerifyAttempt.Outcome.FAIL)
        assertThat(r.failAttr).isEqualTo(DeviceVerifyAttempt.FailAttr.DETECT)
        assertThat(r.colorNumber).isEqualTo(0)
        assertThat(r.authOutcome).isEqualTo(DeviceVerifyAttempt.AuthOutcome.ALL_SKIP)
    }

    @Test
    fun fromScanAttempt_partialQuality_yieldsNullBlock() {
        // The live-frame quality snapshot is all-or-nothing; a partial set (e.g. one sentinel) must
        // not emit a half-populated quality block.
        val r = DeviceVerifyAttemptExporter.fromScanAttempt(
            isSuccess = false,
            decodeTimeMs = 3,
            colorNumber = 8,
            failAttr = DeviceVerifyAttempt.FailAttr.CLASSIFICATION,
            focus = 0.5f, brightness = null, contrast = 0.5f
        )
        assertThat(r.quality).isNull()
        assertThat(r.toJson()).doesNotContain("\"quality\"")
    }

    @Test
    fun fromSuiteBDecode_mapsToPass_none_noQuality() {
        // Suite-B decoded a bundled fixture ⇒ PASS, no live-frame quality, decode-only timing.
        val r = DeviceVerifyAttemptExporter.fromSuiteBDecode(colorNumber = 64, medianDecodeMs = 5.5)
        assertThat(r.outcome).isEqualTo(DeviceVerifyAttempt.Outcome.PASS)
        assertThat(r.failAttr).isEqualTo(DeviceVerifyAttempt.FailAttr.NONE)
        assertThat(r.colorNumber).isEqualTo(64)
        assertThat(r.quality).isNull()
        assertThat(r.stageTimingsMs.decode).isEqualTo(5.5)
        assertThat(r.authOutcome).isEqualTo(DeviceVerifyAttempt.AuthOutcome.ALL_SKIP)
    }

    @Test
    fun paths_pointIntoExportDir() {
        val dir = tmp.newFolder("export")
        val exporter = exporterIn(dir)
        assertThat(exporter.jsonlPath()).endsWith(DeviceVerifyAttemptExporter.JSONL_FILE)
        assertThat(exporter.csvPath()).endsWith(DeviceVerifyAttemptExporter.CSV_FILE)
        assertThat(exporter.jsonlPath()).contains(dir.name)
    }
}
