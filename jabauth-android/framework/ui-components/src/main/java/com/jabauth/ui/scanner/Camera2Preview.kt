package com.jabauth.ui.scanner

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import android.view.TextureView
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
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val windowManager = remember { context.getSystemService(Context.WINDOW_SERVICE) as WindowManager }
    val camera2Controller = remember { Camera2Controller(context, windowManager, onFrameAvailable, autoFocus, exposureCompensation) }
    
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
    initialExposureCompensation: Int
) {
    companion object {
        private const val TAG = "Camera2Controller"
        private const val IMAGE_WIDTH = 1920  // Increased from 1280 for sharper edges
        private const val IMAGE_HEIGHT = 1080  // Increased from 720 for sharper edges
    }
    
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private var previewSurface: Surface? = null
    private var sensorOrientation: Int = 0
    private var currentTextureView: TextureView? = null
    private var cameraCharacteristics: CameraCharacteristics? = null
    
    @Volatile
    private var autoFocusEnabled: Boolean = initialAutoFocus
    
    @Volatile
    private var exposureCompensationValue: Int = initialExposureCompensation
    
    private val backgroundThread = HandlerThread("Camera2Background").apply { start() }
    private val backgroundHandler = Handler(backgroundThread.looper)
    
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
            
            // Set buffer size to match ImageReader dimensions
            val texture = textureView.surfaceTexture
            texture?.setDefaultBufferSize(IMAGE_WIDTH, IMAGE_HEIGHT)
            
            // Setup ImageReader for analysis frames
            imageReader = ImageReader.newInstance(
                IMAGE_WIDTH, IMAGE_HEIGHT, 
                ImageFormat.YUV_420_888, 
                2  // Double buffering
            ).apply {
                setOnImageAvailableListener({ reader ->
                    Log.v(TAG, "ImageReader onImageAvailable callback triggered")
                    onFrameAvailable?.invoke(reader)
                }, backgroundHandler)
            }
            
            Log.d(TAG, "ImageReader initialized: ${imageReader?.width}x${imageReader?.height}, format=${imageReader?.imageFormat} (expected: ${IMAGE_WIDTH}x${IMAGE_HEIGHT}, YUV=${ImageFormat.YUV_420_888})")
            
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
            texture?.setDefaultBufferSize(IMAGE_WIDTH, IMAGE_HEIGHT)
            val surface = Surface(texture)
            previewSurface = surface  // Store for auto-focus updates
            
            camera.createCaptureSession(
                listOf(surface, reader.surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        captureSession = session
                        startRepeatingRequest(surface)
                    }
                    
                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        Log.e(TAG, "Capture session configuration failed")
                    }
                },
                backgroundHandler
            )
            
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Create capture session failed", e)
        }
    }
    
    private fun startRepeatingRequest(surface: Surface) {
        val camera = cameraDevice ?: return
        val session = captureSession ?: return
        val reader = imageReader ?: return
        
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
            
            // Enable auto-exposure
            requestBuilder.set(
                CaptureRequest.CONTROL_AE_MODE,
                CaptureRequest.CONTROL_AE_MODE_ON
            )
            
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
            
            // Enable auto white balance
            requestBuilder.set(
                CaptureRequest.CONTROL_AWB_MODE,
                CaptureRequest.CONTROL_AWB_MODE_AUTO
            )
            
            session.setRepeatingRequest(
                requestBuilder.build(),
                null,
                backgroundHandler
            )
            
            Log.d(TAG, "Camera2 preview started: AF=${if (autoFocusEnabled) "ON" else "OFF"}, AE=ON (EV=${exposureCompensationValue}), AWB=AUTO")
            
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
        val previewWidth = IMAGE_WIDTH
        val previewHeight = IMAGE_HEIGHT
        
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
