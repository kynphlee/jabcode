package com.jabauth.ui.scanner

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.Rect
import android.hardware.camera2.*
import android.hardware.camera2.params.ColorSpaceTransform
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.RggbChannelVector
import android.hardware.camera2.params.MeteringRectangle
import android.hardware.camera2.params.SessionConfiguration
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import java.util.concurrent.Executor
import android.util.Log
import android.util.Size
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.requiredSize
import com.jabauth.jabcode.camera.transform.CentreCropSizing
import com.jabauth.jabcode.camera.transform.FrameRotationPublisher
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
import com.jabauth.jabcode.camera.CameraDeviceProfiler
import com.jabauth.jabcode.camera.metadata.MetadataExtractor
import com.jabauth.jabcode.camera.transform.OrientationCalculator
import kotlin.math.abs

/**
 * Aspect-ratio equality tolerance used when negotiating the analysis stream
 * size.
 *
 * 2% is wide enough to admit the macroblock-padded sizes some HALs advertise
 * in place of a clean 16:9 — 1920x1088 is 1.31% off — and far too narrow for a
 * 4:3 size to masquerade as 16:9 (that is 25% off). The looser band matters:
 * on a device whose only near-16:9 option is 1920x1088, a tighter tolerance
 * would reject it and fall back to 1280x720, trading a fractional-percent
 * reticle mis-map for a resolution that cannot decode a dense symbol at all.
 */
private const val ASPECT_EPSILON = 0.02f

/**
 * Pick the analysis (ImageReader) stream size from the sizes the camera
 * actually advertises for YUV_420_888, instead of hardcoding one.
 *
 * ### Why this matters: pixels per module
 *
 * ISO/IEC 23634 §4.5.2 asks for roughly **>= 5 px per module** for reliable
 * JAB Code decoding, and this project's own field calibration agrees: ~4.9
 * px/module reads, ~3.3 px/module fails. That makes the analysis stream's
 * resolution a hard ceiling on how dense a symbol the app can ever read:
 *
 * ```
 *   1280x720   ->  256 x 144 modules   (the previous hardcoded value)
 *   1920x1080  ->  384 x 216 modules   (this change)
 * ```
 *
 * A 12-symbol cascade measuring 260 x 216 modules exceeds the 720p ceiling on
 * BOTH axes, which is why it could not be read at all. Note the frame-level
 * ceiling above is an upper bound the full pipeline does not reach: the
 * preview is FILL_CENTER and the analyzer crops the frame to the *view*
 * aspect, so on a 19.5:9 phone only ~82% of the frame's short axis survives to
 * the decoder. See the report in the PR description for the end-to-end number.
 *
 * ### Why the width is capped
 *
 * [maxWidth] is a deliberate ceiling, not an oversight:
 *
 *  - Camera2's guaranteed stream combinations only promise a 1080p YUV stream
 *    alongside a PRIV preview on LIMITED/LEGACY hardware. Asking for a larger
 *    YUV output can fail session configuration outright on those devices.
 *  - Every analysed frame goes through a YUV->NV21->JPEG->Bitmap round trip
 *    (`CameraUtils.imageToBitmap`); its cost scales with pixel count, and the
 *    1280x720 value this replaces was itself chosen to cut that cost. Raising
 *    the cap further is the obvious next lever, but it must be justified by a
 *    measured on-device frame time, not assumed.
 *
 * @param supported sizes advertised for [android.graphics.ImageFormat.YUV_420_888]
 * @param targetAspect the preview stream's aspect ratio. The analysis stream is
 *        deliberately held to the SAME aspect as the preview: the ROI mapping in
 *        `Camera2JABCodeAnalyzer.cropToRoi` assumes the analysis frame and the
 *        preview cover an identical field of view, so letting the two diverge
 *        would silently mis-map the on-screen reticle onto the decoded region.
 * @param maxWidth inclusive ceiling on the chosen width (see above)
 * @param fallback returned when [supported] is empty or nothing fits the cap
 */
internal fun chooseAnalysisSize(
    supported: List<Size>,
    targetAspect: Float,
    maxWidth: Int,
    fallback: Size
): Size {
    // The configuration map reports sizes in sensor (landscape) orientation, so
    // width >= height; anything else is a portrait-only oddity we skip.
    val candidates = supported.filter {
        it.width in 1..maxWidth && it.height in 1..it.width
    }
    if (candidates.isEmpty()) return fallback

    fun aspectOf(s: Size) = s.width.toFloat() / s.height.toFloat()
    fun areaOf(s: Size) = s.width.toLong() * s.height.toLong()

    // Prefer the LARGEST size that matches the preview's aspect ratio. If the
    // device advertises none, fall back to the size whose aspect is closest to
    // it, largest first among equally-close ones.
    val sameAspect = candidates.filter { abs(aspectOf(it) - targetAspect) <= ASPECT_EPSILON }
    return if (sameAspect.isNotEmpty()) {
        sameAspect.maxByOrNull { areaOf(it) } ?: fallback
    } else {
        candidates.minWithOrNull(
            compareBy<Size> { abs(aspectOf(it) - targetAspect) }
                .thenByDescending { areaOf(it) }
        ) ?: fallback
    }
}

