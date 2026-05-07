package com.jabauth.diagnostic.ui.scanner

import android.view.ViewGroup
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import java.util.concurrent.Executors
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * CameraPreview - Live camera feed using CameraX
 * 
 * Displays a real-time camera preview for JABCode scanning.
 * 
 * @param imageAnalyzer Optional analyzer for processing camera frames
 * @param isTorchOn Whether torch (flashlight) should be enabled
 * @param onCameraReady Callback when camera is bound (provides CameraControl)
 * @param modifier Modifier for the preview
 */
@Composable
fun CameraPreview(
    imageAnalyzer: ImageAnalysis.Analyzer? = null,
    isTorchOn: Boolean = false,
    onCameraReady: (CameraControl) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    
    // Store camera control for torch updates without rebinding
    var cameraControl by remember { mutableStateOf<CameraControl?>(null) }
    
    // Update torch when state changes (without rebinding camera)
    LaunchedEffect(isTorchOn) {
        cameraControl?.enableTorch(isTorchOn)
    }
    
    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                scaleType = PreviewView.ScaleType.FILL_CENTER
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            }
        },
        modifier = modifier,
        update = { previewView ->
            // Only bind camera once when preview view is ready
            if (cameraControl == null) {
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build()
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                
                preview.setSurfaceProvider(previewView.surfaceProvider)
                
                try {
                    cameraProvider.unbindAll()
                    
                    // Build use cases
                    val useCases = mutableListOf<androidx.camera.core.UseCase>(preview)
                    
                    // Add image analysis if provided
                    if (imageAnalyzer != null) {
                        val imageAnalysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                            .also { it.setAnalyzer(executor, imageAnalyzer) }
                        useCases.add(imageAnalysis)
                    }
                    
                    // Bind all use cases
                    val camera = cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        *useCases.toTypedArray()
                    )
                    
                    // Store camera control for future torch updates
                    cameraControl = camera.cameraControl
                    
                    // Set initial torch state
                    camera.cameraControl.enableTorch(isTorchOn)
                    
                    // Notify caller of camera ready
                    onCameraReady(camera.cameraControl)
                    
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    )
    
    // Cleanup when composable leaves composition
    DisposableEffect(Unit) {
        onDispose {
            try {
                cameraProviderFuture.get().unbindAll()
                executor.shutdown()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

/**
 * Permission rationale UI
 */
@Composable
fun CameraPermissionRationale(
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(4f/3f),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Camera Permission Required",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "This app needs camera access to scan JABCodes.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Button(onClick = onRequestPermission) {
                Text("Grant Permission")
            }
        }
    }
}

/**
 * Permission denied UI
 */
@Composable
fun CameraPermissionDenied(
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(4f/3f),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Camera Access Denied",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "Please enable camera permission in app settings to scan JABCodes.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Button(onClick = onRequestPermission) {
                Text("Request Again")
            }
        }
    }
}
