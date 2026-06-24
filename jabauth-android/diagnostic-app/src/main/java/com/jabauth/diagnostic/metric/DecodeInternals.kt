package com.jabauth.diagnostic.metric

/**
 * Stage 3 — structured decode-internals capture for the device leg.
 *
 * <h2>What this is</h2>
 * A typed Kotlin record of the native decoder's per-decode forensic markers — the same markers
 * `DiagnosticControl.setDiagVerbose(true)` makes the C decoder/detector emit, which today exist
 * ONLY as free-text logcat lines. [parse] lifts one decode's worth of those lines into this struct,
 * so a colour-classification-margin failure is inspectable as DATA — which modules were classified,
 * the learned palette, the DETECT status, where Part I/II stopped — instead of being grepped out of a
 * multi-MB trace.
 *
 * <h2>Path chosen (b), not (a)</h2>
 * This is a pure parse of the existing diag-verbose markers, NOT a JNI/C out-struct. The markers are
 * already emitted and well-structured; lifting them in Kotlin is contained and touches no C or JNI
 * (and so no Fraunhofer-tracked source), where the out-struct path would have to add a struct in
 * jabcode.h, fill it across decoder.c + detector.c at several sites, and re-cut the JABCodeMobile JNI
 * + Panama signatures. This mirrors the merged Stage-1b convention exactly: pure-Kotlin,
 * dependency-free, hand-rolled JSON, JVM-unit-testable.
 *
 * <h2>The markers parsed</h2>
 * (message text only — the parser is tag-agnostic, so it works whether the caller keeps the logcat
 * `JABCodeDetector:` / `JABCodeDecoder:` prefix or strips it):
 *  - `DETECT SUCCESS FP0(x,y,ms=…) …` → [detectSuccess] + [finderPatterns]
 *  - `[PartI_DIAG] module_colors=[a,b,c,d]` → [partIModuleColors]
 *  - `[PartI_DIAG] SUCCESS Nc=…` / `FAIL_STAGE=… module[i] …` → [partINc] / [partIFailStage] / [partIFailModule]
 *  - `DIAG_PALETTE_LEARNED Nc=… idx=… rgb=(r,g,b)` → [learnedPalette]
 *  - `DIAG_PALETTE_LEARNED Nc=… colors=… hash=0x…` → [paletteColors] / [paletteHash]
 *  - `DIAG_PARTII_RESULT result=… Nc=… ok=…` → [partIIResult] / [partIIOk]
 *  - `Nc_FALLBACK: Retrying with Nc=… (try t/n, original=o)` → [ncFallbackRetries]
 *  - `FAIL_ATTR stage=… status=… …` / `DECODE_OK Nc=… total=… module_size=…` → [terminalOutcome] et al.
 *
 * <h2>How it relates to [DeviceVerifyAttempt]</h2>
 * [DeviceVerifyAttempt] is the cross-leg OUTCOME record (one row: Nc, quality, pass/fail, fail-attr,
 * timings) that the device and host both emit and diff column-for-column. This record is the device
 * leg's WHY — the codec internals behind one such attempt, which the host has no equivalent of (the
 * decoder runs only device-side). The two are exported side by side (see [DecodeInternalsExporter]),
 * sharing the same `failAttr` axis: a [DeviceVerifyAttempt] row with `failAttr=CLASSIFICATION` is
 * explained by the matching [DecodeInternals] showing, e.g., a learned palette with two near-collinear
 * entries and a Part II `ldpc_fail`.
 *
 * <h2>Telemetry only — never a credential</h2>
 * Every field here is a coordinate, a module-size, an RGB palette triplet, a small integer index, a
 * status code, or an outcome string — exactly what the diag markers already print. The decoder's
 * markers never print the decoded payload, and [parse] has no field to put one in even if a line
 * contained it: lines that don't match a known marker are ignored.
 *
 * @property detectSuccess     true iff a `DETECT SUCCESS` line was seen (all four finder patterns locked)
 * @property finderPatterns    the four located finder patterns (centre + module size), empty if no DETECT
 * @property partIModuleColors the four Part-I metadata module colour indices `[m0,m1,m2,m3]`, or empty
 * @property partINc           the Nc decoded by Part I (`[PartI_DIAG] SUCCESS Nc=`), or null if Part I failed
 * @property partIFailStage    the Part-I failure stage (`module_color`/`pair_bits`/`ldpc`), or null on success
 * @property partIFailModule   the module index at which Part I failed (when [partIFailStage] is `module_color`), else null
 * @property learnedPalette    the per-capture learned palette slots (idx → rgb), in emit order, possibly empty
 * @property paletteColors     palette colour count from the `colors=` summary line, or null if absent
 * @property paletteHash       deterministic palette hash from the `hash=0x…` summary line, or null if absent
 * @property partIIResult      Part-II result token (a numeric string, `default_fallthrough`, `skipped`), or null
 * @property partIIOk          whether Part II was accepted (`ok=1`), or null if no Part-II line was seen
 * @property partIINc          the Nc reported on the Part-II line, or null
 * @property ncFallbackRetries the Nc-fallback retry ladder, in order (each: which Nc, try i/n, original Nc)
 * @property terminalOutcome   the terminal verdict for the attempt — see [TerminalOutcome]
 * @property failAttrStage     the C `FAIL_ATTR stage=` token when [terminalOutcome] is FAIL, else null
 * @property failAttrStatus    the C `FAIL_ATTR status=` code when [terminalOutcome] is FAIL, else null
 * @property decodedNc         the Nc on a terminal `DECODE_OK Nc=` line, or null unless DECODE_OK
 */
