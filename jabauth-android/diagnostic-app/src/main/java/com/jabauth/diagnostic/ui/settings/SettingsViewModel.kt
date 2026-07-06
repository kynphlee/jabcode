package com.jabauth.diagnostic.ui.settings

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jabauth.diagnostic.data.SettingsRepository
import com.jabauth.diagnostic.data.TrustAnchorRepository
import com.jabauth.diagnostic.verify.OfflineTrustPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel for Settings screen
 *
 * Manages app configuration and preferences with persistent storage
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SettingsRepository(application)
    private val trustAnchors = TrustAnchorRepository.get(application)

    /** The persisted trust-anchor count for the Settings "Trust Store" row (A′ Stage 3). */
    val anchorCount: StateFlow<Int> =
        trustAnchors.countFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _importMessage = MutableStateFlow<String?>(null)
    val importMessage: StateFlow<String?> = _importMessage.asStateFlow()

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

    fun updateMotionTelemetry(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateMotionTelemetry(enabled)
        }
    }

    fun updateMotionThrottling(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateMotionThrottling(enabled)
        }
    }

    // Verification group (Flow C)
    fun updateRevocationCheck(enabled: Boolean) { viewModelScope.launch { repository.updateRevocationCheck(enabled) } }
    fun updateOfflineHardFail(hardFail: Boolean) { viewModelScope.launch { repository.updateOfflineHardFail(hardFail) } }
    fun updateDefaultCoaProfile(profile: String) { viewModelScope.launch { repository.updateDefaultCoaProfile(profile) } }
    fun updateVerifierAttributes(attrs: Set<String>) { viewModelScope.launch { repository.updateVerifierAttributes(attrs) } }
    fun updateOfflineTrustPolicy(policy: OfflineTrustPolicy) { viewModelScope.launch { repository.updateOfflineTrustPolicy(policy) } }

    fun resetToDefaults() {
        viewModelScope.launch {
            repository.resetToDefaults()
        }
    }

    /** Import a trust anchor from a picked certificate file (DER/PEM) into the shared, persistent store. */
    fun importTrustAnchor(uri: Uri) {
        viewModelScope.launch {
            val bytes = withContext(Dispatchers.IO) {
                runCatching {
                    getApplication<Application>().contentResolver.openInputStream(uri)?.use { it.readBytes() }
                }.getOrNull()
            }
            val cert = bytes?.let { trustAnchors.importFrom(it) }
            _importMessage.value = if (cert != null) {
                "Imported anchor · ${cert.subjectX500Principal.name}"
            } else {
                "Not a certificate — expected a DER/PEM .crt/.cer/.pem file"
            }
        }
    }

    fun clearImportMessage() { _importMessage.value = null }
}
