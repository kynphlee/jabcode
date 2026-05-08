package com.jabauth.diagnostic.camera

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.jabauth.jabcode.camera.CameraUtils
import com.jabauth.jabcode.camera.ImageQualityAnalyzer

/**
 * Camera Image Analyzer
 * 
 * Analyzes camera frames to calculate quality metrics:
 * - Brightness: Average luminosity (0.0 = dark, 1.0 = bright)
 * - Focus: Edge sharpness via Laplacian variance (0.0 = blurry, 1.0 = sharp)
 * - Contrast: Standard deviation of pixel intensities (0.0 = flat, 1.0 = high contrast)
 * 
 * Updates callback on each frame with normalized [0.0-1.0] values.
 * 
 * @param onQualityUpdate Callback invoked with (brightness, focus, contrast)
 */
class CameraAnalyzer(
    private val onQualityUpdate: (brightness: Float, focus: Float, contrast: Float) -> Unit
) : ImageAnalysis.Analyzer {
    
    private val qualityAnalyzer = ImageQualityAnalyzer()
    
    override fun analyze(image: ImageProxy) {
        try {
            // Use framework utility for conversion
            val bitmap = CameraUtils.imageProxyToBitmap(image)
            if (bitmap == null) {
                image.close()
                return
            }
            
            try {
                // Use framework quality analyzer
                val metrics = qualityAnalyzer.analyze(bitmap)
                onQualityUpdate(metrics.brightness, metrics.focus, metrics.contrast)
            } finally {
                bitmap.recycle()
            }
        } finally {
            image.close()
        }
    }
}
