package com.jabauth.diagnostic.metric

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Stage 3 unit tests for [DecodeInternals] — parsing the native decoder's diag-verbose markers into a
 * typed record, and the JSON it serializes to.
 *
 * The marker blocks below are representative of what `DiagnosticControl.setDiagVerbose(true)` makes the
 * C decoder/detector emit (the printf formats in src/jabcode/decoder.c and detector.c). Pure-JVM: no
 * Android, no instrumentation, no NDK.
 */
class DecodeInternalsTest {

    /**
     * A realistic FAILING 16-colour decode window: DETECT locked, Part I read the four metadata
     * modules and proposed Nc, the learned palette was logged, auto-detect walked the Nc-fallback
     * ladder, Part II could not validate, and the attempt closed with a FAIL_ATTR. The lines carry the
     * raw logcat tag prefix to prove the parser is prefix-tolerant.
     */
    private val classificationFailureBlock = listOf(
        "06-24 11:46:57.001  4821  4855 I JABCodeDetector: DETECT SUCCESS FP0(120,118,ms=3) FP1(540,119,ms=3) FP2(118,539,ms=3) FP3(541,540,ms=3)",
        "06-24 11:46:57.002  4821  4855 D JABCodeDecoder: [PartI_DIAG] BEGIN module_count_in=0 start_xy=(7,7)",
        "06-24 11:46:57.003  4821  4855 D JABCodeDecoder: [PartI_DIAG] module_colors=[0,3,6,3]",
        "06-24 11:46:57.004  4821  4855 D JABCodeDecoder: [PartI_DIAG] SUCCESS Nc=3",
        "06-24 11:46:57.005  4821  4855 I JABCode: DIAG_PALETTE_LEARNED Nc=3 idx=0 rgb=(8,8,9)",
        "06-24 11:46:57.006  4821  4855 I JABCode: DIAG_PALETTE_LEARNED Nc=3 idx=1 rgb=(240,16,18)",
        "06-24 11:46:57.007  4821  4855 I JABCode: DIAG_PALETTE_LEARNED Nc=3 idx=2 rgb=(15,238,20)",
        "06-24 11:46:57.008  4821  4855 I JABCode: DIAG_PALETTE_LEARNED Nc=3 colors=16 hash=0xdeadbeef",
        "06-24 11:46:57.009  4821  4855 I JABCode: DIAG_PARTII_RESULT result=0 Nc=3 ok=0",
        "06-24 11:46:57.010  4821  4855 I JABCode: Nc_FALLBACK: Retrying with Nc=2 (try 1/7, original=3)",
        "06-24 11:46:57.011  4821  4855 I JABCode: DIAG_PALETTE_LEARNED Nc=2 colors=8 hash=0x12345678",
        "06-24 11:46:57.012  4821  4855 I JABCode: DIAG_PARTII_RESULT result=skipped Nc=2 ok=0 (strict)",
        "06-24 11:46:57.013  4821  4855 I JABCodeDetector: FAIL_ATTR stage=detect_or_slave status=1 total=1 module_size=3.00"
    )