data class DecodeInternals(
    val detectSuccess: Boolean,
    val finderPatterns: List<FinderPattern>,
    val partIModuleColors: List<Int>,
    val partINc: Int?,
    val partIFailStage: String?,
    val partIFailModule: Int?,
    val learnedPalette: List<PaletteEntry>,
    val paletteColors: Int?,
    val paletteHash: String?,
    val partIIResult: String?,
    val partIIOk: Boolean?,
    val partIINc: Int?,
    val ncFallbackRetries: List<NcFallback>,
    val terminalOutcome: TerminalOutcome,
    val failAttrStage: String?,
    val failAttrStatus: Int?,
    val decodedNc: Int?
) {

    /**
     * Terminal verdict of a decode attempt, derived from the closing C marker.
     *  - [DECODE_OK]   — a `DECODE_OK` line was seen (success).
     *  - [FAIL_ATTR]   — a `FAIL_ATTR` line was seen and no `DECODE_OK` followed (failure, attributed).
     *  - [UNKNOWN]     — neither terminal line was present in the block (e.g. truncated capture window).
     */
    enum class TerminalOutcome { DECODE_OK, FAIL_ATTR, UNKNOWN }

    /**
     * A located finder pattern from the `DETECT SUCCESS` line.
     *
     * @property x          centre x in image pixels
     * @property y          centre y in image pixels
     * @property moduleSize estimated module size at this corner (the `ms=` field)
     */
    data class FinderPattern(val x: Double, val y: Double, val moduleSize: Double)

    /**
     * One slot of the per-capture learned palette (`DIAG_PALETTE_LEARNED … idx=… rgb=(r,g,b)`).
     * This is the colour the decoder learned for a palette index from THIS capture — the raw material
     * a classification-margin failure is read off of (e.g. two indices whose RGBs are nearly equal).
     *
     * @property index palette slot index
     * @property r     red channel 0..255
     * @property g     green channel 0..255
     * @property b     blue channel 0..255
     */
    data class PaletteEntry(val index: Int, val r: Int, val g: Int, val b: Int)

    /**
     * One rung of the Nc-fallback retry ladder (`Nc_FALLBACK: Retrying with Nc=… (try i/n, original=o)`).
     * A non-empty list means auto-detect did not lock the carrier's Nc on the first try — itself a
     * strong colour-margin signal.
     *
     * @property nc         the Nc this retry attempted
     * @property tryIndex   1-based retry number
     * @property tryCount   total retries in the ladder
     * @property originalNc the Nc Part I first proposed
     */
    data class NcFallback(val nc: Int, val tryIndex: Int, val tryCount: Int, val originalNc: Int)

    /**
     * Serialize to a single-line JSON object. Dependency-free and hand-rolled to match the Stage-1b
     * [DeviceVerifyAttempt.toJson] convention (the module ships no JSON library). Field order is the
     * record's declaration order; absent (null / empty) optionals are omitted so a clean DECODE_OK
     * block and a failed one are both compact and self-describing. Doubles render via
     * [DeviceVerifyAttempt.jsonNumber] for the same Java/Jackson-compatible numeric form as the
     * outcome record, so the two files read consistently.
     */
    fun toJson(): String = buildString {
        append('{')
        append("\"detectSuccess\":").append(detectSuccess)
        if (finderPatterns.isNotEmpty()) {
            append(",\"finderPatterns\":[")
            finderPatterns.forEachIndexed { i, fp ->
                if (i > 0) append(',')
                append("{\"x\":").append(num(fp.x))
                append(",\"y\":").append(num(fp.y))
                append(",\"moduleSize\":").append(num(fp.moduleSize)).append('}')
            }
            append(']')
        }
        if (partIModuleColors.isNotEmpty()) {
            append(",\"partIModuleColors\":[")
            partIModuleColors.forEachIndexed { i, c -> if (i > 0) append(','); append(c) }
            append(']')
        }
        partINc?.let { append(",\"partINc\":").append(it) }
        partIFailStage?.let { append(",\"partIFailStage\":\"").append(it).append('"') }
        partIFailModule?.let { append(",\"partIFailModule\":").append(it) }
        if (learnedPalette.isNotEmpty()) {
            append(",\"learnedPalette\":[")
            learnedPalette.forEachIndexed { i, p ->
                if (i > 0) append(',')
                append("{\"index\":").append(p.index)
                append(",\"r\":").append(p.r)
                append(",\"g\":").append(p.g)
                append(",\"b\":").append(p.b).append('}')
            }
            append(']')
        }
        paletteColors?.let { append(",\"paletteColors\":").append(it) }
        paletteHash?.let { append(",\"paletteHash\":\"").append(it).append('"') }
        partIIResult?.let { append(",\"partIIResult\":\"").append(it).append('"') }
        partIIOk?.let { append(",\"partIIOk\":").append(it) }
        partIINc?.let { append(",\"partIINc\":").append(it) }
        if (ncFallbackRetries.isNotEmpty()) {
            append(",\"ncFallbackRetries\":[")
            ncFallbackRetries.forEachIndexed { i, f ->
                if (i > 0) append(',')
                append("{\"nc\":").append(f.nc)
                append(",\"tryIndex\":").append(f.tryIndex)
                append(",\"tryCount\":").append(f.tryCount)
                append(",\"originalNc\":").append(f.originalNc).append('}')
            }
            append(']')
        }
        append(",\"terminalOutcome\":\"").append(terminalOutcome.name).append('"')
        failAttrStage?.let { append(",\"failAttrStage\":\"").append(it).append('"') }
        failAttrStatus?.let { append(",\"failAttrStatus\":").append(it) }
        decodedNc?.let { append(",\"decodedNc\":").append(it) }
        append('}')
    }

    companion object {
        private fun num(d: Double): String = DeviceVerifyAttempt.jsonNumber(d)

        // One regex per marker. Each tolerates a leading logcat prefix (tag/pid/timestamp) by
        // matching the marker token anywhere in the line, and ignores trailing free-text tails
        // (e.g. the parenthetical commentary on some PARTII lines). Kept deliberately literal to the
        // C printf format strings in src/jabcode/decoder.c and detector.c.
        private val RE_DETECT = Regex(
            """DETECT SUCCESS\s+FP0\(([-\d.]+),([-\d.]+),ms=([-\d.]+)\)\s+""" +
                """FP1\(([-\d.]+),([-\d.]+),ms=([-\d.]+)\)\s+""" +
                """FP2\(([-\d.]+),([-\d.]+),ms=([-\d.]+)\)\s+""" +
                """FP3\(([-\d.]+),([-\d.]+),ms=([-\d.]+)\)"""
        )
        private val RE_MODULE_COLORS = Regex("""\[PartI_DIAG]\s+module_colors=\[(-?\d+),(-?\d+),(-?\d+),(-?\d+)]""")
        private val RE_PARTI_SUCCESS = Regex("""\[PartI_DIAG]\s+SUCCESS\s+Nc=(\d+)""")
        private val RE_PARTI_FAIL = Regex("""\[PartI_DIAG]\s+FAIL_STAGE=(\w+)(?:\s+module\[(\d+)])?""")
        private val RE_PALETTE_SLOT = Regex("""DIAG_PALETTE_LEARNED\s+Nc=(\d+)\s+idx=(\d+)\s+rgb=\((\d+),(\d+),(\d+)\)""")
        private val RE_PALETTE_SUMMARY = Regex("""DIAG_PALETTE_LEARNED\s+Nc=(\d+)\s+colors=(\d+)\s+hash=(0x[0-9a-fA-F]+)""")
        private val RE_PARTII = Regex("""DIAG_PARTII_RESULT\s+result=(\w+)\s+Nc=(\d+)\s+ok=(\d+)""")
        private val RE_NC_FALLBACK = Regex("""Nc_FALLBACK:\s+Retrying with Nc=(\d+)\s+\(try\s+(\d+)/(\d+),\s+original=(\d+)\)""")
        private val RE_FAIL_ATTR = Regex("""FAIL_ATTR\s+stage=(\w+)\s+status=(-?\d+)""")
        private val RE_DECODE_OK = Regex("""DECODE_OK\s+Nc=(\d+)""")

        /**
         * Parse one decode attempt's worth of diag-verbose marker lines into a [DecodeInternals].
         *
         * The input is the set of lines emitted between (and including) a `DETECT SUCCESS` and its
         * terminal `DECODE_OK` / `FAIL_ATTR` — i.e. one decode window with `g_diag_verbose` on. Lines
         * may be raw logcat (with the tag/timestamp prefix) or already stripped to the message; either
         * works. Unrecognized lines are ignored, so interleaved unrelated logcat is harmless.
         *
         * Semantics for repeated markers within one block (the Nc-fallback ladder re-emits palette and
         * Part II lines per rung): the LAST occurrence wins for the scalar summaries (palette hash,
         * Part II result, Part I outcome) — that is the rung that produced the terminal verdict — while
         * [learnedPalette] keeps the slots of the last Nc seen, and [ncFallbackRetries] accumulates the
         * whole ladder in order. This makes the record describe the decode as it concluded, with the
         * retry history preserved.
         *
         * @param lines the marker lines for one decode window (in emission order)
         * @return the structured internals; [TerminalOutcome.UNKNOWN] if no terminal marker was present
         */
        fun parse(lines: List<String>): DecodeInternals {
            var detectSuccess = false
            var finderPatterns = emptyList<FinderPattern>()
            var partIModuleColors = emptyList<Int>()
            var partINc: Int? = null
            var partIFailStage: String? = null
            var partIFailModule: Int? = null
            // Palette slots are gathered per Nc; a new Nc (fallback rung) resets the accumulator so the
            // final list is the palette of the rung that concluded the decode.
            var paletteNc: Int? = null
            var paletteSlots = mutableListOf<PaletteEntry>()
            var paletteColors: Int? = null
            var paletteHash: String? = null
            var partIIResult: String? = null
            var partIIOk: Boolean? = null
            var partIINc: Int? = null
            val ncFallbackRetries = mutableListOf<NcFallback>()
            var terminalOutcome = TerminalOutcome.UNKNOWN
            var failAttrStage: String? = null
            var failAttrStatus: Int? = null
            var decodedNc: Int? = null

            for (raw in lines) {
                val line = raw.trim()

                RE_DETECT.find(line)?.let { m ->
                    detectSuccess = true
                    val g = m.groupValues
                    finderPatterns = listOf(
                        FinderPattern(g[1].toDouble(), g[2].toDouble(), g[3].toDouble()),
                        FinderPattern(g[4].toDouble(), g[5].toDouble(), g[6].toDouble()),
                        FinderPattern(g[7].toDouble(), g[8].toDouble(), g[9].toDouble()),
                        FinderPattern(g[10].toDouble(), g[11].toDouble(), g[12].toDouble())
                    )
                    return@let
                }
                RE_MODULE_COLORS.find(line)?.let { m ->
                    val g = m.groupValues
                    partIModuleColors = listOf(g[1].toInt(), g[2].toInt(), g[3].toInt(), g[4].toInt())
                    return@let
                }
                RE_PARTI_SUCCESS.find(line)?.let { m ->
                    partINc = m.groupValues[1].toInt()
                    // A later SUCCESS supersedes an earlier FAIL on a retry rung.
                    partIFailStage = null
                    partIFailModule = null
                    return@let
                }
                RE_PARTI_FAIL.find(line)?.let { m ->
                    partIFailStage = m.groupValues[1]
                    partIFailModule = m.groupValues[2].takeIf { it.isNotEmpty() }?.toInt()
                    return@let
                }
                RE_PALETTE_SLOT.find(line)?.let { m ->
                    val g = m.groupValues
                    val nc = g[1].toInt()
                    if (paletteNc != nc) {
                        paletteNc = nc
                        paletteSlots = mutableListOf()
                    }
                    paletteSlots.add(PaletteEntry(g[2].toInt(), g[3].toInt(), g[4].toInt(), g[5].toInt()))
                    return@let
                }
                RE_PALETTE_SUMMARY.find(line)?.let { m ->
                    paletteColors = m.groupValues[2].toInt()
                    paletteHash = m.groupValues[3]
                    return@let
                }
                RE_PARTII.find(line)?.let { m ->
                    partIIResult = m.groupValues[1]
                    partIINc = m.groupValues[2].toInt()
                    partIIOk = m.groupValues[3] == "1"
                    return@let
                }
                RE_NC_FALLBACK.find(line)?.let { m ->
                    val g = m.groupValues
                    ncFallbackRetries.add(
                        NcFallback(g[1].toInt(), g[2].toInt(), g[3].toInt(), g[4].toInt())
                    )
                    return@let
                }
                RE_DECODE_OK.find(line)?.let { m ->
                    terminalOutcome = TerminalOutcome.DECODE_OK
                    decodedNc = m.groupValues[1].toInt()
                    // A success closes the attempt; any FAIL seen on an earlier rung is not terminal.
                    failAttrStage = null
                    failAttrStatus = null
                    return@let
                }
                RE_FAIL_ATTR.find(line)?.let { m ->
                    // Only the LAST FAIL_ATTR with no following DECODE_OK is the terminal verdict.
                    if (terminalOutcome != TerminalOutcome.DECODE_OK) {
                        terminalOutcome = TerminalOutcome.FAIL_ATTR
                        failAttrStage = m.groupValues[1]
                        failAttrStatus = m.groupValues[2].toInt()
                    }
                    return@let
                }
            }

            return DecodeInternals(
                detectSuccess = detectSuccess,
                finderPatterns = finderPatterns,
                partIModuleColors = partIModuleColors,
                partINc = partINc,
                partIFailStage = partIFailStage,
                partIFailModule = partIFailModule,
                learnedPalette = paletteSlots.toList(),
                paletteColors = paletteColors,
                paletteHash = paletteHash,
                partIIResult = partIIResult,
                partIIOk = partIIOk,
                partIINc = partIINc,
                ncFallbackRetries = ncFallbackRetries.toList(),
                terminalOutcome = terminalOutcome,
                failAttrStage = failAttrStage,
                failAttrStatus = failAttrStatus,
                decodedNc = decodedNc
            )
        }
    }
}
