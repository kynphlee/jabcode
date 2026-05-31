package com.jabauth.ui.scanner

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.hardware.camera2.params.ColorSpaceTransform
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.RggbChannelVector
import android.hardware.camera2.params.SessionConfiguration
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import java.util.concurrent.Executor
import android.util.Log
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat

/**
 * Camera2Preview - Raw Camera2 API preview component
 * 
 * Reusable Compose component providing Camera2 preview with ImageReader frame delivery.
 * Located in framework for reuse across diagnostic and production apps.
 * 
 * **Features:**
 * - Camera2 TextureView preview
 * - Configurable auto-focus (continuous picture mode or off)
 * - Auto-exposure with compensation
 * - Auto white balance
 * - ImageReader for frame analysis
 * - Lifecycle management
 * 
 * **Usage:**
 * ```kotlin
 * Camera2Preview(
 *     onFrameAvailable = { reader ->
 *         analyzer.analyze(reader)
 *     },
 *     autoFocus = true,
 *     exposureCompensation = 0,
 *     modifier = Modifier.aspectRatio(16f/9f)
 * )
 * ```
 * 
 * @param onFrameAvailable Callback when new camera frame available (receives ImageReader)
 * @param autoFocus Enable continuous auto-focus (default: true)
 * @param exposureCompensation Exposure compensation in EV steps (-2 to +2, default: 0)
 * @param modifier Compose modifier
 */
@Composable
fun Camera2Preview(
    onFrameAvailable: ((ImageReader) -> Unit)? = null,
    autoFocus: Boolean = true,
    exposureCompensation: Int = 0,
    /** Tier-1 HUD: pinch-zoom level changes (1.0 → maxDigitalZoom). */
    onZoomChanged: ((Float) -> Unit)? = null,
    /** Tier-1 HUD: LLB capability reported once per session-creation. */
    onLowLightBoostSupported: ((Boolean) -> Unit)? = null,
    /** Tier-1 HUD: LLB state transitions (0=INACTIVE, 1=ACTIVE). */
    onLowLightBoostStateChanged: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val windowManager = remember { context.getSystemService(Context.WINDOW_SERVICE) as WindowManager }
    val camera2Controller = remember {
        Camera2Controller(
            context, windowManager,
            onFrameAvailable, autoFocus, exposureCompensation,
            onZoomChanged, onLowLightBoostSupported, onLowLightBoostStateChanged
        )
    }
    
    // Update auto-focus when setting changes
    LaunchedEffect(autoFocus) {
        camera2Controller.updateAutoFocus(autoFocus)
    }
    
    // Update exposure compensation when setting changes
    LaunchedEffect(exposureCompensation) {
        camera2Controller.updateExposureCompensation(exposureCompensation)
    }
    
    DisposableEffect(Unit) {
        onDispose {
            camera2Controller.close()
        }
    }
    
    AndroidView(
        factory = { ctx ->
            TextureView(ctx).apply {
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                        camera2Controller.openCamera(this@apply, width, height)
                    }
                    
                    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
                        camera2Controller.updateTransform(this@apply, width, height)
                    }
                    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean = true
                    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
                }
            }
        },
        modifier = modifier
    )
}

