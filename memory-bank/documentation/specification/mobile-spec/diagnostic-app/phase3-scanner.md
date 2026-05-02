# Phase 3: Scanner Screen Implementation

**Duration:** 5 days  
**Dependencies:** Phase 1 complete  
**Status:** ⬜ Not Started

---

## Overview

Implement the scanner UI matching `scanner-interface.html` prototype with full camera integration.

**Coverage Target:** 75%+ (22 tests: 8 screenshot + 7 interaction + 7 integration)

---

## Components to Implement

### **1. CameraPreview** (Day 1)
- CameraX integration
- Runtime permission handling
- Preview use case
- Image analysis for decoding

### **2. ScanTargetOverlay** (Day 2)
- Corner guides (40dp, 3dp stroke)
- Pulsing animation (600ms cycle)
- Detection state (green + scale)
- ScanningLine animation (2s vertical)

### **3. QualityIndicators** (Day 3)
- Brightness, Focus, Contrast bars
- Real-time camera analysis
- Color-coded (red/yellow/green)
- 60x4dp progress bars

### **4. ResultBottomSheet** (Day 4)
- ModalBottomSheet implementation
- ResultHeader (icon + title + close)
- ValidationBadges (PKI, JWT, Expiry)
- DetailSections (3 sections)
- Action buttons (Accept, Scan Again)

---

## Camera Integration

```kotlin
@Composable
fun ScannerScreen(
    viewModel: ScannerViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    val cameraProviderFuture = remember {
        ProcessCameraProvider.getInstance(context)
    }
    
    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).apply {
                implementationMode = PreviewView.ImplementationMode.PERFORMANCE
            }.also { previewView ->
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build()
                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also { analysis ->
                            analysis.setAnalyzer(
                                Executors.newSingleThreadExecutor(),
                                viewModel.imageAnalyzer
                            )
                        }
                    
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageAnalysis
                    )
                    
                    preview.setSurfaceProvider(previewView.surfaceProvider)
                }, ContextCompat.getMainExecutor(ctx))
            }
        }
    )
}
```

---

## Testing Strategy

### **Screenshot Tests (8)**
- Scanning state
- Detected state
- Result panel (success/error)
- Quality indicators (high/medium/low)

### **Interaction Tests (7)**
- Torch toggle
- Result panel expand/collapse
- Accept button
- Scan again button
- Quality indicator updates

### **Integration Tests (7)**
- Full scan flow
- Authentication validation
- Error handling
- Camera lifecycle

---

**Reference:** `@/swift-java-wrapper/android/ui-prototypes/scanner-interface.html`  
**Component Spec:** `@/swift-java-wrapper/android/ui-prototypes/SCANNER_COMPONENTS.md`

---

**Last Updated:** 2026-05-02
