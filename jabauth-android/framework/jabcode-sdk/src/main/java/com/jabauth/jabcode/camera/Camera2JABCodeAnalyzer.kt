package com.jabauth.jabcode.camera

import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.Image
import android.media.ImageReader
import android.util.Log
import com.jabauth.jabcode.DecodeOptions
import com.jabauth.jabcode.DecodeResult
import com.jabauth.jabcode.JABCodeDecoder

/**
 * Decode region-of-interest. Expressed in the preview **view's** normalized
 * coordinate space (0..1, origin top-left) — i.e. the same space the on-screen
 * reticle lives in — plus the geometry needed to map it onto the
 * sensor-orientation analysis bitmap:
 *
 *  - [viewAspect]           preview view width / height (e.g. 1080/2340)
 *  - [frameRotationDegrees] clockwise degrees that bring the analysis frame
 *    into the view's CURRENT orientation
 *
 * The analyzer rotates the landscape analysis frame by [frameRotationDegrees]
 * to display orientation, centre-crops it to [viewAspect] (the preview is
 * FILL_CENTER, so that centred crop **is** what the user sees), then crops to
 * the normalized rect — making the reticle map 1:1 onto the decoder input.
 *
 * [frameRotationDegrees] is *not* `SENSOR_ORIENTATION`. Sensor orientation
 * alone is only correct while the view is in the device's natural (portrait)
 * orientation; once the scanner may rotate, the display's own rotation has to
 * be folded in. The caller supplies the already-combined value — see
 * `com.jabauth.ui.scanner.relativeFrameRotation`.
 */
data class RoiSpec(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val viewAspect: Float,
    val frameRotationDegrees: Int
)

/**
 * Camera2 analyzer for JABCode scanning
 * 
 * Provides low-level Camera2 integration with:
 * - ImageReader frame processing
 * - Frame throttling (prevents CPU overload)
 * - Automatic YUV→Bitmap conversion
 * - Optional quality metrics calculation
 * - JABCode decoding with timeout
 * - Result and error callbacks
 * 
 * **Usage:**
 * ```kotlin
 * val analyzer = Camera2JABCodeAnalyzer(
 *     decoder = jabCodeDecoder,
 *     options = DecodeOptions(timeout = 200L, analyzeIntervalMs = 500L),
 *     onDecodeSuccess = { result -> handleSuccess(result) },
 *     onDecodeFailure = { error -> handleError(error) },
 *     onQualityUpdate = { metrics -> updateUI(metrics) }
 * )
 * 
 * // Pass to Camera2Preview or ImageReader
 * imageReader.setOnImageAvailableListener({ reader ->
 *     analyzer.analyze(reader)
 * }, handler)
 * ```
 * 
 * @param decoder JABCode decoder instance
 * @param options Decode options (includes throttling and timeout)
 * @param onDecodeSuccess Callback when JABCode successfully decoded
 * @param onDecodeFailure Callback when decode error occurs
 * @param onQualityUpdate Optional callback for quality metrics (null = skip metrics)
 */
