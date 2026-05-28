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

    // Tier-1 HUD state — exposed to the UI as Compose-friendly StateFlows.
    // The Camera2Preview callbacks push values in via the setters below.
    private val _currentZoom = MutableStateFlow(1.0f)
    val currentZoom: StateFlow<Float> = _currentZoom.asStateFlow()

    private val _llbSupported = MutableStateFlow(false)
    val llbSupported: StateFlow<Boolean> = _llbSupported.asStateFlow()

    private val _llbState = MutableStateFlow(-1)  // -1 unknown, 0 inactive, 1 active
    val llbState: StateFlow<Int> = _llbState.asStateFlow()

    // Rolling 30-second stats: a list of (timestampMs, isSuccess) pairs,
    // pruned on read to the last 30 seconds. Cheap data structure since
    // the volume is bounded by ~10-20 attempts/sec at most.
    private data class AttemptRecord(val timestampMs: Long, val isSuccess: Boolean)
    private val attemptLog = mutableListOf<AttemptRecord>()
    private val attemptWindowMs = 30_000L
    private val _recentStats = MutableStateFlow(ScanStats(0, 0))
    val recentStats: StateFlow<ScanStats> = _recentStats.asStateFlow()

    // History of last 5 successful decodes, newest first.
    private val _decodeHistory = MutableStateFlow<List<DecodeResult>>(emptyList())
    val decodeHistory: StateFlow<List<DecodeResult>> = _decodeHistory.asStateFlow()
    private val historyMaxSize = 5

    // Expose settings for UI consumption (auto-focus, color mode, etc.)
    val settings = settingsRepository.settingsFlow

    // --- Tier-1 HUD setters (called from ScannerScreen via Camera2Preview callbacks) ---
    fun onZoomChanged(zoomRatio: Float) {
        _currentZoom.value = zoomRatio
    }

    fun onLowLightBoostSupported(supported: Boolean) {
        _llbSupported.value = supported
    }

    fun onLowLightBoostStateChanged(state: Int) {
        _llbState.value = state
    }

    private fun recordAttempt(isSuccess: Boolean) {
        val now = System.currentTimeMillis()
        attemptLog.add(AttemptRecord(now, isSuccess))
        // Prune outside the rolling window
        val cutoff = now - attemptWindowMs
        attemptLog.removeAll { it.timestampMs < cutoff }
        val ok = attemptLog.count { it.isSuccess }
        val fail = attemptLog.size - ok
        _recentStats.value = ScanStats(okCount = ok, failCount = fail)
    }

    data class ScanStats(val okCount: Int, val failCount: Int) {
        val total: Int get() = okCount + failCount
        val successRate: Float get() = if (total == 0) 0f else okCount.toFloat() / total
    }
    
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
        Log.i("ScannerViewModel", "🔧 Creating new Camera2JABCodeAnalyzer")
        Log.i("ScannerViewModel", "   - Timeout: ${timeout}ms")
        Log.i("ScannerViewModel", "   - Analyze interval: ${analyzeInterval}ms")
        Log.i("ScannerViewModel", "   - Preferred color mode: ${preferredColorMode?.let { "$it-color" } ?: "auto-detect"}")
        Log.i("ScannerViewModel", "   - Debug logging: $isDebugEnabled")
        
        logger.iSync("Creating analyzer: timeout=${timeout}ms, interval=${analyzeInterval}ms", isDebugEnabled)
        
        return Camera2JABCodeAnalyzer(
            decoder = decoder,
            options = DecodeOptions(
                timeout = timeout,
                analyzeIntervalMs = analyzeInterval
            ),
            onDecodeSuccess = { result ->
                val decodedColorValue = result.colorMode.value
                Log.i("ScannerViewModel", "✅ DECODE SUCCESS!")
                Log.i("ScannerViewModel", "   - Data: '${result.asString()}'")
                Log.i("ScannerViewModel", "   - Color mode: ${result.colorMode} (${decodedColorValue} colors)")
                Log.i("ScannerViewModel", "   - Decode time: ${result.decodeTimeMs}ms")
                
                logger.dSync("Decode SUCCESS: data='${result.asString()}', colorMode=${result.colorMode}, decodeTime=${result.decodeTimeMs}ms", isDebugEnabled)
                
                // Validate against preferred color mode if set
                preferredColorMode?.let { preferred ->
                    if (decodedColorValue != preferred) {
                        Log.w("ScannerViewModel", "⚠️ Color mode mismatch: expected ${preferred}-color, decoded ${decodedColorValue}-color")
                        logger.dSync(
                            "Color mode mismatch: expected ${preferred}-color, decoded ${decodedColorValue}-color (auto-detect found different mode)",
                            isDebugEnabled
                        )
                    } else {
                        Log.d("ScannerViewModel", "✅ Color mode validated: ${decodedColorValue}-color matches preference")
                        logger.dSync("Color mode validated: ${decodedColorValue}-color matches preference", isDebugEnabled)
                    }
                }
                
                _scanResult.value = result
                _scanError.value = null
                _scanCount.value++
                // Tier-1 HUD: record success + prepend to history (last 5).
                recordAttempt(isSuccess = true)
                val updated = (listOf(result) + _decodeHistory.value).take(historyMaxSize)
                _decodeHistory.value = updated
            },
            onDecodeFailure = { error ->
                Log.e("ScannerViewModel", "❌ Decode FAILURE: $error")
                logger.dSync("Decode FAILURE: $error", isDebugEnabled)
                _scanError.value = error
                recordAttempt(isSuccess = false)
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
