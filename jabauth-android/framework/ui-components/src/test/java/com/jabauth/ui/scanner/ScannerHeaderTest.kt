package com.jabauth.ui.scanner

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Unit tests for Scanner Components
 * 
 * Tests scanner component logic and structure.
 * Full rendering tests require instrumented testing.
 * Coverage Target: 70%+
 */
class ScannerComponentsTest {
    
    @Test
    fun `ScanStatus enum has all states`() {
        val states = ScanStatus.values()
        assertThat(states).hasLength(4)
        assertThat(states).asList().containsExactly(
            ScanStatus.SCANNING,
            ScanStatus.SUCCESS,
            ScanStatus.ERROR,
            ScanStatus.WARNING
        )
    }
    
    @Test
    fun `ScanStatus SCANNING is first state`() {
        assertThat(ScanStatus.SCANNING.ordinal).isEqualTo(0)
    }
    
    @Test
    fun `ScanStatus SUCCESS exists`() {
        val status = ScanStatus.SUCCESS
        assertThat(status).isNotNull()
        assertThat(status.name).isEqualTo("SUCCESS")
    }
    
    @Test
    fun `ScanStatus ERROR exists`() {
        val status = ScanStatus.ERROR
        assertThat(status).isNotNull()
        assertThat(status.name).isEqualTo("ERROR")
    }
    
    @Test
    fun `ScanStatus WARNING exists`() {
        val status = ScanStatus.WARNING
        assertThat(status).isNotNull()
        assertThat(status.name).isEqualTo("WARNING")
    }
}