class Camera2JABCodeAnalyzer(
    private val decoder: JABCodeDecoder,
    private val options: DecodeOptions = DecodeOptions(),
    private val onDecodeSuccess: (DecodeResult) -> Unit,
    private val onDecodeFailure: (String, Long) -> Unit,
    private val onQualityUpdate: ((ImageQualityAnalyzer.QualityMetrics) -> Unit)? = null,
    /**
     * Supplies the current decode region-of-interest, or null to decode the
     * full frame. Queried once per analyzed frame so the reticle can move/resize
     * live without recreating the analyzer. See [RoiSpec] and [cropToRoi].
     */
    private val roiProvider: (() -> RoiSpec?)? = null,
    /**
     * Anti-fabrication consensus for nc2 (COLOR_8). nc2 is the ONLY mode that can
     * decode via the default-mode fall-through (it IS the default mode), so it is
     * the only mode where a degraded non-nc2 code can fabricate a payload. A
     * genuine nc2 reproduces the same bytes across frames; a fabrication does not.
     * An nc2 decode is withheld until [nc2ConsensusMinAgreement] frames within
     * [nc2ConsensusWindowMs] agree byte-identically; confirmed (real-Part-I)
     * modes are reported immediately. Default M=1 DISABLES the gate (preserves
     * single-frame behaviour for SDK consumers/tests); the diagnostic app opts in
     * with M>=2. See docs/cassandra-register/H_nc2_decode_failure.md (2026-06-11).
     */
    private val nc2ConsensusMinAgreement: Int = 1,
    private val nc2ConsensusWindowMs: Long = 1500L,
    /**
     * Converts an acquired [Image] to a decodable [Bitmap] (or null on
     * failure). Defaults to the production YUV_420_888→Bitmap path
     * ([CameraUtils.imageToBitmap]). Injectable purely as a test seam: it
     * lets unit tests drive the decode control flow (throttle, success/
     * failure callbacks, image cleanup) with a stub conversion, since the
     * real YUV path cannot run against a mocked [Image] with no planes. The
     * conversion itself is covered end-to-end by the androidTest suite.
     */
    private val imageToBitmap: (Image) -> Bitmap? = CameraUtils::imageToBitmap
) {
    
    companion object {
        private const val TAG = "Camera2JABCodeAnalyzer"
        // Quiet-zone margin added around the reticle ROI before cropping, as a
        // fraction of the ROI size per side. JABCode (like QR) needs a light
        // border to lock the perspective transform; without it a tightly-drawn
        // reticle yields "found but not decodable" — detection succeeds but the
        // master symbol can't establish. 0.18 ≈ 3-4 modules on a typical symbol.
        private const val ROI_QUIET_ZONE_PAD = 0.18f
    }
    
    private val qualityAnalyzer = if (options.includeQualityMetrics && onQualityUpdate != null) {
        ImageQualityAnalyzer()
    } else {
        null
    }
    
    private var lastAnalyzedTimestamp = 0L
    private var frameCount = 0
    private var decodeAttempts = 0

    // Rolling buffer of recent nc2 (COLOR_8) decodes for the consensus gate.
    private class Nc2Record(val timestampMs: Long, val payload: ByteArray)
    private val nc2Recent = ArrayDeque<Nc2Record>()

    /**
     * Analyze a frame from ImageReader
     * CRITICAL: Caller MUST close the Image after this returns
     */
    fun analyze(imageReader: ImageReader) {
        var image: Image? = null
        // Trace section for Jetpack Macrobenchmark TraceSectionMetric.
        // Captures the full analyze() span for end-to-end frame timing.
        android.os.Trace.beginSection("Camera2JABCodeAnalyzer.analyze")
        try {
            frameCount++

            // Frame throttling - prevent CPU overload
            val currentTimestamp = System.currentTimeMillis()
            if (currentTimestamp - lastAnalyzedTimestamp < options.analyzeIntervalMs) {
                // Drain the buffer even though this frame is not being analysed.
                //
                // The ImageReader's pool is FOUR deep. Returning here without acquiring leaves
                // this frame's buffer held by the queue, and once four accumulate the HAL cannot
                // dequeue one to write the next frame into. It waits, times out, and drops the
                // frame at the source:
                //
                //   E Camera3-OutputStream: Can't dequeue next output buffer: Connection timed out
                //   E CameraDeviceClient: notifyError: errorCode=5, errorStreamId=1
                //
                // Measured on an SM-S918U before this line existed: 60 dequeue timeouts and 74
                // dropped frames in 50 seconds, which is what the stutter was.
                //
                // acquireLatestImage closes everything staler than the newest, so one call and
                // one close returns the whole backlog rather than a single slot.
                //
                // This was harmless for as long as the throttle never fired — the per-frame cost
                // was 590ms against a 300ms window, so the branch was unreachable. Making the
                // quality analysis 225x faster brought the cycle under the window and woke it up.
                imageReader.acquireLatestImage()?.close()
                Log.v(TAG, "Frame $frameCount throttled (interval: ${currentTimestamp - lastAnalyzedTimestamp}ms < ${options.analyzeIntervalMs}ms)")
                return
            }
            lastAnalyzedTimestamp = currentTimestamp
            
            Log.d(TAG, "=== Analyzing frame $frameCount ===")
            
            // Acquire latest image
            image = imageReader.acquireLatestImage()
            if (image == null) {
                Log.w(TAG, "Frame $frameCount: No image available from ImageReader")
                return
            }
            
            Log.d(TAG, "Frame $frameCount: Acquired image ${image.width}x${image.height}, format=${image.format}")
            
            // Convert Image to Bitmap
            val bitmapStart = System.currentTimeMillis()
            val bitmap = imageToBitmap(image)
            val bitmapTime = System.currentTimeMillis() - bitmapStart
            
            if (bitmap == null) {
                Log.e(TAG, "Frame $frameCount: Bitmap conversion failed")
                return
            }

            // Release the camera buffer NOW. imageToBitmap() above produced a fully
            // independent copy, so the Image is no longer needed; nothing below reads
            // it. Holding it across the long (45-449ms) decode blocks the ImageReader's
            // callback thread and exhausts its 4-slot buffer pool, stalling the camera
            // (the root cause that @Ignore'd the Suite C macrobenchmarks). Closing here
            // keeps the pool drained so acquireLatestImage()'s drop-stale logic works.
            image?.close()
            image = null

            Log.d(TAG, "Frame $frameCount: Bitmap created ${bitmap.width}x${bitmap.height} (${bitmapTime}ms)")
            
            // Pixel probe, kept as a diagnostic but no longer three lookups of the same
            // pixel inside one log call: the centre was fetched three times to shift and mask it
            // three ways, on every frame, whether or not the log was consumed.
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                val first = bitmap.getPixel(0, 0)
                val centre = bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
                fun rgb(p: Int) = "(${(p shr 16) and 0xFF},${(p shr 8) and 0xFF},${p and 0xFF})"
                Log.d(TAG, "Frame $frameCount: First pixel RGB=${rgb(first)}, centre RGB=${rgb(centre)}")
            }

            // Decode region-of-interest: when the reticle is active, hand the
            // decoder ONLY its region. This excludes the surrounding screen/desk
            // that otherwise spawns false-positive finder patterns and skews the
            // per-capture palette learning. Falls back to the full frame on any
            // crop error so a bad ROI can never blank the scanner.
            val roiSpec = roiProvider?.invoke()
            val decodeBitmap: Bitmap = if (roiSpec != null) {
                try {
                    cropToRoi(bitmap, roiSpec).also {
                        Log.d(TAG, "Frame $frameCount: ROI_CROP ${bitmap.width}x${bitmap.height} -> ${it.width}x${it.height} " +
                                   "roi=[${roiSpec.left},${roiSpec.top},${roiSpec.right},${roiSpec.bottom}] " +
                                   "viewAspect=${roiSpec.viewAspect} frameRot=${roiSpec.frameRotationDegrees}")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Frame $frameCount: ROI crop failed (${e.message}); decoding full frame")
                    bitmap
                }
            } else {
                bitmap
            }

            try {
                // Calculate quality metrics (if enabled) on the region we decode.
                if (qualityAnalyzer != null && onQualityUpdate != null) {
                    val qualityStart = System.currentTimeMillis()
                    val metrics = qualityAnalyzer.analyze(decodeBitmap)
                    val qualityTime = System.currentTimeMillis() - qualityStart
                    Log.v(TAG, "Frame $frameCount: Quality metrics - brightness=${metrics.brightness}, " +
                               "contrast=${metrics.contrast}, focus=${metrics.focus} (${qualityTime}ms)")
                    onQualityUpdate.invoke(metrics)
                }
                
                // Attempt JABCode decode
                decodeAttempts++
                Log.d(TAG, "Frame $frameCount: Starting decode attempt #$decodeAttempts (timeout=${options.timeout}ms)")
                
                val decodeStart = System.currentTimeMillis()
                android.os.Trace.beginSection("JABCodeDecoder.decode")
                val result = try {
                    decoder.decode(decodeBitmap, options)
                } finally {
                    android.os.Trace.endSection() // JABCodeDecoder.decode
                }
                val decodeTime = System.currentTimeMillis() - decodeStart
                
                if (result != null) {
                    // Success - found JABCode
                    Log.i(TAG, "Frame $frameCount: ✅ JABCode DETECTED! Data size=${result.data.size} bytes, " +
                               "colorMode=${result.colorMode}, decodeTime=${decodeTime}ms")
                    if (result.colorMode.value == 8 && nc2ConsensusMinAgreement > 1) {
                        // nc2 reached decode via the default-mode fall-through — the
                        // one path that can fabricate. Withhold until >= M recent nc2
                        // frames agree byte-identically; a fabrication won't reproduce.
                        val now = System.currentTimeMillis()
                        while (nc2Recent.isNotEmpty() &&
                               now - nc2Recent.first().timestampMs > nc2ConsensusWindowMs) {
                            nc2Recent.removeFirst()
                        }
                        nc2Recent.addLast(Nc2Record(now, result.data))
                        val agree = nc2Recent.count { it.payload.contentEquals(result.data) }
                        if (agree >= nc2ConsensusMinAgreement) {
                            Log.i(TAG, "Frame $frameCount: NC2_CONSENSUS reached $agree/$nc2ConsensusMinAgreement — accepting")
                            onDecodeSuccess(result)
                        } else {
                            Log.i(TAG, "Frame $frameCount: NC2_CONSENSUS pending $agree/$nc2ConsensusMinAgreement — withholding (anti-fabrication)")
                            onDecodeFailure("nc2 awaiting consensus ($agree/$nc2ConsensusMinAgreement)", decodeTime)
                        }
                    } else {
                        // Confirmed-Part-I mode (or gate disabled): trust the frame.
                        onDecodeSuccess(result)
                    }
                } else {
                    // Null result is a real failure outcome — surface it via the
                    // failure callback so the upstream ViewModel can categorize
                    // by status (status=0 "No JABCode found" vs status=1 "JABCode
                    // found but not decodable"). Without this, the rolling
                    // failure stats see only exceptions and the FAIL_ATTR axis
                    // can't be cross-referenced against ScannerViewModel telemetry.
                    val errorMsg = decoder.getLastError() ?: "No JABCode found"
                    Log.v(TAG, "Frame $frameCount: $errorMsg (decode took ${decodeTime}ms)")
                    onDecodeFailure(errorMsg, decodeTime)
                }
                
            } finally {
                if (decodeBitmap !== bitmap) decodeBitmap.recycle()
                bitmap.recycle()
            }
        } catch (e: Exception) {
            // Decode error - report to callback. Decode time isn't available
            // here because the exception may fire before the decode-start
            // timestamp is captured; report 0L so the stats path treats it as
            // a no-timing-info sample (excluded from min/max/avg/median).
            Log.e(TAG, "Frame $frameCount: ❌ Decode exception: ${e.javaClass.simpleName}: ${e.message}", e)
            onDecodeFailure("Decode error: ${e.message}", 0L)
        } finally {
            // CRITICAL: Close image to prevent buffer exhaustion
            image?.close()
            Log.v(TAG, "Frame $frameCount: Image closed")
            android.os.Trace.endSection() // Camera2JABCodeAnalyzer.analyze
        }
    }

    /**
     * Crop [src] (a landscape, sensor-orientation analysis frame) to the decode
     * region-of-interest described by [spec], returning an independent bitmap
     * the caller owns. Three steps — rotate to display orientation, centre-crop
     * to the preview's aspect ratio (matching its FILL_CENTER scaling), then
     * crop to the normalized ROI — so the on-screen reticle maps 1:1.
     */
    private fun cropToRoi(src: Bitmap, spec: RoiSpec): Bitmap {
        // 1) Rotate the sensor-orientation (landscape) frame to the view's
        //    CURRENT orientation. In portrait this is typically 90 degrees; in
        //    landscape it is 0 or 180, so the frame is used as-is or inverted.
        val deg = ((spec.frameRotationDegrees % 360) + 360) % 360
        val rotated: Bitmap = if (deg != 0) {
            val m = Matrix().apply { postRotate(deg.toFloat()) }
            Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
        } else {
            src
        }

        // 2) Un-letterbox the reticle's VIEW fractions into FRAME fractions. The preview FITS
        //    the view (whole frame visible, bars on one axis), so parts of the view are bars
        //    that address no frame at all. PreviewSizing owns this geometry for both the
        //    display and this mapping; a null means the reticle lies wholly in the bars, and
        //    the decoder gets the full frame rather than an invented sliver at the edge.
        val rw = rotated.width
        val rh = rotated.height
        val rotAspect = rw.toFloat() / rh.toFloat()
        val viewAspect = if (spec.viewAspect > 0f) spec.viewAspect else rotAspect
        val mapped = com.jabauth.jabcode.camera.transform.PreviewSizing.frameRectFor(
            viewAspect, rotAspect, spec.left, spec.top, spec.right, spec.bottom,
        ) ?: floatArrayOf(0f, 0f, 1f, 1f)

        // 3) Apply the normalized ROI within that visible region, EXPANDED by a
        //    quiet-zone margin (ROI_QUIET_ZONE_PAD per side). A reticle drawn
        //    tight to the code clips the light border the master symbol needs,
        //    so we pad outward; the surround re-admitted is a thin ring, not the
        //    whole frame, so the false-positive rejection is mostly retained.
        //    Clamp + floor a minimum so a degenerate reticle can't give a 0 crop.
        val roiW = (mapped[2] - mapped[0]) * rw
        val roiH = (mapped[3] - mapped[1]) * rh
        val padX = roiW * ROI_QUIET_ZONE_PAD
        val padY = roiH * ROI_QUIET_ZONE_PAD
        var l = (mapped[0] * rw - padX).toInt()
        var t2 = (mapped[1] * rh - padY).toInt()
        var r = (mapped[2] * rw + padX).toInt()
        var b = (mapped[3] * rh + padY).toInt()
        l = l.coerceIn(0, rw - 2)
        t2 = t2.coerceIn(0, rh - 2)
        r = r.coerceIn(l + 1, rw)
        b = b.coerceIn(t2 + 1, rh)

        var crop = Bitmap.createBitmap(rotated, l, t2, r - l, b - t2)
        // createBitmap may return the source instance when the rect is the whole
        // bitmap — copy so the crop is always independent of src/rotated and the
        // recycle bookkeeping above stays correct.
        if (crop === rotated || crop === src) {
            crop = crop.copy(crop.config ?: Bitmap.Config.ARGB_8888, false)
        }
        if (rotated !== src) rotated.recycle()
        return crop
    }
}
