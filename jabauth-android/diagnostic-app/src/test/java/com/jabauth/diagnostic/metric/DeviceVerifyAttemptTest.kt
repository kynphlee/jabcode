package com.jabauth.diagnostic.metric

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Schema tests for the device leg's [DeviceVerifyAttempt] — the JSON it emits must be byte-compatible
 * on the shared columns with the host-side `org.nexus.jabauth.coa.metric.VerifyAttempt` record (a
 * Jackson-serialized Java record in jab-auth-spring-integration).
 *
 * The expectations here are pinned against that host record's own `VerifyAttemptTest`: Jackson
 * serializes record components in declaration order, omits a null `quality` (`@JsonInclude(NON_NULL)`),
 * and renders enums by their UPPERCASE `.name()`. These are pure-JVM tests — no Android, no
 * instrumentation, no NDK.
 */
class DeviceVerifyAttemptTest {

    @Test
    fun deviceAttempt_serializesShared_columns_inHostOrder_withSkipAuth() {
        // A device-leg PASS with a live-frame quality block.
        val attempt = DeviceVerifyAttempt(
            colorNumber = 8,
            version = 0,
            ecc = 0,
            imagePx = 0,
            quality = DeviceVerifyAttempt.Quality(focus = 0.91, brightness = 0.62, contrast = 0.77),
            outcome = DeviceVerifyAttempt.Outcome.PASS,
            failAttr = DeviceVerifyAttempt.FailAttr.NONE,
            stageTimingsMs = DeviceVerifyAttempt.StageTimingsMs(decode = 2.5),
            authOutcome = DeviceVerifyAttempt.AuthOutcome.ALL_SKIP
        )

        val json = attempt.toJson()

        // Exact byte-for-byte string, in the host record's declaration order. This is the strongest
        // form of the "diffs cleanly column-for-column" contract: any drift in key order, spacing,
        // enum casing, or number rendering breaks this assertion.
        assertThat(json).isEqualTo(
            "{" +
                "\"colorNumber\":8," +
                "\"version\":0," +
                "\"ecc\":0," +
                "\"imagePx\":0," +
                "\"quality\":{\"focus\":0.91,\"brightness\":0.62,\"contrast\":0.77}," +
                "\"outcome\":\"PASS\"," +
                "\"failAttr\":\"NONE\"," +
                "\"stageTimingsMs\":{\"decode\":2.5,\"pki\":0.0,\"jwt\":0.0,\"abe\":0.0}," +
                "\"authOutcome\":{\"pki\":\"SKIP\",\"jwt\":\"SKIP\",\"abe\":\"SKIP\"}" +
                "}"
        )
    }

    @Test
    fun deviceAttempt_matchesHostStrings_forSharedColumns() {
        // Mirrors the substrings asserted in the host VerifyAttemptTest so the two are pinned to the
        // same wire strings.
        val pass = DeviceVerifyAttempt(
            8, 5, 3, 384, null,
            DeviceVerifyAttempt.Outcome.PASS,
            DeviceVerifyAttempt.FailAttr.NONE,
            DeviceVerifyAttempt.StageTimingsMs(decode = 1.2),
            DeviceVerifyAttempt.AuthOutcome.ALL_SKIP
        )
        val json = pass.toJson()
        assertThat(json).contains("\"colorNumber\":8")
        assertThat(json).contains("\"version\":5")
        assertThat(json).contains("\"ecc\":3")
        assertThat(json).contains("\"imagePx\":384")
        assertThat(json).contains("\"outcome\":\"PASS\"")
        assertThat(json).contains("\"failAttr\":\"NONE\"")
        assertThat(json).contains("\"stageTimingsMs\"")
        assertThat(json).contains("\"authOutcome\"")
        assertThat(json).contains("\"decode\":1.2")
        // Device leg never runs the crypto stages: all auth outcomes are SKIP.
        assertThat(json).contains("\"pki\":\"SKIP\"")
        assertThat(json).contains("\"jwt\":\"SKIP\"")
        assertThat(json).contains("\"abe\":\"SKIP\"")
    }

    @Test
    fun nullQuality_isOmitted_matchingHostNonNull() {
        // Host @JsonInclude(NON_NULL): a null quality block is omitted entirely (the fixture-decode
        // and no-live-frame cases).
        val attempt = DeviceVerifyAttempt(
            8, 0, 0, 0, null,
            DeviceVerifyAttempt.Outcome.PASS,
            DeviceVerifyAttempt.FailAttr.NONE,
            DeviceVerifyAttempt.StageTimingsMs(decode = 4.0),
            DeviceVerifyAttempt.AuthOutcome.ALL_SKIP
        )
        assertThat(attempt.toJson()).doesNotContain("\"quality\"")
    }

