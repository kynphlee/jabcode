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
 * - Auto-focus (continuous picture mode)
 * - Auto-exposure
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
 *     modifier = Modifier.aspectRatio(16f/9f)
 * )
 * ```
 * 
 * @param onFrameAvailable Callback when new camera frame available (receives ImageReader)
 * @param modifier Compose modifier
 */
@Composable
fun Camera2Preview(
    onFrameAvailable: ((ImageReader) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val camera2Controller = remember { Camera2Controller(context, onFrameAvailable) }
    
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
                        camera2Controller.openCamera(this@apply)
                        updateTransform(this@apply, width, height)
                    }
                    
                    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
                        updateTransform(this@apply, width, height)
                    }
                    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean = true
                    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
                }
            }
        },
        modifier = modifier
    )
}

/**
 * Update TextureView transform to properly scale camera output to view
 * 
 * Camera outputs 1280x720 (landscape 16:9)
 * View is portrait with 16:9 aspect ratio
 * Scale to fill width and center vertically
 */
private fun updateTransform(textureView: TextureView, viewWidth: Int, viewHeight: Int) {
    if (viewWidth == 0 || viewHeight == 0) return
    
    val matrix = android.graphics.Matrix()
    
    // Camera output dimensions (landscape)
    val bufferWidth = 1280f
    val bufferHeight = 720f
    
    // Scale to fill view width, maintain aspect ratio
    val scaleX = viewWidth / bufferWidth
    val scaleY = viewHeight / bufferHeight
    val scale = maxOf(scaleX, scaleY)
    
    // Center the preview
    val scaledWidth = bufferWidth * scale
    val scaledHeight = bufferHeight * scale
    val dx = (viewWidth - scaledWidth) / 2f
    val dy = (viewHeight - scaledHeight) / 2f
    
    matrix.setScale(scale, scale)
    matrix.postTranslate(dx, dy)
    
    textureView.setTransform(matrix)
}

private class Camera2Controller(
    private val context: Context,
    private val onFrameAvailable: ((ImageReader) -> Unit)?
) {
    companion object {
        private const val TAG = "Camera2Controller"
        private const val IMAGE_WIDTH = 1280
        private const val IMAGE_HEIGHT = 720
    }
    
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    
    private val backgroundThread = HandlerThread("Camera2Background").apply { start() }
    private val backgroundHandler = Handler(backgroundThread.looper)
    
    fun openCamera(textureView: TextureView) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) 
            != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Camera permission not granted")
            return
        }
        
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            val cameraId = manager.cameraIdList[0]  // Back camera
            
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
                    onFrameAvailable?.invoke(reader)
                }, backgroundHandler)
            }
            
            // Open camera
            manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    createCaptureSession(textureView)
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
            val previewSurface = Surface(texture)
            
            camera.createCaptureSession(
                listOf(previewSurface, reader.surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        captureSession = session
                        startRepeatingRequest(previewSurface)
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
    
    private fun startRepeatingRequest(previewSurface: Surface) {
        val camera = cameraDevice ?: return
        val session = captureSession ?: return
        val reader = imageReader ?: return
        
        try {
            val requestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            requestBuilder.addTarget(previewSurface)
            requestBuilder.addTarget(reader.surface)
            
            // Enable continuous auto-focus for barcode scanning
            requestBuilder.set(
                CaptureRequest.CONTROL_AF_MODE,
                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
            )
            
            // Enable auto-exposure
            requestBuilder.set(
                CaptureRequest.CONTROL_AE_MODE,
                CaptureRequest.CONTROL_AE_MODE_ON
            )
            
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
            
            Log.d(TAG, "Camera2 preview started with AF/AE/AWB")
            
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
}
