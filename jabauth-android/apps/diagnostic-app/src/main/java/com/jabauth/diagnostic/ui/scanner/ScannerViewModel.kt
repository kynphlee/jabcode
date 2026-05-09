package com.jabauth.diagnostic.ui.scanner

import android.media.ImageReader
import androidx.lifecycle.ViewModel
import com.jabauth.jabcode.DecodeOptions
import com.jabauth.jabcode.DecodeResult
import com.jabauth.jabcode.JABCodeDecoderImpl
import com.jabauth.jabcode.camera.Camera2JABCodeAnalyzer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ScannerViewModel : ViewModel() {

    private val decoder = JABCodeDecoderImpl()

    private val _scanResult = MutableStateFlow<DecodeResult?>(null)
    val scanResult: StateFlow<DecodeResult?> = _scanResult.asStateFlow()

    private val _scanError = MutableStateFlow<String?>(null)
    val scanError: StateFlow<String?> = _scanError.asStateFlow()

    private val analyzer = Camera2JABCodeAnalyzer(
        decoder = decoder,
        options = DecodeOptions(
            timeout = 200L,
            analyzeIntervalMs = 500L
        ),
        onDecodeSuccess = { result ->
            _scanResult.value = result
            _scanError.value = null
        },
        onDecodeFailure = { error ->
            _scanError.value = error
        }
    )

    fun analyzeFrame(reader: ImageReader) {
        analyzer.analyze(reader)
    }

    override fun onCleared() {
        super.onCleared()
        // Cleanup if needed
    }
}
