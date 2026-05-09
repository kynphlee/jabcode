package com.jabauth.diagnostic.ui.scanner

import android.app.Application
import android.media.ImageReader
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jabauth.diagnostic.data.SettingsRepository
import com.jabauth.diagnostic.util.DiagnosticLogger
import com.jabauth.jabcode.DecodeOptions
import com.jabauth.jabcode.DecodeResult
import com.jabauth.jabcode.JABCodeDecoderImpl
import com.jabauth.jabcode.camera.Camera2JABCodeAnalyzer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ScannerViewModel(application: Application) : AndroidViewModel(application) {
    
    private val decoder = JABCodeDecoderImpl()
    private val settingsRepository = SettingsRepository(application)
    private val logger = DiagnosticLogger.create("ScannerViewModel", settingsRepository)
    
    private val _scanResult = MutableStateFlow<DecodeResult?>(null)
    val scanResult: StateFlow<DecodeResult?> = _scanResult.asStateFlow()
    
    private val _scanError = MutableStateFlow<String?>(null)
    val scanError: StateFlow<String?> = _scanError.asStateFlow()
    
    private val _scanCount = MutableStateFlow(0)
    val scanCount: StateFlow<Int> = _scanCount.asStateFlow()
    
    // Expose settings for UI consumption (auto-focus, color mode, etc.)
    val settings = settingsRepository.settingsFlow
    
    // Track debug logging state for synchronous logging
    private var isDebugEnabled = false
    
    // Track preferred color mode for result validation
    private var preferredColorMode: Int? = null
    
    // Mutable analyzer - recreated when settings change
    private var analyzer: Camera2JABCodeAnalyzer
    
    init {
        // Initialize analyzer with default settings
        analyzer = createAnalyzer(
            timeout = SettingsRepository.DEFAULT_DECODE_TIMEOUT.toLong(),
            analyzeInterval = SettingsRepository.DEFAULT_ANALYZE_INTERVAL.toLong()
        )
        
        // Observe settings changes and recreate analyzer
        viewModelScope.launch {
            settingsRepository.settingsFlow.collect { settings ->
                isDebugEnabled = settings.debugLogging
                preferredColorMode = settings.preferredColorMode
                
                val colorModeStr = settings.preferredColorMode?.let { "${it}-color" } ?: "auto-detect"
                logger.dSync(
                    "Settings updated: timeout=${settings.decodeTimeout}ms, interval=${settings.analyzeInterval}ms, " +
                    "autoFocus=${settings.autoFocus}, colorMode=$colorModeStr, debug=${settings.debugLogging}", 
                    isDebugEnabled
                )
                
                analyzer = createAnalyzer(
                    timeout = settings.decodeTimeout.toLong(),
                    analyzeInterval = settings.analyzeInterval.toLong()
                )
            }
        }
    }
    
    private fun createAnalyzer(timeout: Long, analyzeInterval: Long): Camera2JABCodeAnalyzer {
        logger.iSync("Creating analyzer: timeout=${timeout}ms, interval=${analyzeInterval}ms", isDebugEnabled)
        
        return Camera2JABCodeAnalyzer(
            decoder = decoder,
            options = DecodeOptions(
                timeout = timeout,
                analyzeIntervalMs = analyzeInterval
            ),
            onDecodeSuccess = { result ->
                val decodedColorValue = result.colorMode.value
                logger.dSync("Decode SUCCESS: data='${result.asString()}', colorMode=${result.colorMode}, decodeTime=${result.decodeTimeMs}ms", isDebugEnabled)
                
                // Validate against preferred color mode if set
                preferredColorMode?.let { preferred ->
                    if (decodedColorValue != preferred) {
                        logger.dSync(
                            "Color mode mismatch: expected ${preferred}-color, decoded ${decodedColorValue}-color (auto-detect found different mode)",
                            isDebugEnabled
                        )
                    } else {
                        logger.dSync("Color mode validated: ${decodedColorValue}-color matches preference", isDebugEnabled)
                    }
                }
                
                _scanResult.value = result
                _scanError.value = null
                _scanCount.value++
            },
            onDecodeFailure = { error ->
                logger.dSync("Decode FAILURE: $error", isDebugEnabled)
                _scanError.value = error
            }
        )
    }
    
    fun analyzeFrame(reader: ImageReader) {
        analyzer.analyze(reader)
    }
    
    override fun onCleared() {
        super.onCleared()
        // Cleanup if needed
    }
}
