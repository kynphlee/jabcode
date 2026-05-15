package com.jabcode.test.calibration;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import com.jabcode.test.R;
import org.json.JSONException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class CalibrationActivity extends AppCompatActivity {
    private static final String TAG = "CalibrationActivity";
    private static final int REQUEST_CAMERA_PERMISSION = 1001;
    private static final int REQUEST_IMAGE_CAPTURE = 1002;
    
    private enum CalibrationStep {
        WELCOME,
        GENERATE,
        PRINT,
        CAPTURE,
        ANALYZE,
        RESULTS
    }
    
    private CalibrationStep currentStep = CalibrationStep.WELCOME;
    private CalibrationProfileManager profileManager;
    
    private View welcomeView;
    private View generateView;
    private View printView;
    private View captureView;
    private View analyzeView;
    private View resultsView;
    
    private TextView statusText;
    private ImageView patternPreview;
    private ProgressBar progressBar;
    private Button actionButton;
    private TextView resultsText;
    
    private Bitmap generatedPattern;
    private File patternFile;
    private File capturedFile;
    private CalibrationProfile generatedProfile;
    private String printerModel = "Unknown Printer";
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calibration);
        
        profileManager = new CalibrationProfileManager(this);
        
        initializeViews();
        showStep(CalibrationStep.WELCOME);
    }
    
    private void initializeViews() {
        welcomeView = findViewById(R.id.welcome_layout);
        generateView = findViewById(R.id.generate_layout);
        printView = findViewById(R.id.print_layout);
        captureView = findViewById(R.id.capture_layout);
        analyzeView = findViewById(R.id.analyze_layout);
        resultsView = findViewById(R.id.results_layout);
        
        statusText = findViewById(R.id.status_text);
        patternPreview = findViewById(R.id.pattern_preview);
        progressBar = findViewById(R.id.progress_bar);
        actionButton = findViewById(R.id.action_button);
        resultsText = findViewById(R.id.results_text);
        
        actionButton.setOnClickListener(v -> handleAction());
        
        findViewById(R.id.btn_start_calibration).setOnClickListener(v -> 
            showStep(CalibrationStep.GENERATE));
        findViewById(R.id.btn_manage_profiles).setOnClickListener(v -> 
            startActivity(new Intent(this, ProfileManagerActivity.class)));
    }
    
    private void showStep(CalibrationStep step) {
        currentStep = step;
        
        welcomeView.setVisibility(View.GONE);
        generateView.setVisibility(View.GONE);
        printView.setVisibility(View.GONE);
        captureView.setVisibility(View.GONE);
        analyzeView.setVisibility(View.GONE);
        resultsView.setVisibility(View.GONE);
        
        switch (step) {
            case WELCOME:
                welcomeView.setVisibility(View.VISIBLE);
                setTitle("JABCode Calibration");
                break;
                
            case GENERATE:
                generateView.setVisibility(View.VISIBLE);
                setTitle("Generate Test Pattern");
                statusText.setText("Ready to generate calibration pattern");
                actionButton.setText("Generate Pattern");
                break;
                
            case PRINT:
                printView.setVisibility(View.VISIBLE);
                setTitle("Print Pattern");
                patternPreview.setImageBitmap(generatedPattern);
                actionButton.setText("I've Printed It");
                break;
                
            case CAPTURE:
                captureView.setVisibility(View.VISIBLE);
                setTitle("Capture Printed Pattern");
                statusText.setText("Position camera over printed pattern");
                actionButton.setText("Capture Photo");
                break;
                
            case ANALYZE:
                analyzeView.setVisibility(View.VISIBLE);
                setTitle("Analyzing Colors");
                progressBar.setVisibility(View.VISIBLE);
                statusText.setText("Analyzing color samples...");
                break;
                
            case RESULTS:
                resultsView.setVisibility(View.VISIBLE);
                setTitle("Calibration Results");
                displayResults();
                actionButton.setText("Save Profile");
                break;
        }
    }
    
    private void handleAction() {
        switch (currentStep) {
            case GENERATE:
                generatePattern();
                break;
                
            case PRINT:
                showStep(CalibrationStep.CAPTURE);
                break;
                
            case CAPTURE:
                capturePattern();
                break;
                
            case RESULTS:
                saveProfile();
                break;
        }
    }
    
    private void generatePattern() {
        progressBar.setVisibility(View.VISIBLE);
        actionButton.setEnabled(false);
        statusText.setText("Generating 4,096 color pattern...");
        
        new Thread(() -> {
            try {
                generatedPattern = CalibrationPatternGenerator.generateTestPattern();
                
                patternFile = savePatternToFile(generatedPattern);
                
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    statusText.setText("Pattern generated successfully!");
                    Toast.makeText(this, "Pattern saved to: " + patternFile.getAbsolutePath(), 
                        Toast.LENGTH_LONG).show();
                    
                    showStep(CalibrationStep.PRINT);
                });
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to generate pattern", e);
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    actionButton.setEnabled(true);
                    Toast.makeText(this, "Generation failed: " + e.getMessage(), 
                        Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }
    
    private File savePatternToFile(Bitmap bitmap) throws IOException {
        File picturesDir = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_PICTURES);
        File calibrationDir = new File(picturesDir, "JABCode_Calibration");
        if (!calibrationDir.exists()) {
            calibrationDir.mkdirs();
        }
        
        String filename = "calibration_pattern_" + System.currentTimeMillis() + ".png";
        File file = new File(calibrationDir, filename);
        
        try (FileOutputStream out = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
        
        MediaStore.Images.Media.insertImage(
            getContentResolver(), 
            file.getAbsolutePath(), 
            filename, 
            "JABCode Calibration Test Pattern"
        );
        
        Log.i(TAG, "Pattern saved: " + file.getAbsolutePath());
        return file;
    }
    
    private void capturePattern() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) 
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, 
                new String[]{Manifest.permission.CAMERA}, 
                REQUEST_CAMERA_PERMISSION);
            return;
        }
        
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            try {
                capturedFile = createImageFile();
                Uri photoURI = FileProvider.getUriForFile(this,
                    "com.jabcode.test.fileprovider",
                    capturedFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
            } catch (IOException e) {
                Log.e(TAG, "Failed to create image file", e);
                Toast.makeText(this, "Camera error", Toast.LENGTH_SHORT).show();
            }
        }
    }
    
    private File createImageFile() throws IOException {
        String filename = "calibration_capture_" + System.currentTimeMillis() + ".jpg";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        return new File(storageDir, filename);
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            showStep(CalibrationStep.ANALYZE);
            analyzeCapture();
        }
    }
    
    private void analyzeCapture() {
        new Thread(() -> {
            try {
                Bitmap capturedBitmap = MediaStore.Images.Media.getBitmap(
                    getContentResolver(), Uri.fromFile(capturedFile));
                
                String deviceModel = android.os.Build.MODEL;
                
                CalibrationAnalyzer.AnalysisResult result = 
                    CalibrationAnalyzer.analyzeCapturedPattern(
                        capturedBitmap, printerModel, deviceModel);
                
                runOnUiThread(() -> {
                    if (result.success) {
                        generatedProfile = result.profile;
                        showStep(CalibrationStep.RESULTS);
                    } else {
                        Toast.makeText(this, "Analysis failed: " + result.errorMessage, 
                            Toast.LENGTH_LONG).show();
                        showStep(CalibrationStep.CAPTURE);
                    }
                });
                
            } catch (Exception e) {
                Log.e(TAG, "Analysis failed", e);
                runOnUiThread(() -> {
                    Toast.makeText(this, "Analysis error: " + e.getMessage(), 
                        Toast.LENGTH_SHORT).show();
                    showStep(CalibrationStep.CAPTURE);
                });
            }
        }).start();
    }
    
    private void displayResults() {
        if (generatedProfile == null) {
            resultsText.setText("No profile generated");
            return;
        }
        
        CalibrationProfile.QualityMetrics quality = generatedProfile.getQuality();
        
        StringBuilder sb = new StringBuilder();
        sb.append("Calibration Profile Generated\n\n");
        sb.append("Printer: ").append(generatedProfile.getPrinter().model).append("\n");
        sb.append("Device: ").append(generatedProfile.getCamera().device).append("\n\n");
        
        sb.append("Quality: ").append(quality.getQualityLevel()).append("\n");
        sb.append("Min Separation: ").append(String.format("%.2f", quality.minSeparation)).append("\n");
        sb.append("Avg Separation: ").append(String.format("%.2f", quality.averageColorDistance)).append("\n");
        sb.append("Red↔Magenta: ").append(String.format("%.2f", quality.redMagentaSeparation)).append("\n\n");
        
        sb.append("Calibrated Colors:\n");
        CalibrationProfile.ColorMapping mapping = generatedProfile.getColorMapping();
        sb.append("  Red: ").append(formatColor(mapping.red.calibrated)).append("\n");
        sb.append("  Green: ").append(formatColor(mapping.green.calibrated)).append("\n");
        sb.append("  Blue: ").append(formatColor(mapping.blue.calibrated)).append("\n");
        sb.append("  White: ").append(formatColor(mapping.white.calibrated)).append("\n");
        sb.append("  Magenta: ").append(formatColor(mapping.magenta.calibrated)).append("\n");
        
        resultsText.setText(sb.toString());
        
        if ("Poor".equals(quality.getQualityLevel())) {
            Toast.makeText(this, 
                "Warning: Poor quality calibration may not improve scanning", 
                Toast.LENGTH_LONG).show();
        }
    }
    
    private String formatColor(CalibrationProfile.RGBColor color) {
        return String.format("RGB(%d, %d, %d)", color.r, color.g, color.b);
    }
    
    private void saveProfile() {
        if (generatedProfile == null) {
            Toast.makeText(this, "No profile to save", Toast.LENGTH_SHORT).show();
            return;
        }
        
        try {
            profileManager.saveProfile(generatedProfile);
            profileManager.setActiveProfile(generatedProfile);
            
            Toast.makeText(this, "Profile saved and activated", Toast.LENGTH_SHORT).show();
            finish();
            
        } catch (IOException | JSONException e) {
            Log.e(TAG, "Failed to save profile", e);
            Toast.makeText(this, "Save failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, 
            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                capturePattern();
            } else {
                Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