    @Test
    fun parse_classificationFailure_liftsDetectPartIPaletteAndTerminalFields() {
        val internals = DecodeInternals.parse(classificationFailureBlock)

        // DETECT — all four finder patterns located.
        assertThat(internals.detectSuccess).isTrue()
        assertThat(internals.finderPatterns).hasSize(4)
        assertThat(internals.finderPatterns[0]).isEqualTo(DecodeInternals.FinderPattern(120.0, 118.0, 3.0))
        assertThat(internals.finderPatterns[3]).isEqualTo(DecodeInternals.FinderPattern(541.0, 540.0, 3.0))

        // Part I — the four metadata module colours and the proposed Nc; no failure stage on success.
        assertThat(internals.partIModuleColors).containsExactly(0, 3, 6, 3).inOrder()
        assertThat(internals.partINc).isEqualTo(3)
        assertThat(internals.partIFailStage).isNull()
        assertThat(internals.partIFailModule).isNull()

        // Learned palette — keeps the slots of the LAST Nc seen (the fallback rung Nc=2 had only the
        // summary line, so the per-slot list is the Nc=3 slots), plus the final summary colors/hash.
        assertThat(internals.learnedPalette).hasSize(3)
        assertThat(internals.learnedPalette[1]).isEqualTo(DecodeInternals.PaletteEntry(1, 240, 16, 18))
        assertThat(internals.paletteColors).isEqualTo(8)
        assertThat(internals.paletteHash).isEqualTo("0x12345678")

        // Part II — last result wins (the strict skip on the fallback rung).
        assertThat(internals.partIIResult).isEqualTo("skipped")
        assertThat(internals.partIIOk).isFalse()
        assertThat(internals.partIINc).isEqualTo(2)

        // Nc-fallback ladder preserved in order.
        assertThat(internals.ncFallbackRetries).containsExactly(
            DecodeInternals.NcFallback(nc = 2, tryIndex = 1, tryCount = 7, originalNc = 3)
        ).inOrder()

        // Terminal verdict — a FAIL with the C attribution stage + status, no decoded Nc.
        assertThat(internals.terminalOutcome).isEqualTo(DecodeInternals.TerminalOutcome.FAIL_ATTR)
        assertThat(internals.failAttrStage).isEqualTo("detect_or_slave")
        assertThat(internals.failAttrStatus).isEqualTo(1)
        assertThat(internals.decodedNc).isNull()
    }

    @Test
    fun parse_cleanDecodeOk_setsSuccessTerminalAndDecodedNc() {
        val block = listOf(
            "JABCodeDetector: DETECT SUCCESS FP0(64,64,ms=4) FP1(320,64,ms=4) FP2(64,320,ms=4) FP3(320,320,ms=4)",
            "JABCodeDecoder: [PartI_DIAG] module_colors=[0,6,3,0]",
            "JABCodeDecoder: [PartI_DIAG] SUCCESS Nc=2",
            "JABCode: DIAG_PALETTE_LEARNED Nc=2 idx=0 rgb=(5,5,5)",
            "JABCode: DIAG_PALETTE_LEARNED Nc=2 idx=1 rgb=(250,250,250)",
            "JABCode: DIAG_PALETTE_LEARNED Nc=2 colors=8 hash=0x0badf00d",
            "JABCode: DIAG_PARTII_RESULT result=1 Nc=2 ok=1",
            "JABCodeDetector: DECODE_OK Nc=2 total=1 module_size=4.00"
        )

        val internals = DecodeInternals.parse(block)

        assertThat(internals.detectSuccess).isTrue()
        assertThat(internals.partINc).isEqualTo(2)
        assertThat(internals.partIIOk).isTrue()
        assertThat(internals.partIIResult).isEqualTo("1")
        assertThat(internals.terminalOutcome).isEqualTo(DecodeInternals.TerminalOutcome.DECODE_OK)
        assertThat(internals.decodedNc).isEqualTo(2)
        // A success window carries no FAIL attribution.
        assertThat(internals.failAttrStage).isNull()
        assertThat(internals.failAttrStatus).isNull()
        assertThat(internals.ncFallbackRetries).isEmpty()
    }

