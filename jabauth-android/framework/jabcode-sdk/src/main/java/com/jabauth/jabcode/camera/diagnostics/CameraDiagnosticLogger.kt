package com.jabauth.jabcode.camera.diagnostics

import android.hardware.camera2.CaptureResult
import android.util.Log
import com.jabauth.jabcode.camera.metadata.FrameMetadata
import com.jabauth.jabcode.camera.metadata.MetadataExtractor
import kotlin.math.abs

/**
 * Phase 2 - Option C: Camera configuration diagnostics for optimal screen display tuning
 * 
 * Logs camera parameters (exposure time, ISO, FPS) to enable statistical analysis
 * for computing optimal settings to minimize screen refresh interference.
 * 
 * **Key Metrics:**
 * - Exposure time (target: 1/30 sec = 33.3ms to average 2-4 refresh cycles at 60Hz/120Hz)
 * - ISO sensitivity (monitor for noise in low light)
 * - Frame rate (detect rolling shutter interference patterns)
 * - AE state convergence (stability indicator)
 * 
 * **Usage:**
 * ```kotlin
 * val diagnosticLogger = CameraDiagnosticLogger()
 * 
 * // In capture callback:
 * override fun onCaptureCompleted(result: CaptureResult) {
 *     diagnosticLogger.logFrame(result)
 * }
 * 
 * // After test session:
 * diagnosticLogger.printStatistics()
 * diagnosticLogger.computeOptimalSettings()
 * ```
 */
class CameraDiagnosticLogger {
    
    companion object {
        private const val TAG = "CameraDiagnostics"
        
        // Target values for screen display compatibility
        private const val TARGET_EXPOSURE_NS = 33_333_333L  // 1/30 sec (averages 2 refresh cycles at 60Hz)
        private const val TARGET_FPS = 30  // Reduces rolling shutter effects
        private const val SCREEN_REFRESH_60HZ_NS = 16_666_667L  // 1/60 sec
        private const val SCREEN_REFRESH_120HZ_NS = 8_333_333L  // 1/120 sec
    }
    
    private val metadataExtractor = MetadataExtractor()
    
    // Statistics accumulators
    private val exposureTimes = mutableListOf<Long>()
    private val isoValues = mutableListOf<Int>()
    private val frameIntervals = mutableListOf<Long>()
    private var lastFrameTimestamp = 0L
    private var frameCount = 0
    
    // AE convergence tracking
    private var aeConvergedFrames = 0
    private var aeSearchingFrames = 0
    
    /**
     * Log a single frame's camera parameters
     * Call this from CaptureResult callback
     */
    fun logFrame(result: CaptureResult) {
        frameCount++
        val metadata = metadataExtractor.extract(result)
        
        // Collect exposure time
        metadata.exposureTimeNs?.let { exposureNs ->
            exposureTimes.add(exposureNs)
            
            // Log Phase 2 diagnostic with delta from target
            val exposureMs = exposureNs / 1_000_000.0
            val targetMs = TARGET_EXPOSURE_NS / 1_000_000.0
            val deltaPct = ((exposureNs - TARGET_EXPOSURE_NS) * 100.0 / TARGET_EXPOSURE_NS).toInt()
            
            Log.i(TAG, "Frame $frameCount: EXPOSURE=${String.format("%.2f", exposureMs)}ms " +
                       "(target=${String.format("%.2f", targetMs)}ms, delta=${deltaPct}%)")
        }
        
        // Collect ISO
        metadata.iso?.let { iso ->
            isoValues.add(iso)
            Log.i(TAG, "Frame $frameCount: ISO=$iso")
        }
        
        // Calculate FPS from frame interval
        if (lastFrameTimestamp > 0) {
            val intervalNs = metadata.timestamp - lastFrameTimestamp
            frameIntervals.add(intervalNs)
            
            val intervalMs = intervalNs / 1_000_000.0
            val fps = if (intervalNs > 0) 1_000_000_000.0 / intervalNs else 0.0
            
            Log.i(TAG, "Frame $frameCount: FPS=${String.format("%.1f", fps)} " +
                       "(interval=${String.format("%.1f", intervalMs)}ms)")
        }
        lastFrameTimestamp = metadata.timestamp
        
        // Track AE convergence
        when (metadata.aeState) {
            FrameMetadata.AeState.CONVERGED -> {
                aeConvergedFrames++
                Log.v(TAG, "Frame $frameCount: AE_STATE=CONVERGED")
            }
            FrameMetadata.AeState.SEARCHING -> {
                aeSearchingFrames++
                Log.v(TAG, "Frame $frameCount: AE_STATE=SEARCHING")
            }
            else -> {
                Log.v(TAG, "Frame $frameCount: AE_STATE=${metadata.aeState}")
            }
        }
    }
    
