package com.jabcode.test;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.FocusMeteringAction;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.MeteringPoint;
import androidx.camera.core.MeteringPointFactory;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.camera.camera2.interop.Camera2Interop;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.hardware.camera2.CaptureRequest;
import android.os.Handler;
import android.os.Looper;
import android.util.Range;

import java.util.concurrent.TimeUnit;

import com.google.common.util.concurrent.ListenableFuture;
import com.jabcode.JABCodeMobile;
import com.jabcode.test.databinding.ActivityScannerBinding;
import com.jabcode.test.calibration.CalibrationProfileManager;
import com.jabcode.test.calibration.CalibrationProfile;

import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ScannerActivity extends AppCompatActivity {
    private static final String TAG = "JABCodeScanner";
    private static final int CAMERA_PERMISSION_REQUEST = 1001;
    
    private ActivityScannerBinding binding;
    private ExecutorService cameraExecutor;
    private Camera camera;
    private CameraControlManager cameraControlManager;
    private AdaptiveCameraOptimizer cameraOptimizer;
    private ScannerSettings settings;
    private FeedbackManager feedbackManager;
    private long lastDecodeAttempt = 0;
    private static final long DECODE_THROTTLE_MS = 100; // 10 fps max

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityScannerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        cameraExecutor = Executors.newSingleThreadExecutor();
        cameraControlManager = new CameraControlManager();
        cameraOptimizer = new AdaptiveCameraOptimizer(cameraControlManager);
        settings = new ScannerSettings(this);
        feedbackManager = new FeedbackManager(this, settings);
        
        loadCalibrationProfile();
        setupControls();
        checkCameraPermission();
    }
    
    private void loadCalibrationProfile() {
        CalibrationProfileManager profileManager = new CalibrationProfileManager(this);
        if (profileManager.hasActiveProfile()) {
            try {
                CalibrationProfile profile = profileManager.getActiveProfile();
                String json = profile.toJson();
                
                boolean loaded = JABCodeMobile.loadCalibration(json);
                if (loaded) {
                    Log.i(TAG, "Loaded calibration profile: " + profile.getPrinter().model);
                    Toast.makeText(this, "Using calibration: " + profile.getPrinter().model, 
                        Toast.LENGTH_SHORT).show();
                } else {
                    Log.w(TAG, "Failed to load calibration profile");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error loading calibration", e);
            }
        } else {
            Log.d(TAG, "No active calibration profile");
            JABCodeMobile.clearCalibration();
        }
    }

    private void setupControls() {
        binding.bottomControlSheet.setOnTorchClickListener(this::toggleTorch);
        binding.bottomControlSheet.setOnGalleryClickListener(this::openGallery);
        binding.bottomControlSheet.setOnSettingsClickListener(this::openSettings);
        
        binding.viewfinderOverlay.setState(ViewfinderOverlay.State.IDLE);
    }

    private void checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    CAMERA_PERMISSION_REQUEST);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera();
            } else {
                Toast.makeText(this, "Camera permission required", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                bindCameraUseCases(cameraProvider);
            } catch (Exception e) {
                Log.e(TAG, "Camera initialization failed", e);
                Toast.makeText(this, "Camera error: " + e.getMessage(), 
                        Toast.LENGTH_LONG).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindCameraUseCases(ProcessCameraProvider cameraProvider) {
        // Preview with Camera2Interop for stable auto-exposure
        Preview.Builder previewBuilder = new Preview.Builder();
        Camera2Interop.Extender<Preview> previewExtender = new Camera2Interop.Extender<>(previewBuilder);
        
        // Set stable FPS range to prevent exposure hunting
        previewExtender.setCaptureRequestOption(
            CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
            new Range<>(30, 30)
        );
        
        // Enable auto-exposure mode
        previewExtender.setCaptureRequestOption(
            CaptureRequest.CONTROL_AE_MODE,
            CaptureRequest.CONTROL_AE_MODE_ON
        );
        
        // Use auto white balance without lock (let it converge naturally)
        previewExtender.setCaptureRequestOption(
            CaptureRequest.CONTROL_AWB_MODE,
            CaptureRequest.CONTROL_AWB_MODE_AUTO
        );
        
        // Disable auto-exposure lock to allow dynamic adjustment
        previewExtender.setCaptureRequestOption(
            CaptureRequest.CONTROL_AE_LOCK,
            false
        );
        
        Preview preview = previewBuilder.build();
        preview.setSurfaceProvider(binding.previewView.getSurfaceProvider());

        // Image analysis for JABCode scanning
        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();
        
        imageAnalysis.setAnalyzer(cameraExecutor, this::analyzeImage);

        // Camera selector
        CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

        try {
            // Unbind all before rebinding
            cameraProvider.unbindAll();

            // Bind use cases to camera
            camera = cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageAnalysis
            );
            
            // Register camera with control manager
            cameraControlManager.setCamera(camera);
            
            // Boost exposure for better JABCode visibility
            cameraControlManager.setExposureCompensation(1);

            Log.i(TAG, "Camera bound successfully");
            updateStatus("Ready to scan");

        } catch (Exception e) {
            Log.e(TAG, "Use case binding failed", e);
            Toast.makeText(this, "Camera binding error", Toast.LENGTH_LONG).show();
        }
    }

    private void analyzeImage(ImageProxy image) {
        // Throttle decode attempts
        long now = System.currentTimeMillis();
        if (now - lastDecodeAttempt < DECODE_THROTTLE_MS) {
            image.close();
            return;
        }
        lastDecodeAttempt = now;

        try {
            runOnUiThread(() -> binding.viewfinderOverlay.setState(ViewfinderOverlay.State.SCANNING));
            
            // Convert ImageProxy to Bitmap
            Bitmap bitmap = imageProxyToBitmap(image);
            
            if (bitmap != null) {
                // Decode using JABCode native library
                String result = JABCodeMobile.decodeFromBitmap(bitmap);
                
                if (result != null && !result.isEmpty()) {
                    cameraOptimizer.onDecodeSuccess(bitmap, null);
                    runOnUiThread(() -> {
                        binding.viewfinderOverlay.setState(ViewfinderOverlay.State.SUCCESS);
                        onJABCodeDetected(result);
                    });
                } else {
                    cameraOptimizer.onDecodeFailure(bitmap);
                    runOnUiThread(() -> binding.viewfinderOverlay.setState(ViewfinderOverlay.State.IDLE));
                }
                
                bitmap.recycle();
            }
        } catch (Exception e) {
            Log.e(TAG, "Decode error", e);
            runOnUiThread(() -> binding.viewfinderOverlay.setState(ViewfinderOverlay.State.ERROR));
        } finally {
            image.close();
        }
    }

    private Bitmap imageProxyToBitmap(ImageProxy image) {
        // For now, use a simple conversion
        // TODO: Optimize this conversion for performance
        ImageProxy.PlaneProxy[] planes = image.getPlanes();
        ByteBuffer yBuffer = planes[0].getBuffer();
        ByteBuffer uBuffer = planes[1].getBuffer();
        ByteBuffer vBuffer = planes[2].getBuffer();

        int ySize = yBuffer.remaining();
        int uSize = uBuffer.remaining();
        int vSize = vBuffer.remaining();

        byte[] nv21 = new byte[ySize + uSize + vSize];
        yBuffer.get(nv21, 0, ySize);
        vBuffer.get(nv21, ySize, vSize);
        uBuffer.get(nv21, ySize + vSize, uSize);

        android.graphics.YuvImage yuvImage = new android.graphics.YuvImage(
                nv21,
                android.graphics.ImageFormat.NV21,
                image.getWidth(),
                image.getHeight(),
                null
        );

        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        yuvImage.compressToJpeg(new android.graphics.Rect(0, 0, 
                image.getWidth(), image.getHeight()), 100, out);
        
        byte[] imageBytes = out.toByteArray();
        return android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
    }

    private void onJABCodeDetected(String data) {
        Log.i(TAG, "JABCode detected: " + data);
        
        binding.viewfinderOverlay.setState(ViewfinderOverlay.State.SUCCESS);
        binding.resultCard.setVisibility(View.VISIBLE);
        binding.resultText.setText(data);
        binding.metadataText.setText("Decoded successfully");
        updateStatus("JABCode found!");
        
        feedbackManager.provideSuccessFeedback();
        feedbackManager.playSuccessSound();
    }

    private void toggleTorch() {
        boolean newState = !cameraControlManager.isTorchEnabled();
        boolean success = cameraControlManager.setTorchEnabled(newState);
        
        if (success) {
            binding.bottomControlSheet.setTorchEnabled(newState);
            updateStatus(newState ? "Torch ON" : "Torch OFF");
        } else {
            Toast.makeText(this, "Torch not available", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void openGallery() {
        Toast.makeText(this, "Gallery import coming soon", Toast.LENGTH_SHORT).show();
    }
    
    private void openSettings() {
        startActivity(new android.content.Intent(this, SettingsActivity.class));
    }

    private void setupCenterMeteringLock() {
        if (camera == null) {
            return;
        }
        
        try {
            // Create center metering point where viewfinder overlay is
            MeteringPointFactory factory = binding.previewView.getMeteringPointFactory();
            MeteringPoint centerPoint = factory.createPoint(0.5f, 0.5f);
            
            // Lock focus and metering to center region with long auto-cancel
            FocusMeteringAction action = new FocusMeteringAction.Builder(centerPoint)
                    .setAutoCancelDuration(10, TimeUnit.SECONDS)
                    .build();
            
            camera.getCameraControl().startFocusAndMetering(action);
            
            // Re-trigger every 10 seconds to maintain lock
            new Handler(Looper.getMainLooper()).postDelayed(this::setupCenterMeteringLock, 10000);
            
            Log.d(TAG, "Center metering lock established");
        } catch (Exception e) {
            Log.e(TAG, "Failed to setup metering lock", e);
        }
    }
    
    private void updateStatus(String message) {
        runOnUiThread(() -> binding.statusText.setText(message));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
        feedbackManager.release();
    }
}