    @Test
    fun parse_partIModuleColorFailure_capturesFailStageAndModuleIndex() {
        // Part I aborts at the module-colour validity check on module 2 — a direct classification miss
        // on a metadata module (the kind of thing this whole record exists to surface as data).
        val block = listOf(
            "JABCodeDetector: DETECT SUCCESS FP0(10,10,ms=2) FP1(90,10,ms=2) FP2(10,90,ms=2) FP3(90,90,ms=2)",
            "JABCodeDecoder: [PartI_DIAG] FAIL_STAGE=module_color module[2] rgb=5 (mode0=0 valid_set={0,3,6})",
            "JABCodeDetector: FAIL_ATTR stage=detect_or_slave status=0 total=0 module_size=2.00"
        )

        val internals = DecodeInternals.parse(block)

        assertThat(internals.partINc).isNull()
        assertThat(internals.partIFailStage).isEqualTo("module_color")
        assertThat(internals.partIFailModule).isEqualTo(2)
        assertThat(internals.terminalOutcome).isEqualTo(DecodeInternals.TerminalOutcome.FAIL_ATTR)
        assertThat(internals.failAttrStatus).isEqualTo(0)
    }

    @Test
    fun parse_emptyOrIrrelevantLines_yieldUnknownTerminal() {
        // No markers at all → an UNKNOWN-terminal record, with every optional cleanly absent.
        val internals = DecodeInternals.parse(
            listOf("06-24 11:46:57.000 I SomeOtherTag: unrelated chatter", "")
        )
        assertThat(internals.detectSuccess).isFalse()
        assertThat(internals.terminalOutcome).isEqualTo(DecodeInternals.TerminalOutcome.UNKNOWN)
        assertThat(internals.finderPatterns).isEmpty()
        assertThat(internals.partIModuleColors).isEmpty()
        assertThat(internals.learnedPalette).isEmpty()
    }

    @Test
    fun toJson_emitsPresentFieldsInDeclarationOrder_andOmitsAbsentOptionals() {
        val internals = DecodeInternals.parse(classificationFailureBlock)
        val json = internals.toJson()

        // Required fields are always present.
        assertThat(json).startsWith("{\"detectSuccess\":true,")
        assertThat(json).contains("\"terminalOutcome\":\"FAIL_ATTR\"")
        // Present optionals serialize with their parsed values.
        assertThat(json).contains("\"partIModuleColors\":[0,3,6,3]")
        assertThat(json).contains("\"partINc\":3")
        assertThat(json).contains("\"paletteColors\":8")
        assertThat(json).contains("\"paletteHash\":\"0x12345678\"")
        assertThat(json).contains("\"partIIResult\":\"skipped\"")
        assertThat(json).contains("\"partIIOk\":false")
        assertThat(json).contains("\"ncFallbackRetries\":[{\"nc\":2,\"tryIndex\":1,\"tryCount\":7,\"originalNc\":3}]")
        assertThat(json).contains("\"failAttrStage\":\"detect_or_slave\"")
        assertThat(json).contains("\"failAttrStatus\":1")
        // Finder patterns nested array, first element, integral doubles render as Jackson-style x.0.
        assertThat(json).contains("\"finderPatterns\":[{\"x\":120.0,\"y\":118.0,\"moduleSize\":3.0}")
        // Absent optionals on a FAIL are omitted (no decoded Nc, no Part-I fail stage on a Part-I success).
        assertThat(json).doesNotContain("decodedNc")
        assertThat(json).doesNotContain("partIFailStage")
    }

    @Test
    fun toJson_cleanDecode_omitsFailFieldsAndCarriesDecodedNc() {
        val json = DecodeInternals.parse(
            listOf(
                "DETECT SUCCESS FP0(1,1,ms=2) FP1(9,1,ms=2) FP2(1,9,ms=2) FP3(9,9,ms=2)",
                "[PartI_DIAG] SUCCESS Nc=2",
                "DIAG_PARTII_RESULT result=1 Nc=2 ok=1",
                "DECODE_OK Nc=2 total=1 module_size=2.00"
            )
        ).toJson()

        assertThat(json).contains("\"terminalOutcome\":\"DECODE_OK\"")
        assertThat(json).contains("\"decodedNc\":2")
        assertThat(json).contains("\"partIIOk\":true")
        // No FAIL attribution on a success.
        assertThat(json).doesNotContain("failAttrStage")
        assertThat(json).doesNotContain("failAttrStatus")
    }
}