private class Camera2Controller(
    private val context: Context,
    private val windowManager: WindowManager,
    private val onFrameAvailable: ((ImageReader) -> Unit)?,
    initialAutoFocus: Boolean,
    initialExposureCompensation: Int,
    private val onZoomChanged: ((Float) -> Unit)? = null,
    private val onLowLightBoostSupported: ((Boolean) -> Unit)? = null,
    private val onLowLightBoostStateChanged: ((Int) -> Unit)? = null
) {
    companion object {
        private const val TAG = "Camera2Controller"
        // WS-camera-1-2: split preview and analysis resolutions.
        // Preview at 1920x1080 (TextureView display surface) preserves the
        // existing viewfinder quality. Analysis at 1280x720 reduces the
        // per-frame YUV->ARGB bitmap conversion cost by ~2.25x while still
        // providing ~6 px/module at typical scanning distance (JABCode is
        // 21 modules per side; at 40% frame fill on a 1280-wide stream
        // that's 512 px / 21 = ~24 px/module — well above the 3-px
        // decoder minimum). See docs/camera-control-audit.md issue B.
        private const val PREVIEW_WIDTH = 1920
        private const val PREVIEW_HEIGHT = 1080
        private const val ANALYSIS_WIDTH = 1280
        private const val ANALYSIS_HEIGHT = 720
    }
    
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null

    // Convergence-lock state. Tracks whether the auto-white-balance (AWB)
    // and auto-exposure (AE) algorithms have converged at least once on
    // their target values. Once BOTH have converged, the repeating request
    // is reissued with CONTROL_AWB_LOCK + CONTROL_AE_LOCK set, freezing
    // the camera's color and brightness response for the rest of the
    // session. This is the load-bearing change for color stability during
    // JABCode metadata decoding — the H_nc2 cluster's green-channel
    // under-capture is fundamentally an AWB-drift problem, and locking
    // post-convergence is the cleanest Camera2-side fix that doesn't
    // require touching the decoder.
    //
    // See: docs/cassandra-register/H_nc2_decode_failure.md,
    //      framework/jabcode-sdk/docs/CAMERA_CONFIGURATION_GUIDE.md
    private var awbHasConverged: Boolean = false
    private var aeHasConverged: Boolean = false
    private var convergenceLocksApplied: Boolean = false
    private var activeRepeatingSurface: Surface? = null
    private var imageReader: ImageReader? = null
    private var previewSurface: Surface? = null
    private var sensorOrientation: Int = 0
    private var currentTextureView: TextureView? = null
    private var cameraCharacteristics: CameraCharacteristics? = null

    // WS-camera-3 Low Light Boost AE Mode (Android 15+, API 35).
    // Detected once per session in openCamera() from the camera's AE
    // available modes. When true, startRepeatingRequest() opts into
    // CONTROL_AE_MODE_ON_LOW_LIGHT_BOOST_BRIGHTNESS_PRIORITY instead of
    // CONTROL_AE_MODE_ON; the captureCallback then observes
    // CONTROL_LOW_LIGHT_BOOST_STATE so the diagnostic HUD knows when
    // LLB has actually engaged (it activates dynamically based on
    // scene luminance — supported ≠ active on a bright scene).
    private var lowLightBoostSupported: Boolean = false
    @Volatile
    private var lastReportedLlbState: Int = -1  // -1 = no observation yet

    // WS-camera-PR1 pinch-zoom verification (ROI implementation plan §1).
    // The plan's empirical decision gate: does manually zooming the camera
    // unlock high-Nc and Mode 0 decoding that fails at 1x? Pinch gesture
    // drives SCALER_CROP_REGION on every change; the trace correlates
    // decode rate to current zoom level so we can answer the gate question.
    // See docs/roi-detection-implementation-plan.md §1.4 and §1.6.1.
    @Volatile
    private var currentZoomRatio: Float = 1.0f
    private var maxDigitalZoom: Float = 1.0f
    private var activeArraySize: Rect? = null
    @Volatile
    private var cachedCropRegion: Rect? = null
    private lateinit var scaleGestureDetector: ScaleGestureDetector
    
    @Volatile
    private var autoFocusEnabled: Boolean = initialAutoFocus
    
    @Volatile
    private var exposureCompensationValue: Int = initialExposureCompensation
    
    private val backgroundThread = HandlerThread("Camera2Background").apply { start() }
    private val backgroundHandler = Handler(backgroundThread.looper)
    
    /**
     * WS-camera-PR1: apply a SCALER_CROP_REGION computed from the
     * given zoom ratio, centered on the sensor's active array.
     * Triggered by the pinch-zoom gesture listener.
     *
     * @param zoomRatio The desired zoom (1.0 = no zoom; clamped to
     *                  the device's reported max digital zoom)
     */
    private fun applyCropRegion(zoomRatio: Float) {
        val active = activeArraySize ?: run {
            Log.w(TAG, "applyCropRegion: no active array size; ignoring")
            return
        }
        // Compute centered crop. Half-width and half-height scale with
        // 1/zoomRatio. Align to 4-pixel boundaries to avoid HAL rounding
        // (Camera2 best practice).
        val centerX = active.centerX()
        val centerY = active.centerY()
        val halfW = ((active.width()  / (2.0f * zoomRatio)).toInt() / 4) * 4
        val halfH = ((active.height() / (2.0f * zoomRatio)).toInt() / 4) * 4
        val crop = Rect(centerX - halfW, centerY - halfH,
                        centerX + halfW, centerY + halfH)
        cachedCropRegion = crop
        Log.i(TAG, "PinchZoom: Zoom -> ${"%.2f".format(zoomRatio)}x, crop=$crop")
        onZoomChanged?.invoke(zoomRatio)
        // Re-issue the repeating request with the new crop applied.
        previewSurface?.let { startRepeatingRequest(it) }
    }

    /**
     * Update auto-focus setting and restart capture request if active
     */
    fun updateAutoFocus(enabled: Boolean) {
        if (autoFocusEnabled != enabled) {
            autoFocusEnabled = enabled
            Log.d(TAG, "Auto-focus ${if (enabled) "enabled" else "disabled"}")
            
            // Restart capture request with new setting
            previewSurface?.let { surface ->
                startRepeatingRequest(surface)
            }
        }
    }
    
    /**
     * Update exposure compensation and restart capture request if active
     */
    fun updateExposureCompensation(value: Int) {
        if (exposureCompensationValue != value) {
            exposureCompensationValue = value
            Log.d(TAG, "Exposure compensation set to ${value} EV")
            
            // Restart capture request with new setting
            previewSurface?.let { surface ->
                startRepeatingRequest(surface)
            }
        }
    }
    
    fun openCamera(textureView: TextureView, viewWidth: Int, viewHeight: Int) {
        currentTextureView = textureView
        
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) 
            != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Camera permission not granted")
            return
        }
        
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            val cameraId = manager.cameraIdList[0]  // Back camera
            
            // Get sensor orientation for rotation compensation
            val characteristics = manager.getCameraCharacteristics(cameraId)
            cameraCharacteristics = characteristics
            sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0

            // WS-camera-3: detect Low Light Boost AE Mode capability (API 35+).
            // Google's own docs list "scanning QR codes in low light" as a
            // primary use case. When supported, opt in to brighten the live
            // preview adaptively under dim conditions — should help with
            // print scanning in office lighting and may relax the Mode 0
            // chroma-tolerance margin we already tightened (lower noise
            // floor under longer exposure).
            lowLightBoostSupported = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                val availableAeModes = characteristics
                    .get(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES)
                availableAeModes?.contains(
                    CameraMetadata.CONTROL_AE_MODE_ON_LOW_LIGHT_BOOST_BRIGHTNESS_PRIORITY
                ) ?: false
            } else {
                false
            }
            Log.i(TAG, "Low Light Boost AE Mode supported: $lowLightBoostSupported (API ${Build.VERSION.SDK_INT})")
            onLowLightBoostSupported?.invoke(lowLightBoostSupported)

            // WS-camera-PR1: capture the sensor's active array (the coordinate
            // space for SCALER_CROP_REGION) and the device's max digital zoom
            // ratio. On LEGACY hardware level, max zoom may be 1.0 (no zoom
            // support); the gesture detector will then clamp at 1.0 and no
            // crop is ever applied — clean no-op fallback.
            activeArraySize = characteristics
                .get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
            maxDigitalZoom = characteristics
                .get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1.0f
            Log.i(TAG, "PinchZoom: activeArraySize=$activeArraySize maxDigitalZoom=${maxDigitalZoom}x")

            // ScaleGestureDetector for pinch-to-zoom. The listener updates
            // currentZoomRatio (clamped to [1.0, maxDigitalZoom]) and calls
            // applyCropRegion(), which re-issues the repeating request with
            // a new SCALER_CROP_REGION computed from the active array.
            scaleGestureDetector = ScaleGestureDetector(context,
                object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    override fun onScale(detector: ScaleGestureDetector): Boolean {
                        val newZoom = (currentZoomRatio * detector.scaleFactor)
                            .coerceIn(1.0f, maxDigitalZoom)
                        if (newZoom != currentZoomRatio) {
                            currentZoomRatio = newZoom
                            applyCropRegion(newZoom)
                        }
                        return true
                    }
                })
            textureView.setOnTouchListener(View.OnTouchListener { _, event: MotionEvent ->
                scaleGestureDetector.onTouchEvent(event)
                true
            })
            
            // Preview surface buffer sized to the preview resolution.
            val texture = textureView.surfaceTexture
            texture?.setDefaultBufferSize(PREVIEW_WIDTH, PREVIEW_HEIGHT)

            // Setup ImageReader for analysis frames at the smaller
            // analysis resolution to reduce per-frame conversion cost.
            //
            // maxImages = 4 (was 2): the analyzer uses acquireLatestImage()
            // which auto-drops queued backlog, so the buffer count's job is
            // to keep the camera HAL from throttling itself when a decode
            // takes longer than one frame interval. With maxImages=2, even
            // a single slow decode caused the HAL to block on its third
            // outgoing frame (no buffer available to write into), cascading
            // into AE/AWB drift and the intermittent stutter documented in
            // docs/camera-control-audit.md issue E. At 1280x720 YUV_420_888
            // each buffer is ~1.4 MB; 4 buffers ~= 5.6 MB — well within
            // memory budget.
            imageReader = ImageReader.newInstance(
                ANALYSIS_WIDTH, ANALYSIS_HEIGHT,
                ImageFormat.YUV_420_888,
                4  // 4-deep buffer pool — see comment above
            ).apply {
                setOnImageAvailableListener({ reader ->
                    Log.v(TAG, "ImageReader onImageAvailable callback triggered")
                    onFrameAvailable?.invoke(reader)
                }, backgroundHandler)
            }

            Log.d(TAG, "ImageReader initialized: ${imageReader?.width}x${imageReader?.height}, format=${imageReader?.imageFormat} (expected: ${ANALYSIS_WIDTH}x${ANALYSIS_HEIGHT}, YUV=${ImageFormat.YUV_420_888})")
            
            // Open camera
            manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    createCaptureSession(textureView)
                    
                    // Apply transform after camera opens
                    updateTransform(textureView, viewWidth, viewHeight)
                }
                
                override fun onDisconnected(camera: CameraDevice) {
                    Log.w(TAG, "Camera disconnected")
                    close()
                }
                
                override fun onError(camera: CameraDevice, error: Int) {
                    Log.e(TAG, "Camera error: $error")
                    close()
                }
            }, backgroundHandler)
            
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Camera access exception", e)
        }
    }
    
    private fun createCaptureSession(textureView: TextureView) {
        val camera = cameraDevice ?: return
        val reader = imageReader ?: return

        try {
            val texture = textureView.surfaceTexture
            texture?.setDefaultBufferSize(PREVIEW_WIDTH, PREVIEW_HEIGHT)
            val surface = Surface(texture)
            previewSurface = surface  // Store for auto-focus updates

            val stateCallback = object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    captureSession = session
                    startRepeatingRequest(surface)
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Log.e(TAG, "Capture session configuration failed")
                }
            }

            // WS-camera-1-2: prefer the modern SessionConfiguration API
            // (API 28+) so per-surface OutputConfiguration objects can
            // carry a streamUseCase hint to the HAL on API 33+. On older
            // Android versions, fall back to the legacy List<Surface>
            // overload — same behavior as before the modernization.
            //
            // streamUseCase = PREVIEW_VIDEO_STILL signals to the HAL that
            // this is a multi-purpose stream pipeline (sustained preview
            // + concurrent analysis), unlocking sensor-mode and frame-
            // rate optimizations the DEFAULT (0) case can't choose. See
            // docs/camera-control-audit.md issue A for the empirical
            // motivation — logcat under the legacy path showed
            // streamUseCase=0 on every stream.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val previewConfig = OutputConfiguration(surface)
                val analysisConfig = OutputConfiguration(reader.surface)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val streamUseCase = CameraMetadata
                        .SCALER_AVAILABLE_STREAM_USE_CASES_PREVIEW_VIDEO_STILL
                        .toLong()
                    previewConfig.streamUseCase = streamUseCase
                    analysisConfig.streamUseCase = streamUseCase
                    Log.d(TAG, "Capture session: streamUseCase=PREVIEW_VIDEO_STILL on both surfaces")
                } else {
                    Log.d(TAG, "Capture session: modern API but pre-T (no streamUseCase)")
                }
                val executor = Executor { command -> backgroundHandler?.post(command) }
                val sessionConfig = SessionConfiguration(
                    SessionConfiguration.SESSION_REGULAR,
                    listOf(previewConfig, analysisConfig),
                    executor,
                    stateCallback
                )
                camera.createCaptureSession(sessionConfig)
            } else {
                Log.d(TAG, "Capture session: legacy List<Surface> path (API < P)")
                @Suppress("DEPRECATION")
                camera.createCaptureSession(
                    listOf(surface, reader.surface),
                    stateCallback,
                    backgroundHandler
                )
            }
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Create capture session failed", e)
        }
    }
    
    private fun startRepeatingRequest(surface: Surface, applyConvergenceLocks: Boolean = false) {
        val camera = cameraDevice ?: return
        val session = captureSession ?: return
        val reader = imageReader ?: return
        activeRepeatingSurface = surface
        
        try {
            val requestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            requestBuilder.addTarget(surface)
            requestBuilder.addTarget(reader.surface)
            
            // Apply auto-focus setting
            val afMode = if (autoFocusEnabled) {
                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
            } else {
                CaptureRequest.CONTROL_AF_MODE_OFF
            }
            requestBuilder.set(CaptureRequest.CONTROL_AF_MODE, afMode)
            
            // Enable auto-exposure. When the device supports Low Light
            // Boost AE Mode (API 35+, detected in openCamera), opt in —
            // it adaptively brightens the live preview under dim
            // conditions without breaking temporal continuity (no
            // multi-frame combining, no shutter delay). Detection state
            // observed via the captureCallback below; supported != active.
            val aeMode = if (lowLightBoostSupported &&
                             Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                CameraMetadata.CONTROL_AE_MODE_ON_LOW_LIGHT_BOOST_BRIGHTNESS_PRIORITY
            } else {
                CaptureRequest.CONTROL_AE_MODE_ON
            }
            requestBuilder.set(CaptureRequest.CONTROL_AE_MODE, aeMode)
            
            // Set exposure compensation (Tier 2: Improve binarization)
            val aeCompensationRange = cameraCharacteristics?.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)
            if (aeCompensationRange != null && exposureCompensationValue in aeCompensationRange.lower..aeCompensationRange.upper) {
                requestBuilder.set(
                    CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION,
                    exposureCompensationValue
                )
            } else {
                Log.w(TAG, "Exposure compensation ${exposureCompensationValue} out of range: ${aeCompensationRange?.lower}..${aeCompensationRange?.upper}")
            }
            
            /* Path α revised — Manual white balance override (experimental
             * 2026-05-30): bypasses the existing AUTO + convergence-lock
             * AWB behavior from PR #36 entirely, in favor of disabling AWB
             * and applying neutral RGGB gains + identity color-correction
             * matrix.
             *
             * Empirical motivation: the post-AWB-convergence-lock raw-byte
             * trace (06:56:25) showed metadata-position samples consistently
             * read as raw_bytes=(254, 125, 254) — R saturated, G mid-range,
             * B saturated. For a fixture's Y modules (R+G, B=0), B should
             * read NEAR ZERO, not 254. The pattern points at the AWB
             * convergence having locked to a non-neutral scene white-point
             * and the resulting color-correction matrix then applying a
             * spurious R+B amplification to all subsequent frames.
             *
             * Disabling AWB + setting neutral gains + identity transform
             * bypasses the ISP's color manipulation entirely. The sensor's
             * raw Bayer signal passes through with only the inherent CFA
             * characteristics. If the next nc2 trace shows B drop near 0
             * for previously-rgb=5 modules, the AWB-lock-to-wrong-point
             * hypothesis is empirically confirmed and this override
             * should be productized as a Settings opt-in.
             *
             * Note: AE (auto-exposure) lock behavior is INTENTIONALLY
             * preserved unchanged. The AWB-vs-AE separation is the test —
             * we're isolating the white-balance variable. If decoding
             * improves with manual WB + AE-auto-lock, the WB is the cause.
             *
             * To revert (e.g., if the experiment fails or is otherwise
             * unwanted), flip useManualWhiteBalance to false.
             */
            val useManualWhiteBalance = true
            if (useManualWhiteBalance) {
                requestBuilder.set(
                    CaptureRequest.CONTROL_AWB_MODE,
                    CaptureRequest.CONTROL_AWB_MODE_OFF
                )
                requestBuilder.set(
                    CaptureRequest.COLOR_CORRECTION_MODE,
                    CaptureRequest.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX
                )
                /* B-attenuated RGGB gains for the nc2-specific residual cast.
                 *
                 * 2026-05-31 eight-Nc re-baseline (H_nc2 register) showed
                 * five of eight Nc values reach >=97% PartI success at
                 * neutral (1.0, 1.0, 1.0, 1.0) gains — manual WB is a
                 * universal fix for those. nc2 remained at 0% (regressed
                 * from yesterday's 33.75%) because its specific metadata
                 * color content interacts with the residual B-channel
                 * cast that the neutral matrix doesn't suppress.
                 *
                 * Per the H_nc2 register's documented direction:
                 * RggbChannelVector(R, Gr, Gb, B) = (1.0, 1.0, 1.0, 0.3).
                 * Attenuate the B channel to 30% so saturated B reads
                 * drop below the Y-match B-ceiling (tolerance=80 per
                 * decoder.c:786). This is an experimental coefficient
                 * — empirical validation on the next eight-Nc re-baseline
                 * trace is the falsifier; if any of nc1/nc3/nc4/nc5/nc6
                 * regresses from ~100%, the coefficient needs softening
                 * back toward 0.5-0.7. */
                requestBuilder.set(
                    CaptureRequest.COLOR_CORRECTION_GAINS,
                    RggbChannelVector(1.0f, 1.0f, 1.0f, 0.3f)
                )
                // Identity 3×3 color-correction matrix as 9 rationals.
                // Each rational is (numerator, denominator). The diagonal
                // is 1/1, off-diagonal 0/1.
                val identityTransform = ColorSpaceTransform(
                    intArrayOf(
                        1, 1, 0, 1, 0, 1,   // row 0: [1, 0, 0]
                        0, 1, 1, 1, 0, 1,   // row 1: [0, 1, 0]
                        0, 1, 0, 1, 1, 1    // row 2: [0, 0, 1]
                    )
                )
                requestBuilder.set(
                    CaptureRequest.COLOR_CORRECTION_TRANSFORM,
                    identityTransform
                )
                // AE lock behavior preserved — applyConvergenceLocks still
                // controls CONTROL_AE_LOCK independently of WB.
                if (applyConvergenceLocks) {
                    requestBuilder.set(CaptureRequest.CONTROL_AE_LOCK, true)
                }
            } else {
                // Original AUTO + convergence-lock path from PR #36.
                requestBuilder.set(
                    CaptureRequest.CONTROL_AWB_MODE,
                    CaptureRequest.CONTROL_AWB_MODE_AUTO
                )
                if (applyConvergenceLocks) {
                    requestBuilder.set(CaptureRequest.CONTROL_AWB_LOCK, true)
                    requestBuilder.set(CaptureRequest.CONTROL_AE_LOCK, true)
                }
            }

            // WS-camera-PR1: apply the pinch-zoom crop region if set. The
            // gesture detector caches this between zoom events; we honor
            // it on every request rebuild so subsequent autofocus / AE
            // changes (which also call startRepeatingRequest) preserve
            // the zoom state.
            cachedCropRegion?.let { crop ->
                requestBuilder.set(CaptureRequest.SCALER_CROP_REGION, crop)
            }
            
            // WS-camera-3: capture callback that observes the LLB state
            // transitions. Only logs on state CHANGE (debounced via
            // lastReportedLlbState) so it does not flood the trace on
            // every captured frame. When LLB is unsupported, this still
            // runs but the LLB key returns null and the branch is a
            // single no-op map lookup per frame.
            val captureCallback = object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    result: TotalCaptureResult
                ) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                        val state = result.get(CaptureResult.CONTROL_LOW_LIGHT_BOOST_STATE)
                        if (state != null && state != lastReportedLlbState) {
                            lastReportedLlbState = state
                            val stateName = when (state) {
                                CameraMetadata.CONTROL_LOW_LIGHT_BOOST_STATE_ACTIVE -> "ACTIVE"
                                CameraMetadata.CONTROL_LOW_LIGHT_BOOST_STATE_INACTIVE -> "INACTIVE"
                                else -> "UNKNOWN($state)"
                            }
                            Log.i(TAG, "LowLightBoost state -> $stateName")
                            onLowLightBoostStateChanged?.invoke(state)
                        }
                    }

                    // Convergence-lock observation: latch AWB/AE convergence
                    // as one-way state, then reissue the repeating request
                    // with the locks applied once both have settled. This
                    // freezes the ISP's color-correction matrix and
                    // exposure values for the rest of the session,
                    // eliminating frame-to-frame drift that perturbs
                    // JABCode metadata color classification.
                    if (!convergenceLocksApplied) {
                        val awbState = result.get(CaptureResult.CONTROL_AWB_STATE)
                        val aeState = result.get(CaptureResult.CONTROL_AE_STATE)
                        if (awbState == CameraMetadata.CONTROL_AWB_STATE_CONVERGED) {
                            awbHasConverged = true
                        }
                        if (aeState == CameraMetadata.CONTROL_AE_STATE_CONVERGED ||
                            aeState == CameraMetadata.CONTROL_AE_STATE_FLASH_REQUIRED) {
                            // FLASH_REQUIRED also counts as "AE has decided"
                            // — the algorithm has converged on its best
                            // estimate, it just thinks flash would help.
                            // For locked diagnostic capture we accept the
                            // converged-without-flash decision.
                            aeHasConverged = true
                        }
                        if (awbHasConverged && aeHasConverged) {
                            convergenceLocksApplied = true
                            Log.i(TAG, "AWB+AE converged -> reissuing repeating request with CONTROL_AWB_LOCK and CONTROL_AE_LOCK enabled")
                            // Post to the background handler so we don't
                            // re-enter setRepeatingRequest from inside the
                            // capture callback path. The surface is
                            // captured at startRepeatingRequest entry.
                            val lockedSurface = activeRepeatingSurface
                            if (lockedSurface != null) {
                                backgroundHandler?.post {
                                    startRepeatingRequest(lockedSurface, applyConvergenceLocks = true)
                                }
                            }
                        }
                    }
                }
            }

            session.setRepeatingRequest(
                requestBuilder.build(),
                captureCallback,
                backgroundHandler
            )

            Log.d(TAG, "Camera2 preview started: AF=${if (autoFocusEnabled) "ON" else "OFF"}, AE=${if (aeMode == CameraMetadata.CONTROL_AE_MODE_ON_LOW_LIGHT_BOOST_BRIGHTNESS_PRIORITY) "ON_LLB" else "ON"} (EV=${exposureCompensationValue}), AWB=AUTO, ConvergenceLocks=${if (applyConvergenceLocks) "LOCKED" else "waiting-for-convergence"}")
            
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Start repeating request failed", e)
        }
    }
    
    fun close() {
        try {
            captureSession?.close()
            captureSession = null
            
            cameraDevice?.close()
            cameraDevice = null
            
            imageReader?.close()
            imageReader = null
            
            backgroundThread.quitSafely()
            
            Log.d(TAG, "Camera2 closed")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing camera", e)
        }
    }
    
    /**
     * Compute relative rotation between sensor orientation and display rotation
     * Following official Android Camera2 pattern from developer.android.com
     */
    private fun computeRelativeRotation(surfaceRotationDegrees: Int): Int {
        val characteristics = cameraCharacteristics ?: return 0
        
        val sensorOrientationDegrees = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
        
        // Reverse device orientation for front-facing cameras
        val sign = if (characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT) {
            1
        } else {
            -1
        }
        
        return (sensorOrientationDegrees - (surfaceRotationDegrees * sign) + 360) % 360
    }
    
    /**
     * Update TextureView transform with proper rotation and scaling compensation
     * 
     * Implements official Android Camera2 preview scaling pattern to handle:
     * - Aspect ratio preservation
     * - Sensor orientation vs display rotation delta
     * - Dimension swapping when rotation is required
     * 
     * Based on: https://developer.android.com/codelabs/android-camera2-preview
     */
    fun updateTransform(textureView: TextureView, viewWidth: Int, viewHeight: Int) {
        if (viewWidth == 0 || viewHeight == 0) return
        
        val characteristics = cameraCharacteristics ?: run {
            Log.w(TAG, "Cannot update transform: camera characteristics not available")
            return
        }
        
        val surfaceRotation = windowManager.defaultDisplay.rotation
        val surfaceRotationDegrees = surfaceRotation * 90
        
        // Camera output is always landscape (1280x720)
        val previewWidth = PREVIEW_WIDTH
        val previewHeight = PREVIEW_HEIGHT
        
        // Determine if rotation swaps dimensions
        val relativeRotation = computeRelativeRotation(surfaceRotationDegrees)
        val isRotationRequired = relativeRotation % 180 != 0
        
        // Calculate scale factors to reverse TextureView's default scaling
        var scaleX: Float
        var scaleY: Float
        
        if (sensorOrientation == 0) {
            // Sensor orientation 0° (rare, e.g., Chromebooks)
            scaleX = if (!isRotationRequired) {
                viewWidth.toFloat() / previewHeight
            } else {
                viewWidth.toFloat() / previewWidth
            }
            
            scaleY = if (!isRotationRequired) {
                viewHeight.toFloat() / previewWidth
            } else {
                viewHeight.toFloat() / previewHeight
            }
        } else {
            // Sensor orientation 90° or 270° (standard phones/tablets)
            scaleX = if (isRotationRequired) {
                viewWidth.toFloat() / previewHeight
            } else {
                viewWidth.toFloat() / previewWidth
            }
            
            scaleY = if (isRotationRequired) {
                viewHeight.toFloat() / previewWidth
            } else {
                viewHeight.toFloat() / previewHeight
            }
        }
        
        // Use uniform scale to fill view while maintaining aspect ratio
        val finalScale = maxOf(scaleX, scaleY)
        
        val halfWidth = viewWidth / 2f
        val halfHeight = viewHeight / 2f
        
        val matrix = android.graphics.Matrix()
        
        // Apply scaling based on rotation requirement
        if (isRotationRequired) {
            // Dimensions are swapped - use inverse scaling
            matrix.setScale(
                1 / scaleX * finalScale,
                1 / scaleY * finalScale,
                halfWidth,
                halfHeight
            )
        } else {
            // Dimensions not swapped - compensate for aspect ratio difference
            matrix.setScale(
                viewHeight / viewWidth.toFloat() / scaleY * finalScale,
                viewWidth / viewHeight.toFloat() / scaleX * finalScale,
                halfWidth,
                halfHeight
            )
        }
        
        // Rotate to compensate for display rotation
        matrix.postRotate(
            -surfaceRotationDegrees.toFloat(),
            halfWidth,
            halfHeight
        )
        
        textureView.setTransform(matrix)
        
        Log.d(TAG, "Transform updated: surfaceRot=${surfaceRotationDegrees}°, sensorOri=${sensorOrientation}°, " +
                   "relativeRot=${relativeRotation}°, rotRequired=${isRotationRequired}, " +
                   "scaleX=${scaleX}, scaleY=${scaleY}, finalScale=${finalScale}")
    }
}
