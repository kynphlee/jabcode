package com.jabauth.diagnostic.metric

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Stage 3 tests for [DecodeInternalsExporter] — capturing a decode window's marker lines to a
 * well-formed JSONL file, and the round-trip (each written line equals the parsed record's `toJson()`).
 * Pure-JVM: a [TemporaryFolder] stands in for the on-device export directory, so no Android `Context` /
 * instrumentation / NDK is needed.
 */
class DecodeInternalsExporterTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val okBlock = listOf(
        "JABCodeDetector: DETECT SUCCESS FP0(64,64,ms=4) FP1(320,64,ms=4) FP2(64,320,ms=4) FP3(320,320,ms=4)",
        "JABCodeDecoder: [PartI_DIAG] SUCCESS Nc=2",
        "JABCode: DIAG_PARTII_RESULT result=1 Nc=2 ok=1",
        "JABCodeDetector: DECODE_OK Nc=2 total=1 module_size=4.00"
    )

    private val failBlock = listOf(
        "JABCodeDetector: DETECT SUCCESS FP0(10,10,ms=2) FP1(90,10,ms=2) FP2(10,90,ms=2) FP3(90,90,ms=2)",
        "JABCodeDecoder: [PartI_DIAG] FAIL_STAGE=ldpc pre_ldpc=[1,2,3,4,5,6]",
        "JABCodeDetector: FAIL_ATTR stage=decode_data status=1 total=1"
    )

    @Test
    fun capture_parsesAndAppends_oneJsonlLinePerWindow_roundTripping() {
        val dir = tmp.newFolder("export")
        val exporter = DecodeInternalsExporter(dir)

        val ok = exporter.capture(okBlock)
        val fail = exporter.capture(failBlock)

        // capture() returns the parsed record.
        assertThat(ok.terminalOutcome).isEqualTo(DecodeInternals.TerminalOutcome.DECODE_OK)
        assertThat(ok.decodedNc).isEqualTo(2)
        assertThat(fail.terminalOutcome).isEqualTo(DecodeInternals.TerminalOutcome.FAIL_ATTR)
        assertThat(fail.failAttrStage).isEqualTo("decode_data")

        val lines = File(dir, DecodeInternalsExporter.INTERNALS_FILE).readLines()
        // One JSONL line per captured window, each byte-equal to the record's own toJson().
        assertThat(lines).hasSize(2)
        assertThat(lines[0]).isEqualTo(ok.toJson())
        assertThat(lines[1]).isEqualTo(fail.toJson())
    }

    @Test
    fun capture_isAppend_acrossInstances_intoSameSiblingFile() {
        val dir = tmp.newFolder("export")
        // First exporter writes one record; a fresh exporter over the same dir appends, not truncates —
        // matching the Stage-1b sibling so a session's internals accumulate alongside the outcomes.
        DecodeInternalsExporter(dir).capture(okBlock)
        DecodeInternalsExporter(dir).capture(failBlock)

        val file = File(dir, DecodeInternalsExporter.INTERNALS_FILE)
        assertThat(file.readLines()).hasSize(2)
        // The sink is named to sit beside the Stage-1b outcome export in the same directory.
        assertThat(file.name).isEqualTo("decode-internals.jsonl")
    }

    @Test
    fun append_writesPreParsedRecord_withoutReparsing() {
        val dir = tmp.newFolder("export")
        val exporter = DecodeInternalsExporter(dir)
        val record = DecodeInternals.parse(okBlock)

        exporter.append(record)

        val lines = File(dir, DecodeInternalsExporter.INTERNALS_FILE).readLines()
        assertThat(lines).hasSize(1)
        assertThat(lines[0]).isEqualTo(record.toJson())
    }

    @Test
    fun jsonlPath_pointsAtTheSink() {
        val dir = tmp.newFolder("export")
        val exporter = DecodeInternalsExporter(dir)
        assertThat(exporter.jsonlPath()).isEqualTo(File(dir, "decode-internals.jsonl").absolutePath)
    }

    @Test
    fun captureAll_slicesAMultiDecodeDump_intoOneRecordPerDetectWindow() {
        val dir = tmp.newFolder("export")
        val exporter = DecodeInternalsExporter(dir)

        // A flat dump (as a `logcat -d` read would return) covering TWO decode attempts back to back,
        // with pre-DETECT noise that must be dropped.
        val dump = listOf("JABCodeDetector: detectMaster: pass 1 failed, retrying") + okBlock + failBlock

        val records = exporter.captureAll(dump)

        assertThat(records).hasSize(2)
        assertThat(records[0].terminalOutcome).isEqualTo(DecodeInternals.TerminalOutcome.DECODE_OK)
        assertThat(records[1].terminalOutcome).isEqualTo(DecodeInternals.TerminalOutcome.FAIL_ATTR)
        // Both windows were appended to the sink, in order.
        val lines = File(dir, DecodeInternalsExporter.INTERNALS_FILE).readLines()
        assertThat(lines).hasSize(2)
        assertThat(lines[0]).isEqualTo(records[0].toJson())
    }

    @Test
    fun splitWindows_opensOnEachDetectSuccess_andDropsLeadingNoise() {
        val windows = DecodeInternalsExporter.splitWindows(
            listOf(
                "noise before any detect",
                "DETECT SUCCESS FP0(1,1,ms=2) FP1(9,1,ms=2) FP2(1,9,ms=2) FP3(9,9,ms=2)",
                "DECODE_OK Nc=2 total=1 module_size=2.00",
                "DETECT SUCCESS FP0(2,2,ms=3) FP1(8,2,ms=3) FP2(2,8,ms=3) FP3(8,8,ms=3)",
                "FAIL_ATTR stage=decode_data status=1 total=1"
            )
        )
        // Leading noise dropped; two windows, each starting at its DETECT SUCCESS.
        assertThat(windows).hasSize(2)
        assertThat(windows[0]).hasSize(2)
        assertThat(windows[0][0]).contains("FP0(1,1")
        assertThat(windows[1][0]).contains("FP0(2,2")
    }

    @Test
    fun splitWindows_emptyOrNoDetect_yieldsNoWindows() {
        assertThat(DecodeInternalsExporter.splitWindows(emptyList())).isEmpty()
        assertThat(DecodeInternalsExporter.splitWindows(listOf("just", "noise"))).isEmpty()
    }
}