/**
 * Degrees the analysis frame must be rotated **clockwise** to be upright in the
 * device's CURRENT display orientation.
 *
 * `SENSOR_ORIENTATION` alone answers this only while the display sits in its
 * natural (portrait) orientation. Once the scanner is allowed to rotate, the
 * display's own rotation has to be folded in, otherwise a landscape frame gets
 * rotated as though the phone were still upright and the reticle-to-frame
 * mapping is off by 90 degrees.
 *
 * The arithmetic itself is NOT reimplemented here: it delegates to the SDK's
 * [OrientationCalculator], which is the repository's existing single source of
 * truth for it (and agrees with the CameraX / ML Kit convention — the back
 * camera faces opposite the display, so its sensor rotation and the display
 * rotation subtract; a front camera's add). This wrapper exists only to map
 * Camera2's `LENS_FACING` onto that API and to give the mapping a test seam.
 *
 * @param sensorOrientationDegrees `CameraCharacteristics.SENSOR_ORIENTATION`
 * @param surfaceRotationDegrees the display rotation in degrees (0/90/180/270),
 *        i.e. `Surface.ROTATION_*` multiplied by 90
 */
internal fun relativeFrameRotation(
    sensorOrientationDegrees: Int,
    surfaceRotationDegrees: Int,
    isFrontFacing: Boolean
): Int = OrientationCalculator().calculatePreviewRotation(
    sensorOrientation = sensorOrientationDegrees,
    deviceRotation = surfaceRotationDegrees,
    cameraFacing = if (isFrontFacing) {
        CameraDeviceProfiler.Facing.FRONT
    } else {
        // BACK also covers EXTERNAL/UNKNOWN, which the calculator treats
        // identically. This preview only ever opens cameraIdList[0].
        CameraDeviceProfiler.Facing.BACK
    }
)

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
// The preview stream's resolution. At file scope because two places need it: the controller
// configures the camera with it, and the composable derives the surface's aspect from it.
private const val PREVIEW_WIDTH = 1920
private const val PREVIEW_HEIGHT = 1080

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
    /** Reports the rotation (deg, clockwise) that brings the analysis frame
     *  into the CURRENT display orientation — see [relativeFrameRotation].
     *  Needed to map the on-screen reticle onto the analysis frame. Fires when
     *  the camera opens and again on every display-rotation change, so a
     *  scanner that is free to rotate keeps a correct mapping. */
    onFrameRotation: ((Int) -> Unit)? = null,
    /** External zoom control (e.g. the zoom slider). 1.0 = no zoom;
     *  clamped to the device's max digital zoom. Pinch-to-zoom still works
     *  independently and reports back via onZoomChanged. */
    zoom: Float = 1.0f,
    /** AE lock command from the decode-driven policy (ScannerViewModel): false =
     *  continuous AE that adapts to where the camera points; true = freeze the
     *  known-good (decoded) exposure. Replaces the old lock-on-first-convergence
     *  latch that froze the startup scene (washed out if started pointed away). */
    aeLocked: Boolean = false,
    /** Torch (flash held on) for scanning a printed symbol in poor light. Ignored on
     *  devices without a flash unit — see [onTorchAvailable], which is how a caller
     *  learns whether to offer the control at all rather than showing one that does
     *  nothing. */
    torchEnabled: Boolean = false,
    /** Reports once per camera open whether this device HAS a flash unit
     *  (FLASH_INFO_AVAILABLE). A UI that offers a torch button on a device with no
     *  flash is worse than one that omits it: the user cannot tell a broken feature
     *  from a dark room. */
    onTorchAvailable: ((Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val windowManager = remember { context.getSystemService(Context.WINDOW_SERVICE) as WindowManager }
    val camera2Controller = remember {
        Camera2Controller(
            context, windowManager,
            onFrameAvailable, autoFocus, exposureCompensation,
            onZoomChanged, onLowLightBoostSupported, onLowLightBoostStateChanged,
            onFrameRotation,
            onTorchAvailable
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

    // Drive AE lock from the decode-driven policy: continuous AE until the first
    // successful decode, then lock; re-arm on sustained failure / exposure shift.
    // Replaces the old lock-on-first-convergence latch (the wash-out root cause).
    LaunchedEffect(torchEnabled) {
        camera2Controller.setTorchEnabled(torchEnabled)
    }

    LaunchedEffect(aeLocked) {
        camera2Controller.setAeLocked(aeLocked)
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
    
    // The preview is sized to COVER its parent, which is what the TextureView matrix did with
    // maxOf(scaleX, scaleY). It cannot letterbox: the reticle publishes its rect as a fraction of
    // the view, so a black bar would be a region of the view addressing no part of the sensor
    // frame — a decoder that mysteriously stops rather than a layout that visibly slips.
    //
    // The aspect starts at the portrait default and is corrected the moment the camera reports
    // its rotation. Nothing here rotates pixels; a SurfaceView's buffer transform is the
    // compositor's job, and this only has to anticipate the shape that comes out.
    var previewAspect by remember {
        mutableStateOf(CentreCropSizing.aspectFor(PREVIEW_WIDTH, PREVIEW_HEIGHT, 90))
    }
    DisposableEffect(camera2Controller) {
        camera2Controller.onPreviewAspect = { previewAspect = it }
        onDispose { camera2Controller.onPreviewAspect = null }
    }

    BoxWithConstraints(
        // clipToBounds is load-bearing, not tidiness: the surface is deliberately larger than
        // this box on one axis and the overflow has to be cut, not drawn.
        modifier = modifier.clipToBounds(),
        contentAlignment = Alignment.Center,
    ) {
        val size = CentreCropSizing.cover(constraints.maxWidth, constraints.maxHeight, previewAspect)
        val density = LocalDensity.current
        AndroidView(
            factory = { ctx ->
                SurfaceView(ctx).apply {
                    // Above the window's own background but BELOW everything drawn after it, so
                    // the HUD and reticle stay visible. Without this a SurfaceView punches
                    // through and hides them.
                    setZOrderMediaOverlay(true)
                    holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) {
                            // Reached both on first show AND after every resize. openCamera is
                            // guarded so the second case rebuilds only the session.
                            camera2Controller.openCamera(this@apply, width, height)
                        }

                        override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, ht: Int) {
                            camera2Controller.publishFrameRotation()
                        }

                        // A surface going away is NOT the camera going away.
                        //
                        // This called releaseCamera(), which closes the CameraDevice. That is
                        // wrong: a SurfaceView destroys and recreates its surface on any size
                        // change, not only when it leaves the screen — and this preview resizes
                        // during layout, because it is deliberately larger than its parent. The
                        // result was a teardown/reopen cycle that ended with the camera released
                        // and the screen black.
                        //
                        // The session targets this surface and cannot outlive it; the device can.
                        // Releasing the device is the lifecycle observer's job (ON_STOP), which
                        // is the only place that actually knows the scanner is leaving.
                        override fun surfaceDestroyed(holder: SurfaceHolder) {
                            camera2Controller.releaseSessionOnly()
                        }
                    })
                }
            },
            // requiredSize, NOT size.
            //
            // Modifier.size is subject to the constraints coming down from the parent. This
            // surface is deliberately LARGER than its parent on one axis — that is the whole
            // point of covering — so `size` had its request silently reduced to the parent's
            // bounds and the buffer was stretched to fit instead of cropped.
            //
            // Measured: cover() asked for 1316x2340 in a 1080x2340 window and the view laid out
            // at 1080x2340, squeezing a 1920x1080 buffer into the wrong shape. Portrait's
            // distortion was mild enough to pass for normal; landscape's was not, which is why it
            // presented as "skews when I rotate" rather than "is always wrong".
            //
            // requiredSize ignores the incoming constraints, which is exactly what is wanted
            // here and the reason clipToBounds sits on the parent.
            modifier = with(density) { Modifier.requiredSize(size.width.toDp(), size.height.toDp()) },
        )
    }
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
    private val onFrameRotation: ((Int) -> Unit)? = null,
    private val onTorchAvailable: ((Boolean) -> Unit)? = null
) {
    companion object {
        private const val TAG = "Camera2Controller"
        // Heartbeat throttle for 3A telemetry (an AF/AE state change always logs;
        // between changes, emit at most one line per this interval).
        private const val TELEMETRY_3A_INTERVAL_MS = 1000L

        // WS-camera-1-2 split the preview and analysis resolutions; the preview
        // (TextureView display surface) stays at 1920x1080.
        // moved to file scope — the composable sizes the surface from these too

        // Analysis stream. WS-camera-1-2 pinned this at 1280x720 to cut the
        // per-frame YUV->Bitmap conversion cost, on the assumption that a
        // scanned symbol is a single ~21-module JAB Code. That assumption does
        // not hold for cascades: at ~5 px/module (ISO/IEC 23634 §4.5.2, and
        // this project's own 4.9-reads / 3.3-fails field calibration) the
        // analysis stream is a hard ceiling on symbol density —
        //
        //     1280x720   ->  256 x 144 modules
        //     1920x1080  ->  384 x 216 modules
        //
        // — and a 12-symbol 260x216-module cascade is outside the 720p ceiling
        // on both axes, so it could never be read at any distance or zoom.
        //
        // The size is now NEGOTIATED from the camera's own
        // StreamConfigurationMap (see chooseAnalysisSize) rather than
        // hardcoded; these constants are the target aspect / cap / fallback.
        // ANALYSIS_MAX_WIDTH is capped at 1920 on purpose — see the
        // chooseAnalysisSize KDoc for the two reasons (guaranteed Camera2
        // stream combinations on LIMITED/LEGACY hardware, and the unmeasured
        // per-frame conversion cost above 1080p).
        private const val ANALYSIS_MAX_WIDTH = 1920
        private const val ANALYSIS_FALLBACK_WIDTH = 1920
        private const val ANALYSIS_FALLBACK_HEIGHT = 1080
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
    // Decode-driven AE lock state (toggled via setAeLocked from the ViewModel's
    // lock-on-decode-success policy). Was previously latched on first 3A
    // convergence, which froze exposure to the startup scene. AWB is held by the
    // CLOUDY_DAYLIGHT preset regardless, so this flag only gates CONTROL_AE_LOCK.
    private var convergenceLocksApplied: Boolean = false
    private var activeRepeatingSurface: Surface? = null

    /** The live repeating request, kept so a torch toggle can amend it in place. */
    private var activeRequestBuilder: CaptureRequest.Builder? = null

    /** The live capture callback. Reused when reissuing, so amending the request does
     *  not silently stop the AE/LLB observation the rest of the class depends on. */
    private var activeCaptureCallback: CameraCaptureSession.CaptureCallback? = null
    private var imageReader: ImageReader? = null
    private var previewSurface: Surface? = null
    private var sensorOrientation: Int = 0
    private var currentSurfaceView: SurfaceView? = null

    /** Told when the display aspect changes, so the composable can resize the surface. */
    var onPreviewAspect: ((Float) -> Unit)? = null
    private var cameraCharacteristics: CameraCharacteristics? = null

    // Telling the analyzer which way up the frames are. Its own object because it is the
    // DECODER's input, not the preview's appearance: publishing the wrong rotation does not
    // smudge the picture, it maps the reticle onto the wrong part of the sensor frame and the
    // scanner quietly stops reading. See FrameRotationPublisher for why that is worth separating
    // from the display transform it used to share a function with.
    private val frameRotationPublisher = FrameRotationPublisher { deg ->
        Log.i(TAG, "Frame rotation -> ${deg}deg (sensorOri=${sensorOrientation}deg)")
        onFrameRotation?.invoke(deg)
    }

    // Display-rotation listener. surfaceChanged covers a portrait<->landscape flip (the view's
    // dimensions swap), but NOT a 180 degree one — landscape-left to landscape-right leaves the
    // surface exactly the same size while inverting the frame. Without this listener that case
    // would silently keep the previous rotation and mis-map the ROI.
    private val displayManager =
        context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
    private var displayListenerRegistered = false
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {}
        override fun onDisplayRemoved(displayId: Int) {}
        override fun onDisplayChanged(displayId: Int) {
            val sv = currentSurfaceView ?: return
            if (sv.display?.displayId != displayId) return
            publishFrameRotation()
        }
    }

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

    // 3A telemetry debounce. Reuses the jabcode-sdk MetadataExtractor for the
    // Camera2 -> enum state mapping (exposure/ISO/focus/af/ae/awb), logged on
    // state transitions + a ~1 Hz heartbeat. Closes the gap the AF/AE field
    // analysis flagged: decode/zoom/LLB were traced, but AE/AF state, exposure
    // and ISO were not, so 3A behaviour had to be inferred from pixels.
    private val metadataExtractor = MetadataExtractor()
    private var last3aAfRaw: Int? = null
    private var last3aAeRaw: Int? = null
    private var last3aLogMs: Long = 0L

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
        // Idempotency guard: a whole range of requested zoom values map to the
        // SAME 4-px crop bucket. Re-issuing the repeating request for an
        // unchanged crop is what produced the slider/pinch jitter — skip it.
        if (crop == cachedCropRegion) return
        cachedCropRegion = crop
        // Report the ACTUAL (quantized) zoom the crop yields — not the request.
        // Reporting the request let the HUD show a value the crop can't hit, so
        // the slider-sync kept yanking the thumb at high zoom (where the bucket
        // step exceeds the sync deadband). The real value is self-consistent.
        val actualZoom = if (halfW > 0) active.width().toFloat() / (2f * halfW) else zoomRatio
        currentZoomRatio = actualZoom
        Log.i(TAG, "Zoom -> req ${"%.3f".format(zoomRatio)}x actual ${"%.3f".format(actualZoom)}x crop=$crop")
        onZoomChanged?.invoke(actualZoom)
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
        // applyCropRegion now dedups the crop bucket and owns currentZoomRatio,
        // so just forward the clamped request.
        applyCropRegion(zoomRatio.coerceIn(1.0f, maxDigitalZoom))
    }

    /** Whether the torch is currently requested. */
    private var torchApplied: Boolean = false

    /** Whether this device has a flash unit at all, read once on camera open. */
    private var flashAvailable: Boolean = false

    /**
     * Apply or release CONTROL_AE_LOCK, driven by the ViewModel's
     * lock-on-decode-success policy. Continuous AE (locked=false) lets exposure
     * adapt to where the camera points; locking (locked=true) freezes the
     * known-good exposure after a successful decode. Idempotent; reissues the
     * repeating request on the background handler. AWB stays on the fixed
     * CLOUDY_DAYLIGHT preset either way, so colour stability for decoding is
     * unaffected by this toggle.
     */
    /**
     * Turn the torch on or off.
     *
     * <p>Applied as FLASH_MODE_TORCH on the repeating request rather than through
     * CameraManager.setTorchMode, which throws once this app holds the camera — the torch has to
     * be part of the session that owns the device, not a second claim on it.
     *
     * <p>Silently a no-op on a device with no flash unit; callers learn that from
     * onTorchAvailable and should not offer the control at all in that case.
     */
    fun setTorchEnabled(enabled: Boolean) {
        if (enabled == torchApplied) return
        if (enabled && !flashAvailable) {
            Log.w(TAG, "setTorchEnabled(true) ignored — device reports no flash unit")
            return
        }
        torchApplied = enabled

        // Amend the LIVE request rather than rebuilding it.
        //
        // The first cut posted a full startRepeatingRequest() to the background handler. That was
        // wrong twice over. It logged "reissuing" BEFORE resolving the surface, so a null surface,
        // a null handler or a null session all read as success — and measured on device, the
        // rebuild often did not run at all: two toggles produced no restart, and on another run
        // both restarts landed at the same millisecond, well after the user had toggled back off.
        // The handler is shared with frame analysis, so a rebuild queues behind decode work.
        //
        // A torch toggle does not need the request rebuilt; it needs one key changed. Setting
        // FLASH_MODE on the request already in flight and reissuing it is both cheaper and
        // immediate.
        val builder = activeRequestBuilder
        val session = captureSession
        if (builder == null || session == null) {
            Log.w(TAG, "setTorchEnabled($enabled): no live request (builder=${builder != null}, " +
                "session=${session != null}); torch will apply when the session starts")
            return
        }
        builder.set(
            CaptureRequest.FLASH_MODE,
            if (enabled) CaptureRequest.FLASH_MODE_TORCH else CaptureRequest.FLASH_MODE_OFF,
        )
        try {
            session.setRepeatingRequest(builder.build(), activeCaptureCallback, backgroundHandler)
            Log.i(TAG, "setTorchEnabled($enabled): FLASH_MODE applied to the live request")
        } catch (e: CameraAccessException) {
            Log.e(TAG, "setTorchEnabled($enabled) failed to reissue", e)
        } catch (e: IllegalStateException) {
            Log.e(TAG, "setTorchEnabled($enabled) on a closed session", e)
        }
    }

    fun setAeLocked(locked: Boolean) {
        if (locked == convergenceLocksApplied) return
        convergenceLocksApplied = locked
        Log.i(TAG, "setAeLocked($locked) -> decode-driven AE ${if (locked) "lock" else "re-arm"}; reissuing repeating request")
        val surface = activeRepeatingSurface ?: previewSurface ?: return
        backgroundHandler?.post {
            startRepeatingRequest(surface, applyConvergenceLocks = locked)
        }
    }

    /** Idempotent; paired with [unregisterDisplayListener]. */
    private fun registerDisplayListener() {
        if (displayListenerRegistered) return
        displayManager?.registerDisplayListener(displayListener, backgroundHandler)
        displayListenerRegistered = true
    }

    private fun unregisterDisplayListener() {
        if (!displayListenerRegistered) return
        displayManager?.unregisterDisplayListener(displayListener)
        displayListenerRegistered = false
    }

    fun openCamera(surfaceView: SurfaceView, viewWidth: Int, viewHeight: Int) {
        currentSurfaceView = surfaceView

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Camera permission not granted")
            return
        }

        // The device may already be open.
        //
        // This is reached from surfaceCreated, which fires on first show AND after every surface
        // resize — and a SurfaceView resizes whenever its layout does. Re-opening an open camera
        // races the close, so a resize rebuilds only what actually died with the old surface:
        // the capture session.
        cameraDevice?.let {
            Log.i(TAG, "Surface replaced; rebuilding the capture session on the open camera")
            releaseSessionOnly()
            createCaptureSession(surfaceView)
            publishFrameRotation()
            return
        }

        registerDisplayListener()

        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            val cameraId = manager.cameraIdList[0]  // Back camera
            
            // Get sensor orientation for rotation compensation. The rotation the
            // ROI mapping needs is display-relative, so it is reported from
            // publishFrameRotation() (called on open and on every rotation), not here.
            val characteristics = manager.getCameraCharacteristics(cameraId)
            cameraCharacteristics = characteristics
            sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0

            // Flash capability, read once per open. Reported so a caller can decide whether to
            // OFFER a torch control at all — a button that is present and does nothing is worse
            // than an absent one, because the user cannot tell it from a dark room.
            flashAvailable = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false
            Log.i(TAG, "Flash unit available: $flashAvailable")
            onTorchAvailable?.invoke(flashAvailable)

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
                        // applyCropRegion clamps the crop, dedups the bucket, and
                        // updates currentZoomRatio to the actual applied zoom.
                        applyCropRegion(
                            (currentZoomRatio * detector.scaleFactor).coerceIn(1.0f, maxDigitalZoom)
                        )
                        return true
                    }
                })
            surfaceView.setOnTouchListener(View.OnTouchListener { _, event: MotionEvent ->
                scaleGestureDetector.onTouchEvent(event)
                true
            })
            
            // Preview buffer sized to the preview resolution. On a SurfaceView this is the
            // holder's job rather than a SurfaceTexture's, and the compositor scales the result
            // to the view — which is exactly why the view is sized to the right aspect.
            surfaceView.holder.setFixedSize(PREVIEW_WIDTH, PREVIEW_HEIGHT)

            // Negotiate the analysis stream size from what this camera actually
            // advertises for YUV_420_888, rather than assuming a fixed value.
            // Held to the PREVIEW's aspect ratio on purpose — the analyzer's ROI
            // mapping assumes both streams cover the same field of view.
            val analysisSize = chooseAnalysisSize(
                supported = characteristics
                    .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    ?.getOutputSizes(ImageFormat.YUV_420_888)
                    ?.toList()
                    .orEmpty(),
                targetAspect = PREVIEW_WIDTH.toFloat() / PREVIEW_HEIGHT,
                maxWidth = ANALYSIS_MAX_WIDTH,
                fallback = Size(ANALYSIS_FALLBACK_WIDTH, ANALYSIS_FALLBACK_HEIGHT)
            )

            // Setup ImageReader for analysis frames.
            //
            // maxImages = 4 (was 2): the analyzer uses acquireLatestImage()
            // which auto-drops queued backlog, so the buffer count's job is
            // to keep the camera HAL from throttling itself when a decode
            // takes longer than one frame interval. With maxImages=2, even
            // a single slow decode caused the HAL to block on its third
            // outgoing frame (no buffer available to write into), cascading
            // into AE/AWB drift and the intermittent stutter documented in
            // docs/camera-control-audit.md issue E. At 1920x1080 YUV_420_888
            // each buffer is ~3.1 MB; 4 buffers ~= 12.4 MB — still well within
            // memory budget (it was ~5.6 MB at the old 1280x720).
            imageReader = ImageReader.newInstance(
                analysisSize.width, analysisSize.height,
                ImageFormat.YUV_420_888,
                4  // 4-deep buffer pool — see comment above
            ).apply {
                setOnImageAvailableListener({ reader ->
                    Log.v(TAG, "ImageReader onImageAvailable callback triggered")
                    onFrameAvailable?.invoke(reader)
                }, backgroundHandler)
            }

            // The module ceiling below is the FRAME-level bound at ~5 px/module
            // (ISO/IEC 23634 §4.5.2). The decoder sees less: the preview is
            // FILL_CENTER and the analyzer crops to the view aspect and then to
            // the reticle, so treat this as an upper bound, not a promise.
            Log.i(
                TAG,
                "Analysis stream negotiated: ${analysisSize.width}x${analysisSize.height} " +
                    "(cap ${ANALYSIS_MAX_WIDTH}px wide, YUV_420_888) — frame-level ceiling " +
                    "~${analysisSize.width / 5} x ${analysisSize.height / 5} modules at 5 px/module"
            )

            // Open camera
            manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    createCaptureSession(surfaceView)
                    
                    // Apply transform after camera opens
                    publishFrameRotation()
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
    
    private fun createCaptureSession(surfaceView: SurfaceView) {
        val camera = cameraDevice ?: return
        val reader = imageReader ?: return

        try {
            surfaceView.holder.setFixedSize(PREVIEW_WIDTH, PREVIEW_HEIGHT)
            val surface = surfaceView.holder.surface
            previewSurface = surface  // Store for auto-focus updates

            val stateCallback = object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    // The device can be gone by the time this lands.
                    //
                    // Configuring a session is asynchronous, and a SurfaceView destroys and
                    // recreates its surface whenever its size changes — which now happens during
                    // layout, because the preview is deliberately sized larger than its parent.
                    // surfaceDestroyed calls releaseCamera(), which closes the device, and this
                    // callback then arrives for a session belonging to it:
                    //
                    //   java.lang.IllegalStateException: CameraDevice was already closed
                    //       at Camera2Controller.startRepeatingRequest
                    //
                    // A TextureView kept one SurfaceTexture for its lifetime, so this race did
                    // not exist before the surface swap. Closing the orphaned session rather than
                    // just dropping it: it holds buffers, and this path is reached precisely when
                    // another session is about to want them.
                    if (cameraDevice == null) {
                        Log.i(TAG, "Session configured after the camera closed; discarding it")
                        try { session.close() } catch (e: Exception) {
                            Log.w(TAG, "Orphaned session would not close", e)
                        }
                        return
                    }
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
            // Re-checked here as well as at the call sites: every caller is asynchronous, and the
            // device can close between the null-check above and this line.
            val requestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            activeRequestBuilder = requestBuilder
            requestBuilder.addTarget(surface)
            requestBuilder.addTarget(reader.surface)
            
            // Apply auto-focus setting
            val afMode = if (autoFocusEnabled) {
                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
            } else {
                CaptureRequest.CONTROL_AF_MODE_OFF
            }
            requestBuilder.set(CaptureRequest.CONTROL_AF_MODE, afMode)

            // Torch. Set on EVERY repeating request rather than once, because the request is
            // rebuilt on zoom, AE-lock and rotation changes — a torch applied once would switch
            // itself off the next time the user pinched.
            requestBuilder.set(
                CaptureRequest.FLASH_MODE,
                if (torchApplied) CaptureRequest.FLASH_MODE_TORCH else CaptureRequest.FLASH_MODE_OFF,
            )
            
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
             * Experiment #2 (2026-08-06): cloudy_d65 OVERSHOT. A four-app
             * survey of one scene — this scanner, the diagnostic app,
             * Fraunhofer's reference app and the OEM camera, all pointed at
             * the same on-screen JABCode — measured the studio's white UI
             * panels (the brightest 15% of frame, which should be neutral):
             *
             *   this scanner   R/G 1.169  B/G 1.185   <- 19% green deficit
             *   diagnostic     R/G 1.100  B/G 1.218   <- 22%
             *   Fraunhofer     R/G 1.011  B/G 1.043   <- neutral
             *   OEM camera     R/G 0.990  B/G 1.040   <- neutral
             *
             * So the 2026-05-30 GREEN cast was traded for a MAGENTA one of
             * similar magnitude: the premise that CLOUDY_DAYLIGHT matches an
             * sRGB-D65 monitor does not hold on this sensor/panel pair. Both
             * reference implementations run OEM AUTO and both land neutral,
             * which is the strongest evidence available. 4-colour decoding is
             * the loser here — the palette separates largely on green, so a
             * 19% green deficit attacks exactly the classifier's axis.
             *
             * REVERTED to cloudy_d65 2026-08-06 (experiment #4). auto_lock really
             * does produce neutral whites — that half of experiment #2 replicated —
             * and it DECODES MUCH WORSE. Diagnostic app, 16-colour ECC3 single
             * symbol, centred, 30s window, C/A/C/A alternated, each run's build
             * verified from its own startup log:
             *
             *   cloudy_d65   54/58 = 93.1%   58/58 = 100.0%   pooled 96.6%
             *   auto_lock    43/58 = 74.1%   40/57 =  70.2%   pooled 72.2%
             *
             *   24.4pp gap · complete separation (worst cloudy_d65 run beats the
             *   best auto_lock run by 19pp) · failures 4/116 vs 32/115 ·
             *   Fisher exact p = 1.3e-07
             *
             * WHY the "correct" white balance loses: a classifier does not care
             * whether white renders as white. It cares whether palette entries stay
             * FAR APART. CLOUDY_DAYLIGHT pins a fixed ~6500K preset, so the OEM's
             * calibrated colour matrix applies a consistent de-mixing that keeps the
             * 16 palette colours spread; the cost is a white-point error a human
             * reads as a magenta cast. auto_lock converges to whatever neutralises
             * the scene's white — which fixes the whites we were measuring AND
             * compresses the channel differences the decoder classifies on. At 16
             * colours, where the palette includes white and the entries crowd, that
             * compression decides it. At 4 colours BOTH strategies hit 100% and the
             * difference is invisible — which is exactly why experiment #2's
             * colour-metric evidence looked so convincing.
             *
             * LESSON: white-point accuracy is not palette separability. Do not tune
             * this constant on colour metrics (chroma / RGB neutrality / clip%).
             * Tune it on DECODE RATE at >= 16 colours, alternated against a control,
             * with every run's build identified from its own log.
             *
             * AE-lock behaviour is preserved unchanged across all strategies
             * (one variable at a time). To A/B on-device, set wbStrategy to:
             *   "cloudy_d65"      — best measured decode rate; this build's default
             *   "auto_lock"       — neutral whites, ~24pp WORSE decoding (#164/#166)
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
                    // 3A telemetry: af/ae/awb state + exposure/ISO/focus. Logged on
                    // every AF or AE state transition, plus a ~1 Hz heartbeat so
                    // exposure/ISO drift stays visible during stable stretches.
                    // Debounced via the raw-state compare so it never logs per frame.
                    val afRaw = result.get(CaptureResult.CONTROL_AF_STATE)
                    val aeRaw = result.get(CaptureResult.CONTROL_AE_STATE)
                    val now3aMs = SystemClock.elapsedRealtime()
                    if (afRaw != last3aAfRaw || aeRaw != last3aAeRaw ||
                        now3aMs - last3aLogMs >= TELEMETRY_3A_INTERVAL_MS) {
                        last3aAfRaw = afRaw
                        last3aAeRaw = aeRaw
                        last3aLogMs = now3aMs
                        val m = metadataExtractor.extract(result)
                        val expMs = m.exposureTimeNs?.let { String.format("%.2f", it / 1_000_000.0) } ?: "?"
                        val focus = m.focusDistance?.let { String.format("%.2f", it) } ?: "?"
                        val evSteps = result.get(CaptureResult.CONTROL_AE_EXPOSURE_COMPENSATION) ?: 0
                        Log.i(
                            "JABCode3A",
                            "af=${m.afState} ae=${m.aeState} awb=${m.awbState} " +
                                "exp=${expMs}ms iso=${m.iso ?: "?"} focusDiopters=$focus " +
                                "evSteps=$evSteps frame=${m.frameNumber}"
                        )
                    }

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

                    // AE locking is now DECODE-DRIVEN (see setAeLocked, called from
                    // the ViewModel's lock-on-decode-success policy) instead of being
                    // latched here on first 3A convergence. The old auto-lock froze
                    // exposure to whatever the camera saw at startup, washing out the
                    // screen when a session began pointed away from it. AWB stays on
                    // the fixed CLOUDY_DAYLIGHT preset, so colour stability for
                    // decoding is preserved without freezing exposure.
                }
            }

            activeCaptureCallback = captureCallback
            session.setRepeatingRequest(
                requestBuilder.build(),
                captureCallback,
                backgroundHandler
            )

            Log.d(TAG, "Camera2 preview started: AF=${if (autoFocusEnabled) "ON" else "OFF"}, AE=${if (aeMode == CameraMetadata.CONTROL_AE_MODE_ON_LOW_LIGHT_BOOST_BRIGHTNESS_PRIORITY) "ON_LLB" else "ON"} (EVbias=${aeBiasStops}stop), AWB=$wbStrategy, AE_meter=centre-third, AE_lock=${if (applyConvergenceLocks) "LOCKED(decode-driven)" else "continuous"}")
            
        } catch (e: IllegalStateException) {
            // The device closed between the guard above and this call. Every caller of this
            // function is asynchronous, so that window cannot be closed by checking harder — only
            // by surviving it. Unhandled, it reaches the default handler on Camera2Background and
            // takes the process down; the surface that replaced this one will open its own camera.
            Log.w(TAG, "Repeating request abandoned — camera closed underneath it: ${e.message}")
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
    /**
     * Drop the capture session but keep the camera open.
     *
     * What a surface teardown costs. The session holds this surface as a target and cannot
     * survive it, but the device, the reader and the background thread are all still good — and
     * re-opening a camera is expensive and racy, which is what made the previous behaviour show
     * up as a black preview rather than a flicker.
     */
    fun releaseSessionOnly() {
        try {
            captureSession?.close()
            captureSession = null
            Log.d(TAG, "Capture session closed; camera device kept open")
        } catch (e: Exception) {
            Log.w(TAG, "Error closing capture session", e)
        }
    }

    fun releaseCamera() {
        try {
            unregisterDisplayListener()
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
     * Re-open the camera after returning from the background (ON_START).
     *
     * Mostly redundant now and deliberately kept. A SurfaceView DESTROYS its surface when it
     * leaves the screen and gets a fresh one on return, so surfaceCreated is the real re-open
     * signal — where a TextureView kept its SurfaceTexture and never re-fired, which is why this
     * hook existed. It stays as a backstop for the ordering not being guaranteed, and guards
     * twice: on cameraDevice != null so a first-launch ON_START cannot double-open, and on the
     * surface actually being valid so it cannot race surfaceCreated.
     */
    fun reopen() {
        if (cameraDevice != null) return
        val sv = currentSurfaceView ?: return
        // A SurfaceView with no valid surface is mid-teardown; surfaceCreated will re-open it.
        if (sv.holder.surface?.isValid == true) {
            Log.i(TAG, "Camera2 reopen on foreground")
            openCamera(sv, sv.width, sv.height)
        }
    }

    fun close() {
        try {
            unregisterDisplayListener()
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
     * Compute the rotation between the sensor's frame and the CURRENT display
     * orientation. Delegates to [relativeFrameRotation] (the CameraX /
     * ML Kit convention).
     *
     * Note this used to add the display rotation for a back camera instead of
     * subtracting it (the front-facing case). While the scanner was locked to
     * portrait the two agreed — surfaceRotationDegrees was always 0 — so the
     * error was invisible; it only surfaces once the scanner may rotate.
     * `isRotationRequired` below is unaffected either way, because the two
     * formulas differ by 2 * surfaceRotationDegrees, which is always 0 mod 180.
     */
    private fun computeRelativeRotation(surfaceRotationDegrees: Int): Int {
        val characteristics = cameraCharacteristics ?: return 0
        return relativeFrameRotation(
            sensorOrientationDegrees =
                characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0,
            surfaceRotationDegrees = surfaceRotationDegrees,
            isFrontFacing = characteristics.get(CameraCharacteristics.LENS_FACING) ==
                CameraCharacteristics.LENS_FACING_FRONT
        )
    }
    
    /**
     * Tell the analyzer which way up the frames are, and the composable what shape to be.
     *
     * This was `updateTransform`, and it also built a Matrix that reversed `TextureView`'s
     * stretch and rotated the preview to match the display. Both halves of that matrix are gone:
     * a `SurfaceView` is composited by SurfaceFlinger, which applies the buffer transform itself,
     * and the fill is now a measured size rather than a scale. What remains is the half that was
     * never about the surface at all.
     *
     * Runs on camera open, on every surface change (portrait <-> landscape) and from the display
     * listener — which catches the 180-degree flips that leave the view exactly the same size
     * while inverting the frame, and would otherwise mis-map the ROI in silence.
     */
    fun publishFrameRotation() {
        if (cameraCharacteristics == null) {
            Log.w(TAG, "Cannot publish frame rotation: camera characteristics not available")
            return
        }
        val surfaceRotationDegrees = windowManager.defaultDisplay.rotation * 90
        val relativeRotation = computeRelativeRotation(surfaceRotationDegrees)
        frameRotationPublisher.publish(relativeRotation)
        onPreviewAspect?.invoke(
            CentreCropSizing.aspectFor(PREVIEW_WIDTH, PREVIEW_HEIGHT, relativeRotation)
        )
    }
}
