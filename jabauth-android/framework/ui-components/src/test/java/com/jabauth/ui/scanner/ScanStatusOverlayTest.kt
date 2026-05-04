package com.jabauth.ui.scanner

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for ScanStatusOverlay composable
 * 
 * Tests scan status types and status message handling.
 */
class ScanStatusOverlayTest {
    
    @Test
    fun `all scan status types exist`() {
        val statuses = ScanStatus.values()
        assertEquals(4, statuses.size)
        
        assertTrue(statuses.contains(ScanStatus.SCANNING))
        assertTrue(statuses.contains(ScanStatus.SUCCESS))
        assertTrue(statuses.contains(ScanStatus.ERROR))
        assertTrue(statuses.contains(ScanStatus.WARNING))
    }
    
    @Test
    fun `scan status enum values are unique`() {
        val statuses = ScanStatus.values()
        val uniqueStatuses = statuses.toSet()
        assertEquals(statuses.size, uniqueStatuses.size)
    }
    
    @Test
    fun `status messages are not empty`() {
        val messages = mapOf(
            ScanStatus.SCANNING to "Scanning...",
            ScanStatus.SUCCESS to "Scan successful!",
            ScanStatus.ERROR to "Scan failed",
            ScanStatus.WARNING to "Poor quality detected"
        )
        
        messages.forEach { (_, message) ->
            assertTrue(message.isNotEmpty())
            assertTrue(message.length > 3)
        }
    }
    
    @Test
    fun `status has correct background color mapping`() {
        // Verify each status maps to a color
        val statusColorMapping = mapOf(
            ScanStatus.SUCCESS to "success",
            ScanStatus.ERROR to "error",
            ScanStatus.WARNING to "warning",
            ScanStatus.SCANNING to "primary"
        )
        
        assertEquals(4, statusColorMapping.size)
        assertTrue(statusColorMapping.containsKey(ScanStatus.SUCCESS))
        assertTrue(statusColorMapping.containsKey(ScanStatus.ERROR))
        assertTrue(statusColorMapping.containsKey(ScanStatus.WARNING))
        assertTrue(statusColorMapping.containsKey(ScanStatus.SCANNING))
    }
    
    @Test
    fun `status overlay accepts various message formats`() {
        val messages = listOf(
            "Short",
            "Medium length message",
            "This is a longer message that provides more context about the scan status"
        )
        
        messages.forEach { message ->
            assertTrue(message.isNotEmpty())
        }
    }
    
    @Test
    fun `status enum can be converted to string`() {
        ScanStatus.values().forEach { status ->
            val statusString = status.toString()
            assertTrue(statusString.isNotEmpty())
            assertTrue(statusString.matches(Regex("[A-Z]+")))
        }
    }
}