    /**
     * Print statistical summary of collected camera parameters
     */
    fun printStatistics() {
        Log.i(TAG, "===== CAMERA DIAGNOSTICS STATISTICS =====")
        Log.i(TAG, "Total frames analyzed: $frameCount")
        
        // Exposure time statistics
        if (exposureTimes.isNotEmpty()) {
            val avgExposureNs = exposureTimes.average().toLong()
            val minExposureNs = exposureTimes.minOrNull() ?: 0
            val maxExposureNs = exposureTimes.maxOrNull() ?: 0
            
            val avgMs = avgExposureNs / 1_000_000.0
            val minMs = minExposureNs / 1_000_000.0
            val maxMs = maxExposureNs / 1_000_000.0
            
            Log.i(TAG, "Exposure Time:")
            Log.i(TAG, "  Average: ${String.format("%.2f", avgMs)}ms")
            Log.i(TAG, "  Min: ${String.format("%.2f", minMs)}ms")
            Log.i(TAG, "  Max: ${String.format("%.2f", maxMs)}ms")
            Log.i(TAG, "  Target: ${String.format("%.2f", TARGET_EXPOSURE_NS / 1_000_000.0)}ms")
        }
        
        // ISO statistics
        if (isoValues.isNotEmpty()) {
            val avgIso = isoValues.average().toInt()
            val minIso = isoValues.minOrNull() ?: 0
            val maxIso = isoValues.maxOrNull() ?: 0
            
            Log.i(TAG, "ISO Sensitivity:")
            Log.i(TAG, "  Average: $avgIso")
            Log.i(TAG, "  Min: $minIso")
            Log.i(TAG, "  Max: $maxIso")
        }
        
        // FPS statistics
        if (frameIntervals.isNotEmpty()) {
            val avgIntervalNs = frameIntervals.average().toLong()
            val avgFps = if (avgIntervalNs > 0) 1_000_000_000.0 / avgIntervalNs else 0.0
            
            Log.i(TAG, "Frame Rate:")
            Log.i(TAG, "  Average FPS: ${String.format("%.1f", avgFps)}")
            Log.i(TAG, "  Target FPS: $TARGET_FPS")
        }
        
        // AE convergence
        val convergenceRate = if (frameCount > 0) 
            (aeConvergedFrames * 100.0 / frameCount) else 0.0
        
        Log.i(TAG, "Auto-Exposure:")
        Log.i(TAG, "  Converged frames: $aeConvergedFrames (${String.format("%.1f", convergenceRate)}%)")
        Log.i(TAG, "  Searching frames: $aeSearchingFrames")
        
        Log.i(TAG, "=========================================")
    }
    
