package com.jabauth.core.logging

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for Logger interface contract
 *
 * Uses TestLoggerImpl to verify logging behavior without Android dependencies.
 * Production LoggerImpl will be tested via instrumented tests.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LoggerTest {

    private lateinit var context: Context
    private lateinit var logger: TestLoggerImpl

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        logger = TestLoggerImpl()
    }

    @Test
    fun `debug logs message at DEBUG level`() {
        logger.debug("Debug message")

        val logs = logger.getLogs()
        assertEquals(1, logs.size)
        assertEquals(LogLevel.DEBUG, logs[0].level)
        assertEquals("Debug message", logs[0].message)
        assertEquals(null, logs[0].throwable)
        assertEquals(null, logs[0].metadata)
    }

    @Test
    fun `debug logs message with metadata`() {
        val metadata = mapOf("key1" to "value1", "key2" to 42)
        logger.debug("Debug with metadata", metadata)

        val logs = logger.getLogs()
        assertEquals(1, logs.size)
        assertEquals(metadata, logs[0].metadata)
    }

    @Test
    fun `info logs message at INFO level`() {
        logger.info("Info message")

        val logs = logger.getLogs()
        assertEquals(1, logs.size)
        assertEquals(LogLevel.INFO, logs[0].level)
        assertEquals("Info message", logs[0].message)
    }

    @Test
    fun `warn logs message at WARN level`() {
        logger.warn("Warning message")

        val logs = logger.getLogs()
        assertEquals(1, logs.size)
        assertEquals(LogLevel.WARN, logs[0].level)
        assertEquals("Warning message", logs[0].message)
    }

    @Test
    fun `error logs message at ERROR level`() {
        logger.error("Error message")

        val logs = logger.getLogs()
        assertEquals(1, logs.size)
        assertEquals(LogLevel.ERROR, logs[0].level)
        assertEquals("Error message", logs[0].message)
    }

    @Test
    fun `error logs message with throwable`() {
        val exception = RuntimeException("Test exception")
        logger.error("Error with exception", exception)

        val logs = logger.getLogs()
        assertEquals(1, logs.size)
        assertEquals(exception, logs[0].throwable)
    }

    @Test
    fun `error logs message with throwable and metadata`() {
        val exception = RuntimeException("Test exception")
        val metadata = mapOf("errorCode" to 500)
        logger.error("Error with exception and metadata", exception, metadata)

        val logs = logger.getLogs()
        assertEquals(1, logs.size)
        assertEquals(exception, logs[0].throwable)
        assertEquals(metadata, logs[0].metadata)
    }

    @Test
    fun `withTag returns tagged logger instance`() {
        val taggedLogger = logger.withTag("TestTag")

        assertNotNull(taggedLogger)
        assertTrue(taggedLogger is TestLoggerImpl)
    }

    @Test
    fun `withTag prefixes messages with tag`() {
        val taggedLogger = logger.withTag("TestTag") as TestLoggerImpl
        taggedLogger.info("Tagged message")

        val logs = taggedLogger.getLogs()
        assertEquals(1, logs.size)
        assertEquals("TestTag", logs[0].tag)
        assertEquals("Tagged message", logs[0].message)
    }

    @Test
    fun `multiple log calls accumulate messages`() {
        logger.debug("Message 1")
        logger.info("Message 2")
        logger.warn("Message 3")
        logger.error("Message 4")

        val logs = logger.getLogs()
        assertEquals(4, logs.size)
        assertEquals("Message 1", logs[0].message)
        assertEquals("Message 2", logs[1].message)
        assertEquals("Message 3", logs[2].message)
        assertEquals("Message 4", logs[3].message)
    }

    @Test
    fun `isDebugEnabled returns true by default`() {
        assertTrue(logger.isDebugEnabled())
    }

    @Test
    fun `isDebugEnabled respects configuration`() {
        val disabledLogger = TestLoggerImpl(debugEnabled = false)
        assertFalse(disabledLogger.isDebugEnabled())
    }

    @Test
    fun `clear removes all logged messages`() {
        logger.debug("Message 1")
        logger.info("Message 2")
        
        assertEquals(2, logger.getLogs().size)
        
        logger.clear()
        
        assertEquals(0, logger.getLogs().size)
    }
}
