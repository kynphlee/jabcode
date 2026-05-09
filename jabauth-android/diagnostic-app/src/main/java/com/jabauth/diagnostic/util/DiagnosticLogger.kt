package com.jabauth.diagnostic.util

import android.util.Log
import com.jabauth.diagnostic.data.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Diagnostic Logger - Conditional logging based on user settings
 * 
 * Provides Android Log wrapper that respects debug logging preference.
 * When debug logging is disabled, only error logs are emitted.
 */
class DiagnosticLogger(
    private val tag: String,
    private val settingsRepository: SettingsRepository
) {
    
    /**
     * Flow indicating if debug logging is enabled
     */
    val isDebugEnabled: Flow<Boolean> = settingsRepository.settingsFlow
        .map { it.debugLogging }
    
    /**
     * Log debug message (only if debug logging enabled)
     */
    suspend fun d(message: String, throwable: Throwable? = null) {
        settingsRepository.settingsFlow.collect { settings ->
            if (settings.debugLogging) {
                if (throwable != null) {
                    Log.d(tag, message, throwable)
                } else {
                    Log.d(tag, message)
                }
            }
            return@collect // Only check once
        }
    }
    
    /**
     * Log info message (only if debug logging enabled)
     */
    suspend fun i(message: String, throwable: Throwable? = null) {
        settingsRepository.settingsFlow.collect { settings ->
            if (settings.debugLogging) {
                if (throwable != null) {
                    Log.i(tag, message, throwable)
                } else {
                    Log.i(tag, message)
                }
            }
            return@collect
        }
    }
    
    /**
     * Log warning message (only if debug logging enabled)
     */
    suspend fun w(message: String, throwable: Throwable? = null) {
        settingsRepository.settingsFlow.collect { settings ->
            if (settings.debugLogging) {
                if (throwable != null) {
                    Log.w(tag, message, throwable)
                } else {
                    Log.w(tag, message)
                }
            }
            return@collect
        }
    }
    
    /**
     * Log error message (ALWAYS logged, regardless of settings)
     */
    fun e(message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e(tag, message, throwable)
        } else {
            Log.e(tag, message)
        }
    }
    
    /**
     * Synchronous debug log (checks setting immediately)
     * Use sparingly - prefer suspend d() for Flow-based check
     */
    fun dSync(message: String, isEnabled: Boolean) {
        if (isEnabled) {
            Log.d(tag, message)
        }
    }
    
    /**
     * Synchronous info log (checks setting immediately)
     * Use sparingly - prefer suspend i() for Flow-based check
     */
    fun iSync(message: String, isEnabled: Boolean) {
        if (isEnabled) {
            Log.i(tag, message)
        }
    }
    
    companion object {
        /**
         * Create logger for given tag
         */
        fun create(tag: String, settingsRepository: SettingsRepository): DiagnosticLogger {
            return DiagnosticLogger(tag, settingsRepository)
        }
    }
}
