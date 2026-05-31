package com.jabauth.diagnostic.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jabauth.diagnostic.data.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for Settings screen
 * 
 * Manages app configuration and preferences with persistent storage
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    
    private val repository = SettingsRepository(application)
    
    val settings: StateFlow<SettingsRepository.Settings> = repository.settingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SettingsRepository.Settings()
        )
    
    fun updateDecodeTimeout(timeout: Int) {
        viewModelScope.launch {
            repository.updateDecodeTimeout(timeout)
        }
    }
    
    fun updateAnalyzeInterval(interval: Int) {
        viewModelScope.launch {
            repository.updateAnalyzeInterval(interval)
        }
    }
    
    fun updateAutoFocus(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateAutoFocus(enabled)
        }
    }
    
    fun updateDebugLogging(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateDebugLogging(enabled)
        }
    }
    
    fun updateColorMode(mode: Int?) {
        viewModelScope.launch {
            repository.updatePreferredColorMode(mode)
        }
    }

    fun updateHapticFeedback(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateHapticFeedback(enabled)
        }
    }

    fun resetToDefaults() {
        viewModelScope.launch {
            repository.resetToDefaults()
        }
    }
}