    @Test
    fun classificationFailure_carriesUppercaseCode_andSkipAuth() {
        // The device-side CLASSIFICATION example from the host's deviceSideAttempt test.
        val attempt = DeviceVerifyAttempt(
            8, 0, 3, 384,
            DeviceVerifyAttempt.Quality(0.91, 0.62, 0.77),
            DeviceVerifyAttempt.Outcome.FAIL,
            DeviceVerifyAttempt.FailAttr.CLASSIFICATION,
            DeviceVerifyAttempt.StageTimingsMs(decode = 2.5),
            DeviceVerifyAttempt.AuthOutcome.ALL_SKIP
        )
        val json = attempt.toJson()
        assertThat(json).contains("\"failAttr\":\"CLASSIFICATION\"")
        assertThat(json).contains("\"outcome\":\"FAIL\"")
        assertThat(json).contains("\"focus\":0.91")
        assertThat(json).contains("\"brightness\":0.62")
        assertThat(json).contains("\"contrast\":0.77")
        assertThat(json).contains("\"abe\":\"SKIP\"")
    }

    @Test
    fun detectFailure_wireString_isUppercaseName() {
        val attempt = DeviceVerifyAttempt(
            4, 0, 0, 0, null,
            DeviceVerifyAttempt.Outcome.FAIL,
            DeviceVerifyAttempt.FailAttr.DETECT,
            DeviceVerifyAttempt.StageTimingsMs(decode = 1.0),
            DeviceVerifyAttempt.AuthOutcome.ALL_SKIP
        )
        assertThat(attempt.toJson()).contains("\"failAttr\":\"DETECT\"")
    }

    @Test
    fun failAttr_lowercaseCodes_mirrorHostCodeAccessor() {
        // The lowercase codes mirror the host FailAttr.code() accessor (used for C-side
        // cross-referencing), and stay stable independently of the UPPERCASE JSON wire name.
        assertThat(DeviceVerifyAttempt.FailAttr.NONE.code).isEqualTo("none")
        assertThat(DeviceVerifyAttempt.FailAttr.DETECT.code).isEqualTo("detect")
        assertThat(DeviceVerifyAttempt.FailAttr.CLASSIFICATION.code).isEqualTo("classification")
        // Auth codes are carried for a faithful host mirror even though the device never emits them.
        assertThat(DeviceVerifyAttempt.FailAttr.REPLAY.code).isEqualTo("replay")
        assertThat(DeviceVerifyAttempt.FailAttr.REVOKED.code).isEqualTo("revoked")
        assertThat(DeviceVerifyAttempt.FailAttr.EXPIRED.code).isEqualTo("expired")
        assertThat(DeviceVerifyAttempt.FailAttr.UNTRUSTED.code).isEqualTo("untrusted")
        assertThat(DeviceVerifyAttempt.FailAttr.BAD_SIG.code).isEqualTo("bad_sig")
    }

    @Test
    fun integralTimings_render_withTrailingZero_likeJacksonDouble() {
        // Jackson renders a double 0 as "0.0"; the hand-rolled writer must match so integral-valued
        // timing/quality columns stay byte-identical across legs.
        assertThat(DeviceVerifyAttempt.jsonNumber(0.0)).isEqualTo("0.0")
        assertThat(DeviceVerifyAttempt.jsonNumber(1.0)).isEqualTo("1.0")
        assertThat(DeviceVerifyAttempt.jsonNumber(2.5)).isEqualTo("2.5")
    }

    @Test
    fun csvRow_flattensColumns_andLeavesQualityBlank_whenNull() {
        val withQuality = DeviceVerifyAttempt(
            8, 0, 0, 0,
            DeviceVerifyAttempt.Quality(0.9, 0.6, 0.7),
            DeviceVerifyAttempt.Outcome.FAIL,
            DeviceVerifyAttempt.FailAttr.CLASSIFICATION,
            DeviceVerifyAttempt.StageTimingsMs(decode = 3.0),
            DeviceVerifyAttempt.AuthOutcome.ALL_SKIP
        )
        assertThat(withQuality.toCsvRow())
            .isEqualTo("8,0,0,0,0.9,0.6,0.7,FAIL,CLASSIFICATION,3.0,0.0,0.0,0.0,SKIP,SKIP,SKIP")

        val noQuality = withQuality.copy(quality = null, failAttr = DeviceVerifyAttempt.FailAttr.NONE,
            outcome = DeviceVerifyAttempt.Outcome.PASS)
        // The three quality columns are empty when the block is absent.
        assertThat(noQuality.toCsvRow())
            .isEqualTo("8,0,0,0,,,,PASS,NONE,3.0,0.0,0.0,0.0,SKIP,SKIP,SKIP")
    }

    @Test
    fun csvHeader_columnCount_matchesRowColumnCount() {
        val row = DeviceVerifyAttempt(
            8, 0, 0, 0,
            DeviceVerifyAttempt.Quality(0.9, 0.6, 0.7),
            DeviceVerifyAttempt.Outcome.PASS,
            DeviceVerifyAttempt.FailAttr.NONE,
            DeviceVerifyAttempt.StageTimingsMs(decode = 3.0),
            DeviceVerifyAttempt.AuthOutcome.ALL_SKIP
        ).toCsvRow()
        val headerCols = DeviceVerifyAttempt.CSV_HEADER.split(",").size
        val rowCols = row.split(",").size
        assertThat(rowCols).isEqualTo(headerCols)
    }
}
