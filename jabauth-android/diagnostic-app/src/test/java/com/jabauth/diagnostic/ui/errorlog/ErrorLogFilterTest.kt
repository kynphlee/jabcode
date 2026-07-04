package com.jabauth.diagnostic.ui.errorlog

import com.google.common.truth.Truth.assertThat
import com.jabauth.diagnostic.verify.StageErrorTag
import org.junit.Test

/** The Error Log filter predicate: severity facets + the new crypto-stage facets. Pure JVM. */
class ErrorLogFilterTest {

    private fun entry(severity: ErrorSeverity, stage: StageErrorTag? = null) =
        ErrorEntry(id = "id", timestamp = 0L, severity = severity, source = "src", message = "msg", stageTag = stage)

    @Test fun `ALL accepts everything`() {
        assertThat(ErrorFilter.ALL.accepts(entry(ErrorSeverity.ERROR, StageErrorTag.PKI))).isTrue()
        assertThat(ErrorFilter.ALL.accepts(entry(ErrorSeverity.INFO))).isTrue()
    }

    @Test fun `severity facets match on severity`() {
        assertThat(ErrorFilter.ERRORS_ONLY.accepts(entry(ErrorSeverity.ERROR))).isTrue()
        assertThat(ErrorFilter.ERRORS_ONLY.accepts(entry(ErrorSeverity.WARNING))).isFalse()
        assertThat(ErrorFilter.WARNINGS_ONLY.accepts(entry(ErrorSeverity.WARNING))).isTrue()
        assertThat(ErrorFilter.INFO_ONLY.accepts(entry(ErrorSeverity.INFO))).isTrue()
    }

    @Test fun `stage facets match the crypto-stage tag`() {
        assertThat(ErrorFilter.PKI.accepts(entry(ErrorSeverity.ERROR, StageErrorTag.PKI))).isTrue()
        assertThat(ErrorFilter.PKI.accepts(entry(ErrorSeverity.ERROR, StageErrorTag.JWT))).isFalse()
        assertThat(ErrorFilter.JWT.accepts(entry(ErrorSeverity.ERROR, StageErrorTag.JWT))).isTrue()
        assertThat(ErrorFilter.ABE.accepts(entry(ErrorSeverity.ERROR, StageErrorTag.ABE))).isTrue()
        assertThat(ErrorFilter.DECODE.accepts(entry(ErrorSeverity.WARNING, StageErrorTag.DECODE))).isTrue()
    }

    @Test fun `a stage facet rejects an untagged entry`() {
        assertThat(ErrorFilter.PKI.accepts(entry(ErrorSeverity.ERROR, stage = null))).isFalse()
    }
}