    /**
     * Compute optimal camera settings based on collected statistics
     * Provides recommended capture request parameters for screen display compatibility
     */
    fun computeOptimalSettings() {
        if (exposureTimes.isEmpty() && frameIntervals.isEmpty()) {
            Log.w(TAG, "No data collected - cannot compute optimal settings")
            return
        }
        
        Log.i(TAG, "===== OPTIMAL SETTINGS RECOMMENDATION =====")
        
        // Exposure time recommendation
        if (exposureTimes.isNotEmpty()) {
            val avgExposureNs = exposureTimes.average().toLong()
            val deltaFromTarget = abs(avgExposureNs - TARGET_EXPOSURE_NS)
            val deltaMs = deltaFromTarget / 1_000_000.0
            
            if (avgExposureNs < TARGET_EXPOSURE_NS * 0.8) {
                Log.i(TAG, "⚠️  EXPOSURE TOO LOW")
                Log.i(TAG, "   Current avg: ${String.format("%.2f", avgExposureNs / 1_000_000.0)}ms")
                Log.i(TAG, "   Recommendation: Increase to ${String.format("%.2f", TARGET_EXPOSURE_NS / 1_000_000.0)}ms")
                Log.i(TAG, "   Delta: +${String.format("%.2f", deltaMs)}ms needed")
                Log.i(TAG, "   Action: Set SENSOR_EXPOSURE_TIME = ${TARGET_EXPOSURE_NS}ns")
            } else if (avgExposureNs > TARGET_EXPOSURE_NS * 1.2) {
                Log.i(TAG, "⚠️  EXPOSURE TOO HIGH")
                Log.i(TAG, "   Current avg: ${String.format("%.2f", avgExposureNs / 1_000_000.0)}ms")
                Log.i(TAG, "   Recommendation: Decrease to ${String.format("%.2f", TARGET_EXPOSURE_NS / 1_000_000.0)}ms")
                Log.i(TAG, "   Delta: -${String.format("%.2f", deltaMs)}ms needed")
                Log.i(TAG, "   Action: Set SENSOR_EXPOSURE_TIME = ${TARGET_EXPOSURE_NS}ns")
            } else {
                Log.i(TAG, "✅ EXPOSURE OPTIMAL")
                Log.i(TAG, "   Current avg: ${String.format("%.2f", avgExposureNs / 1_000_000.0)}ms")
                Log.i(TAG, "   Target: ${String.format("%.2f", TARGET_EXPOSURE_NS / 1_000_000.0)}ms")
            }
        }
        
        // FPS recommendation
        if (frameIntervals.isNotEmpty()) {
            val avgIntervalNs = frameIntervals.average().toLong()
            val avgFps = if (avgIntervalNs > 0) 1_000_000_000.0 / avgIntervalNs else 0.0
            
            if (avgFps > TARGET_FPS * 1.2) {
                Log.i(TAG, "⚠️  FRAME RATE TOO HIGH")
                Log.i(TAG, "   Current: ${String.format("%.1f", avgFps)} FPS")
                Log.i(TAG, "   Recommendation: Reduce to $TARGET_FPS FPS")
                Log.i(TAG, "   Action: Set AE_TARGET_FPS_RANGE = Range($TARGET_FPS, $TARGET_FPS)")
                Log.i(TAG, "   Benefit: Reduces rolling shutter artifacts")
            } else if (avgFps < TARGET_FPS * 0.8) {
                Log.i(TAG, "⚠️  FRAME RATE TOO LOW")
                Log.i(TAG, "   Current: ${String.format("%.1f", avgFps)} FPS")
                Log.i(TAG, "   Recommendation: Increase to $TARGET_FPS FPS")
                Log.i(TAG, "   Action: Set AE_TARGET_FPS_RANGE = Range($TARGET_FPS, $TARGET_FPS)")
            } else {
                Log.i(TAG, "✅ FRAME RATE OPTIMAL")
                Log.i(TAG, "   Current: ${String.format("%.1f", avgFps)} FPS")
                Log.i(TAG, "   Target: $TARGET_FPS FPS")
            }
        }
        
        // Screen refresh interference analysis
        if (exposureTimes.isNotEmpty()) {
            val avgExposureNs = exposureTimes.average().toLong()
            val refreshCycles60Hz = avgExposureNs / SCREEN_REFRESH_60HZ_NS
            val refreshCycles120Hz = avgExposureNs / SCREEN_REFRESH_120HZ_NS
            
            Log.i(TAG, "Screen Refresh Analysis:")
            Log.i(TAG, "  Exposure averages $refreshCycles60Hz refresh cycles @ 60Hz")
            Log.i(TAG, "  Exposure averages $refreshCycles120Hz refresh cycles @ 120Hz")
            
            if (refreshCycles60Hz >= 2) {
                Log.i(TAG, "  ✅ Should smooth out 60Hz artifacts")
            } else {
                Log.i(TAG, "  ⚠️  May see 60Hz scan line artifacts")
            }
        }
        
        Log.i(TAG, "===========================================")
    }
    
    /**
     * Reset all collected statistics
     */
    fun reset() {
        exposureTimes.clear()
        isoValues.clear()
        frameIntervals.clear()
        lastFrameTimestamp = 0L
        frameCount = 0
        aeConvergedFrames = 0
        aeSearchingFrames = 0
    }
}
