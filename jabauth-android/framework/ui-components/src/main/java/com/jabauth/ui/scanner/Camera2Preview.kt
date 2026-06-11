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
import android.hardware.camera2.params.MeteringRectangle
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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
    /** Reports CameraCharacteristics.SENSOR_ORIENTATION (deg) once the camera
     *  opens — needed to map the on-screen reticle onto the analysis frame. */
    onSensorOrientation: ((Int) -> Unit)? = null,
    /** External zoom control (e.g. the zoom slider). 1.0 = no zoom;
     *  clamped to the device's max digital zoom. Pinch-to-zoom still works
     *  independently and reports back via onZoomChanged. */
    zoom: Float = 1.0f,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val windowManager = remember { context.getSystemService(Context.WINDOW_SERVICE) as WindowManager }
    val camera2Controller = remember {
        Camera2Controller(
            context, windowManager,
            onFrameAvailable, autoFocus, exposureCompensation,
            onZoomChanged, onLowLightBoostSupported, onLowLightBoostStateChanged,
            onSensorOrientation
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

    // Drive zoom from an external control (the zoom slider). Pinch still works.
    LaunchedEffect(zoom) {
        camera2Controller.updateZoom(zoom)
    }
    
    // Release the camera on background (ON_STOP) and re-open on foreground
    // (ON_START), so the session doesn't go stale and hang on return. This is
    // the lifecycle handling the diagnostic app was missing vs. the reference
    // scanner.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> camera2Controller.releaseCamera()
                Lifecycle.Event.ON_START -> camera2Controller.reopen()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
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
    private val onLowLightBoostStateChanged: ((Int) -> Unit)? = null,
    private val onSensorOrientation: ((Int) -> Unit)? = null
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

    // WS-camera-ae-bias: deliberate NEGATIVE exposure bias, in EV *stops*,
    // converted to device steps at request time. Biasing dark keeps the JABCode
    // palette saturated (the decoder's brightness-normalised metric handles dim
    // input); biasing bright collapses magenta into the white attractor — a
    // deterministic decode failure. Too-dark is recoverable, too-bright is not.
    // Tune here from one trace to the next; -1.0 = half exposure.
    private val aeBiasStops: Float = -1.0f

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
    
    /**
     * WS-ui-scanrebuild: external zoom control (the zoom slider). Mirrors the
     * pinch-zoom path — clamp to the device's max digital zoom and reuse
     * applyCropRegion(), which sets the crop, re-issues the repeating request,
     * and fires onZoomChanged so the HUD/slider stay in sync.
     */
    fun updateZoom(zoomRatio: Float) {
        val clamped = zoomRatio.coerceIn(1.0f, maxDigitalZoom)
        if (clamped != currentZoomRatio) {
            currentZoomRatio = clamped
            applyCropRegion(clamped)
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
            onSensorOrientation?.invoke(sensorOrientation)

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
            // WS-camera-no-llb: plain AE, NOT Low-Light-Boost. LLB's "brightness
            // priority" over-brightens an emissive screen — it holds exposure up
            // and overrides the spot-meter, which is the residual wash that still
            // collapses magenta->white (the reference scanner runs plain AE and
            // captures vivid magenta off the same screen). LLB still helps dim
            // *print* scanning; make it an adaptive per-medium setting later.
            val aeMode = CaptureRequest.CONTROL_AE_MODE_ON
            requestBuilder.set(CaptureRequest.CONTROL_AE_MODE, aeMode)

            // WS-camera-ae-spotmeter: meter AE on the central reticle region,
            // not the whole frame. A JABCode on a dark surround makes full-frame
            // AE over-expose (it brightens for the dark average) and the bright
            // code washes toward white — saturated only when the code fills the
            // frame. Spot-metering the centre keeps the code correctly exposed at
            // any framing/zoom: the seamless saturation the reference scanner has.
            // Meter AE on the centre THIRD of the *displayed* FOV — the crop
            // region when zoomed, else the full active array. The previous
            // centre-HALF box was wide enough that at normal scanning distance
            // the dark surround fell inside it; the dark average then drove
            // continuous AE to over-expose, washing the bright code toward white
            // (the magenta->white collapse). The user's three "fixes" — zoom in,
            // get closer, background-then-reframe — were one act: making the code
            // fill the meter box. A centre-third box stays inside a reasonably
            // framed code, so AE meters the modules themselves and the exposure
            // stops depending on how much surround is in frame.
            val meterBase = cachedCropRegion ?: activeArraySize
            meterBase?.let { base ->
                val maxAeRegions = cameraCharacteristics
                    ?.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AE) ?: 0
                if (maxAeRegions > 0) {
                    val hw = base.width() / 6      // half-width: box spans the centre third
                    val hh = base.height() / 6
                    val centre = MeteringRectangle(
                        base.centerX() - hw, base.centerY() - hh,
                        hw * 2, hh * 2,
                        MeteringRectangle.METERING_WEIGHT_MAX
                    )
                    requestBuilder.set(CaptureRequest.CONTROL_AE_REGIONS, arrayOf(centre))
                    Log.i("JABCodeAE", "AE_REGION centre-third box=${hw * 2}x${hh * 2} @${base.centerX()},${base.centerY()} base=${base.width()}x${base.height()}")
                }
            }
            
            // Set exposure compensation. The device expresses compensation in
            // integer STEPS of CONTROL_AE_COMPENSATION_STEP EV each (commonly 1/6
            // or 1/3 EV) — so a raw "-1" is a fraction of a stop and does almost
            // nothing. Convert the desired bias in *stops* (aeBiasStops) to steps
            // against the device's own step size, add any caller offset, clamp.
            val aeCompensationRange = cameraCharacteristics?.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)
            val aeCompensationStep = cameraCharacteristics?.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP)
            if (aeCompensationRange != null && aeCompensationStep != null) {
                val evPerStep = aeCompensationStep.toFloat().takeIf { it > 0f } ?: (1f / 6f)
                val biasSteps = Math.round(aeBiasStops / evPerStep)
                val totalSteps = (exposureCompensationValue + biasSteps)
                    .coerceIn(aeCompensationRange.lower, aeCompensationRange.upper)
                requestBuilder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, totalSteps)
                Log.i("JABCodeAE", "EV bias=${aeBiasStops}stop evPerStep=$evPerStep -> $totalSteps steps (range ${aeCompensationRange.lower}..${aeCompensationRange.upper})")
            } else {
                Log.w(TAG, "AE compensation range/step unavailable; leaving EV at device default")
            }
            
            /* Experiment #1 — white-balance / colour-correction strategy.
             *
             * History (2026-05-30): a prior experiment suspected AWB had
             * locked to a non-neutral white point and switched to
             * CONTROL_AWB_MODE=OFF with an IDENTITY colour-correction matrix +
             * unity RGGB gains, to bypass the ISP's colour pipeline. But the
             * identity matrix also disables the sensor's channel de-mixing, so
             * the raw broad-CFA spectral overlap passes through uncorrected.
             * On-device (trace 2026-06-09) that reads as a systematic GREEN
             * cast: pure-blue modules measured (38,136,225) over 232 samples
             * where G should be ~0, while black stayed clean (7,10,13) —
             * ruling out additive flare and pointing at the disabled CCM.
             *
             * CLOUDY_DAYLIGHT (~6500K) matches an sRGB-D65 screen's white
             * point and lets the OEM's *calibrated* colour matrix apply,
             * restoring the de-mixing the identity transform removed. In this
             * mode we set NO COLOR_CORRECTION_* override — doing so would
             * replace the preset's matrix with identity again.
             *
             * AE-lock behaviour is preserved unchanged across all strategies
             * (one variable at a time). To A/B on-device, set wbStrategy to:
             *   "cloudy_d65"      — experiment #1 (this build's default)
             *   "auto_lock"       — OEM AUTO AWB, lock on convergence (PR #36)
             *   "manual_identity" — the prior identity-CCM path (green cast)
             */
            val wbStrategy = "cloudy_d65"
            Log.i(TAG, "JABCodeWB: wbStrategy=$wbStrategy")
            when (wbStrategy) {
                "auto_lock" -> {
                    requestBuilder.set(
                        CaptureRequest.CONTROL_AWB_MODE,
                        CaptureRequest.CONTROL_AWB_MODE_AUTO
                    )
                    if (applyConvergenceLocks) {
                        requestBuilder.set(CaptureRequest.CONTROL_AWB_LOCK, true)
                        requestBuilder.set(CaptureRequest.CONTROL_AE_LOCK, true)
                    }
                }
                "manual_identity" -> {
                    requestBuilder.set(
                        CaptureRequest.CONTROL_AWB_MODE,
                        CaptureRequest.CONTROL_AWB_MODE_OFF
                    )
                    requestBuilder.set(
                        CaptureRequest.COLOR_CORRECTION_MODE,
                        CaptureRequest.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX
                    )
                    requestBuilder.set(
                        CaptureRequest.COLOR_CORRECTION_GAINS,
                        RggbChannelVector(1.0f, 1.0f, 1.0f, 1.0f)
                    )
                    // Identity 3×3 CCM (9 rationals; diagonal 1/1, off-diag 0/1).
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
                    if (applyConvergenceLocks) {
                        requestBuilder.set(CaptureRequest.CONTROL_AE_LOCK, true)
                    }
                }
                else -> {
                    // "cloudy_d65" — experiment #1 default. Fixed ~D65 preset;
                    // the OEM's calibrated CCM applies (no manual override).
                    requestBuilder.set(
                        CaptureRequest.CONTROL_AWB_MODE,
                        CaptureRequest.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT
                    )
                    if (applyConvergenceLocks) {
                        requestBuilder.set(CaptureRequest.CONTROL_AE_LOCK, true)
                    }
                }
            }

            // WS-camera-PR1: apply the pinch-zoom crop region if set. The
            // gesture detector caches this between zoom events; we honor
            // it on every request rebuild so subsequent autofocus / AE
            // changes (which also call startRepeatingRequest) preserve
            // the zoom state.
            cachedCropRegion?.let { crop ->
                requestBuilder.set(CaptureRequest.SCALER_CROP_REGION, crop)
                /* Log zoom state per repeating-request rebuild. Tagged
                 * "JABCodeZoom" so traces can correlate session-to-session
                 * fixture-distance variance with pixels-per-module (zoom
                 * effectively increases the per-module sample count). Fires
                 * only when crop region is set — pinch-zoom events and
                 * AF/AE rebuilds. Does NOT fire per-frame. */
                Log.i("JABCodeZoom", "SCALER_CROP_REGION=${crop.left},${crop.top},${crop.right},${crop.bottom} (w=${crop.width()}, h=${crop.height()})")
            } ?: run {
                Log.i("JABCodeZoom", "SCALER_CROP_REGION=null (no pinch-zoom; using sensor's active array)")
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

            Log.d(TAG, "Camera2 preview started: AF=${if (autoFocusEnabled) "ON" else "OFF"}, AE=${if (aeMode == CameraMetadata.CONTROL_AE_MODE_ON_LOW_LIGHT_BOOST_BRIGHTNESS_PRIORITY) "ON_LLB" else "ON"} (EVbias=${aeBiasStops}stop), AWB=cloudy_d65, AE_meter=centre-third, ConvergenceLocks=${if (applyConvergenceLocks) "LOCKED" else "waiting-for-convergence"}")
            
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Start repeating request failed", e)
        }
    }
    
    /**
     * WS-camera-lifecycle: release camera resources (session, device, reader)
     * but KEEP the background thread alive, so the camera can be re-opened on
     * foreground return. Used on ON_STOP (background). close() remains the full
     * teardown (incl. background-thread quit) for final disposal — quitting the
     * thread there is exactly why a naive close-on-background hung on return.
     */
    fun releaseCamera() {
        try {
            captureSession?.close()
            captureSession = null
            cameraDevice?.close()
            cameraDevice = null
            imageReader?.close()
            imageReader = null
            Log.d(TAG, "Camera2 released (thread kept)")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing camera", e)
        }
    }

    /**
     * Re-open the camera on the retained TextureView after returning from the
     * background (ON_START). TextureView keeps its SurfaceTexture across
     * background, so onSurfaceTextureAvailable does NOT re-fire. Guards on
     * cameraDevice != null so the first-launch ON_START doesn't double-open.
     */
    fun reopen() {
        if (cameraDevice != null) return
        val tv = currentTextureView ?: return
        if (tv.isAvailable) {
            Log.i(TAG, "Camera2 reopen on foreground")
            openCamera(tv, tv.width, tv.height)
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
