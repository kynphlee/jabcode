package com.jabauth.diagnostic.ui.settings

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ViewModel for Settings screen
 * 
 * Manages app configuration and preferences
 */
class SettingsViewModel : ViewModel() {
    
    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()
    
    fun updateDecodeTimeout(timeout: Long) {
        _settings.value = _settings.value.copy(decodeTimeout = timeout)
    }
    
    fun updateAnalyzeInterval(interval: Long) {
        _settings.value = _settings.value.copy(analyzeInterval = interval)
    }
    
    fun updateAutoFocus(enabled: Boolean) {
        _settings.value = _settings.value.copy(autoFocusEnabled = enabled)
    }
    
    fun updateDebugLogging(enabled: Boolean) {
        _settings.value = _settings.value.copy(debugLoggingEnabled = enabled)
    }
    
    fun updateColorMode(mode: Int) {
        _settings.value = _settings.value.copy(preferredColorMode = mode)
    }
    
    fun resetToDefaults() {
        _settings.value = AppSettings()
    }
}

/**
 * Application settings data class
 */
data class AppSettings(
    val decodeTimeout: Long = 200L,
    val analyzeInterval: Long = 500L,
    val autoFocusEnabled: Boolean = true,
    val debugLoggingEnabled: Boolean = false,
    val preferredColorMode: Int? = null // null = auto-detect
)
