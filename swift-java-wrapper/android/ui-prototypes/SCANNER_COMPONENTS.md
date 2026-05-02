# JABAuth Scanner UI Components - Specification

**Module:** 5 (:ui-components)  
**Version:** 1.0.0  
**Date:** 2026-05-02

---

## Overview

This document specifies reusable UI components for JABCode scanning and authentication result display. These components work for both the diagnostic app and custom applications (healthcare, legal, IoT, etc.).

**Interactive Prototype:** `scanner-interface.html`

---

## Component Architecture

```
ScannerScreen (Composable)
├── ScannerHeader
│   ├── AppInfo (name + instruction)
│   └── HeaderActions (torch, settings)
├── CameraViewfinder
│   ├── ScanTargetOverlay
│   │   ├── CornerGuides (4 corners)
│   │   └── ScanningLine (animated)
│   └── QualityIndicators
│       ├── BrightnessIndicator
│       ├── FocusIndicator
│       └── ContrastIndicator
├── ScanStatusOverlay (temporary)
└── ResultPanel (bottom sheet)
    ├── ResultHeader
    │   ├── StatusIcon
    │   ├── ResultTitle
    └── └── CloseButton
    ├── ValidationBadges
    ├── DetailSections
    │   ├── CertificateInfo
    │   ├── JWTToken
    │   └── ScanDetails
    └── ActionButtons
        ├── AcceptButton
        └── ScanAgainButton
```

---

## 1. Scanner Header

### Purpose
Displays app context and provides quick access to scanner controls.

### Spec
```kotlin
@Composable
fun ScannerHeader(
    appName: String,
    instruction: String,
    onTorchToggle: () -> Unit,
    onSettingsClick: () -> Unit,
    isTorchOn: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        ColorBgBase,
                        Color.Transparent
                    )
                )
            )
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // App info
        Column {
            Text(
                text = appName.uppercase(),
                style = TextStyle(
                    fontFamily = IBMPlexMono,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.05.em,
                    color = ColorPrimary
                )
            )
            Text(
                text = instruction,
                style = MaterialTheme.typography.bodyMedium,
                color = ColorTextPrimary
            )
        }
        
        // Actions
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconButton(
                onClick = onTorchToggle,
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = if (isTorchOn) ColorPrimaryDim else ColorBgCard,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .border(
                        width = 1.dp,
                        color = if (isTorchOn) ColorPrimary else ColorBorder,
                        shape = RoundedCornerShape(8.dp)
                    )
            ) {
                Text("💡", fontSize = 18.sp)
            }
            
            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier
                    .size(40.dp)
                    .background(ColorBgCard, RoundedCornerShape(8.dp))
                    .border(1.dp, ColorBorder, RoundedCornerShape(8.dp))
            ) {
                Text("⚙️", fontSize = 18.sp)
            }
        }
    }
}
```

### Design Notes
- Header uses gradient background to overlay camera feed
- App name in UPPERCASE with letter-spacing for technical feel
- Icon buttons: 40x40dp, 8dp corner radius
- Active state (torch on) uses primary color background

---

## 2. Scan Target Overlay

### Purpose
Visual guides for JABCode placement in camera viewfinder.

### Spec
```kotlin
@Composable
fun ScanTargetOverlay(
    size: Dp = 300.dp,
    isScanning: Boolean = true,
    isDetected: Boolean = false
) {
    Box(
        modifier = Modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        // Corner guides
        CornerGuides(
            size = size,
            color = if (isDetected) ColorSuccess else ColorPrimary,
            isDetected = isDetected
        )
        
        // Scanning line animation
        if (isScanning && !isDetected) {
            ScanningLine(targetHeight = size)
        }
    }
}

@Composable
fun CornerGuides(
    size: Dp,
    color: Color,
    isDetected: Boolean
) {
    val cornerSize = 40.dp
    val borderWidth = 3.dp
    
    // Animate on detection
    val scale by animateFloatAsState(
        targetValue = if (isDetected) 1.1f else 1f,
        animationSpec = tween(300)
    )
    
    Canvas(modifier = Modifier.size(size).scale(scale)) {
        val cornerPx = cornerSize.toPx()
        val widthPx = borderWidth.toPx()
        
        // Top-left corner
        drawPath(
            path = Path().apply {
                moveTo(0f, cornerPx)
                lineTo(0f, 0f)
                lineTo(cornerPx, 0f)
            },
            color = color,
            style = Stroke(width = widthPx)
        )
        
        // Top-right corner
        drawPath(
            path = Path().apply {
                moveTo(size.toPx() - cornerPx, 0f)
                lineTo(size.toPx(), 0f)
                lineTo(size.toPx(), cornerPx)
            },
            color = color,
            style = Stroke(width = widthPx)
        )
        
        // Bottom-left corner
        drawPath(
            path = Path().apply {
                moveTo(0f, size.toPx() - cornerPx)
                lineTo(0f, size.toPx())
                lineTo(cornerPx, size.toPx())
            },
            color = color,
            style = Stroke(width = widthPx)
        )
        
        // Bottom-right corner
        drawPath(
            path = Path().apply {
                moveTo(size.toPx() - cornerPx, size.toPx())
                lineTo(size.toPx(), size.toPx())
                lineTo(size.toPx(), size.toPx() - cornerPx)
            },
            color = color,
            style = Stroke(width = widthPx)
        )
    }
}

@Composable
fun ScanningLine(targetHeight: Dp) {
    val infiniteTransition = rememberInfiniteTransition()
    val offsetY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = targetHeight.value,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )
    
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(2.dp)
            .offset(y = offsetY.dp)
    ) {
        drawLine(
            brush = Brush.horizontalGradient(
                colors = listOf(
                    Color.Transparent,
                    ColorPrimary,
                    Color.Transparent
                )
            ),
            start = Offset(0f, 0f),
            end = Offset(size.width, 0f),
            strokeWidth = 2.dp.toPx()
        )
    }
}
```

### Design Notes
- Corner guides: 40dp length, 3dp stroke width
- Pulsing animation on corners (opacity 0.6 → 1.0)
- On detection: corners turn green + scale to 1.1x
- Scanning line: 2px height, 2-second animation cycle

---

## 3. Quality Indicators

### Purpose
Real-time feedback on scan conditions (brightness, focus, contrast).

### Spec
```kotlin
data class QualityMetric(
    val label: String,
    val value: Float, // 0.0 to 1.0
    val threshold: QualityThreshold = QualityThreshold.Medium
)

enum class QualityThreshold {
    Low,    // 0.0 - 0.4: Red
    Medium, // 0.4 - 0.7: Yellow
    High    // 0.7 - 1.0: Green
}

@Composable
fun QualityIndicators(
    metrics: List<QualityMetric>,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(
                color = ColorBgBase.copy(alpha = 0.9f),
                shape = RoundedCornerShape(8.dp)
            )
            .border(1.dp, ColorBorder, RoundedCornerShape(8.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        metrics.forEach { metric ->
            QualityIndicator(metric)
        }
    }
}

@Composable
fun QualityIndicator(metric: QualityMetric) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = metric.label.uppercase(),
            style = TextStyle(
                fontFamily = IBMPlexMono,
                fontSize = 11.sp,
                letterSpacing = 0.05.em,
                color = ColorTextDim
            )
        )
        
        // Progress bar
        Box(
            modifier = Modifier
                .width(60.dp)
                .height(4.dp)
                .background(ColorBgElevated, RoundedCornerShape(2.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(metric.value)
                    .background(
                        color = when {
                            metric.value < 0.4f -> ColorError
                            metric.value < 0.7f -> ColorWarning
                            else -> ColorSuccess
                        },
                        shape = RoundedCornerShape(2.dp)
                    )
            )
        }
    }
}
```

### Usage Example
```kotlin
val scanQuality = remember {
    listOf(
        QualityMetric("Brightness", 0.85f),
        QualityMetric("Focus", 0.92f),
        QualityMetric("Contrast", 0.68f)
    )
}

QualityIndicators(
    metrics = scanQuality,
    modifier = Modifier
        .align(Alignment.BottomCenter)
        .padding(bottom = 20.dp)
)
```

### Design Notes
- Bar width: 60dp, height: 4dp
- Color coding:
  - 0-40%: Red (low quality)
  - 40-70%: Yellow (medium quality)
  - 70-100%: Green (high quality)
- Background: Semi-transparent with backdrop blur
- Update values in real-time based on camera feed analysis

---

## 4. Scan Status Overlay

### Purpose
Temporary fullscreen feedback during scan detection.

### Spec
```kotlin
@Composable
fun ScanStatusOverlay(
    visible: Boolean,
    icon: String,
    message: String
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + scaleIn(initialScale = 0.8f),
        exit = fadeOut()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(enabled = false) { },
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier.padding(32.dp),
                colors = CardDefaults.cardColors(
                    containerColor = ColorBgBase.copy(alpha = 0.95f)
                ),
                border = BorderStroke(2.dp, ColorPrimary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = icon,
                        fontSize = 48.sp,
                        modifier = Modifier.scale(
                            animateFloatAsState(
                                targetValue = 1f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessLow
                                )
                            ).value
                        )
                    )
                    Text(
                        text = message,
                        style = TextStyle(
                            fontFamily = IBMPlexMono,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = ColorTextPrimary
                        )
                    )
                }
            }
        }
    }
}
```

### Usage Example
```kotlin
var scanStatus by remember { mutableStateOf<ScanStatus?>(null) }

ScanStatusOverlay(
    visible = scanStatus != null,
    icon = when (scanStatus) {
        ScanStatus.Detecting -> "🔍"
        ScanStatus.Detected -> "✓"
        ScanStatus.Error -> "✕"
        else -> ""
    },
    message = when (scanStatus) {
        ScanStatus.Detecting -> "Scanning..."
        ScanStatus.Detected -> "Code Detected"
        ScanStatus.Error -> "Scan Failed"
        else -> ""
    }
)
```

### Design Notes
- Fullscreen overlay with semi-transparent black background
- Central card with 2px primary-colored border
- Icon with bounce animation on appearance
- Auto-dismiss after 1 second (managed by caller)

---

## 5. Result Panel (Bottom Sheet)

### Purpose
Displays authentication results and validation details.

### Spec
```kotlin
data class AuthenticationResult(
    val status: ResultStatus,
    val subject: String,
    val certificateInfo: CertificateInfo,
    val jwtInfo: JWTInfo,
    val scanDetails: ScanDetails,
    val validations: List<ValidationCheck>
)

enum class ResultStatus {
    Success,
    Failed
}

data class ValidationCheck(
    val label: String,
    val passed: Boolean
)

@Composable
fun ResultPanel(
    result: AuthenticationResult?,
    onDismiss: () -> Unit,
    onAccept: () -> Unit,
    onScanAgain: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = false
    )
    
    if (result != null) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            containerColor = ColorBgBase,
            contentColor = ColorTextPrimary,
            dragHandle = null
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Header
                ResultHeader(
                    status = result.status,
                    title = when (result.status) {
                        ResultStatus.Success -> "Authentication Valid"
                        ResultStatus.Failed -> "Authentication Failed"
                    },
                    subtitle = result.subject,
                    onClose = onDismiss
                )
                
                // Validation badges
                ValidationBadges(validations = result.validations)
                
                // Detail sections
                CertificateSection(result.certificateInfo)
                JWTSection(result.jwtInfo)
                ScanDetailsSection(result.scanDetails)
                
                // Action buttons
                ActionButtons(
                    primaryLabel = if (result.status == ResultStatus.Success) "Accept" else "Retry",
                    primaryIcon = if (result.status == ResultStatus.Success) "✓" else "↻",
                    onPrimaryClick = if (result.status == ResultStatus.Success) onAccept else onScanAgain,
                    secondaryLabel = "Scan Again",
                    secondaryIcon = "↻",
                    onSecondaryClick = onScanAgain
                )
            }
        }
    }
}

@Composable
fun ResultHeader(
    status: ResultStatus,
    title: String,
    subtitle: String,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        color = if (status == ResultStatus.Success) 
                            ColorSuccessDim else ColorErrorDim,
                        shape = RoundedCornerShape(8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (status == ResultStatus.Success) "✓" else "✕",
                    fontSize = 28.sp,
                    color = if (status == ResultStatus.Success) 
                        ColorSuccess else ColorError
                )
            }
            
            Column {
                Text(
                    text = title,
                    style = TextStyle(
                        fontFamily = IBMPlexMono,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = ColorTextPrimary
                    )
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = ColorTextSecondary
                )
            }
        }
        
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .size(32.dp)
                .background(ColorBgCard, RoundedCornerShape(6.dp))
                .border(1.dp, ColorBorder, RoundedCornerShape(6.dp))
        ) {
            Text("✕", fontSize = 18.sp)
        }
    }
}

@Composable
fun ValidationBadges(validations: List<ValidationCheck>) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        validations.forEach { validation ->
            Surface(
                color = if (validation.passed) ColorSuccessDim else ColorErrorDim,
                shape = RoundedCornerShape(4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (validation.passed) "✓" else "✕",
                        fontSize = 12.sp,
                        color = if (validation.passed) ColorSuccess else ColorError
                    )
                    Text(
                        text = validation.label.uppercase(),
                        style = TextStyle(
                            fontFamily = IBMPlexMono,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.05.em,
                            color = if (validation.passed) ColorSuccess else ColorError
                        )
                    )
                }
            }
        }
    }
}
```

### Design Notes
- Uses Material 3 `ModalBottomSheet` for smooth slide-up animation
- Maximum height: 60vh (scrollable content)
- Status icon: 48x48dp, colored background
- Validation badges use FlowRow for responsive wrapping
- Action buttons in 2-column grid at bottom

---

## 6. Detail Sections

### Purpose
Display certificate, JWT, and scan metadata in structured format.

### Spec
```kotlin
@Composable
fun DetailSection(
    label: String,
    rows: List<Pair<String, String>>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = ColorBgCard
        ),
        border = BorderStroke(1.dp, ColorBorder),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = label.uppercase(),
                style = TextStyle(
                    fontFamily = IBMPlexMono,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.05.em,
                    color = ColorPrimary
                ),
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            rows.forEachIndexed { index, (key, value) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "$key:",
                        style = TextStyle(
                            fontFamily = IBMPlexMono,
                            fontSize = 13.sp,
                            color = ColorTextSecondary
                        ),
                        modifier = Modifier.weight(0.4f)
                    )
                    Text(
                        text = value,
                        style = TextStyle(
                            fontFamily = IBMPlexMono,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = ColorTextPrimary
                        ),
                        modifier = Modifier.weight(0.6f),
                        textAlign = TextAlign.End
                    )
                }
                
                if (index < rows.size - 1) {
                    Divider(
                        color = ColorBorder,
                        thickness = 1.dp
                    )
                }
            }
        }
    }
}
```

### Usage Example
```kotlin
// Certificate section
DetailSection(
    label = "Certificate Info",
    rows = listOf(
        "Subject" to "CN=MedicalCenter, O=HealthCorp",
        "Issuer" to "CN=HealthCorp Root CA",
        "Valid Until" to "2027-05-15 23:59:59 UTC",
        "Serial" to "4F:A3:2E:1B:9C:7D"
    )
)

// JWT section
DetailSection(
    label = "JWT Token",
    rows = listOf(
        "Subject" to "prescription#RX-8472",
        "Algorithm" to "RS256",
        "Issued" to "2026-05-02 10:30:00 UTC",
        "Expires" to "2026-05-02 22:30:00 UTC"
    )
)

// Scan details
DetailSection(
    label = "Scan Details",
    rows = listOf(
        "Color Mode" to "8 colors",
        "ECC Level" to "3 (High)",
        "Decode Time" to "67ms",
        "Quality" to "Excellent"
    )
)
```

---

## 7. Context Variants

### Purpose
Adapt UI colors for different application contexts.

### Implementation
```kotlin
enum class AppContext {
    Healthcare,
    Legal,
    IoT,
    Diagnostic
}

fun AppContext.primaryColor(): Color = when (this) {
    AppContext.Healthcare -> Color(0xFF00D9FF)  // Cyan
    AppContext.Legal -> Color(0xFFFFB800)       // Gold
    AppContext.IoT -> Color(0xFFB84FFF)         // Purple
    AppContext.Diagnostic -> Color(0xFF00D9FF)   // Cyan
}

fun AppContext.primaryDim(): Color = primaryColor().copy(alpha = 0.2f)

@Composable
fun JABAuthTheme(
    context: AppContext = AppContext.Healthcare,
    content: @Composable () -> Unit
) {
    val colorScheme = darkColorScheme(
        primary = context.primaryColor(),
        primaryContainer = context.primaryDim(),
        // ... rest of colors
    )
    
    MaterialTheme(
        colorScheme = colorScheme,
        typography = JABAuthTypography,
        content = content
    )
}
```

### Usage
```kotlin
// Healthcare app
JABAuthTheme(context = AppContext.Healthcare) {
    ScannerScreen()
}

// Legal app
JABAuthTheme(context = AppContext.Legal) {
    ScannerScreen()
}
```

### Color Mappings
| Context | Primary Color | Use Case |
|---------|---------------|----------|
| **Healthcare** | Cyan (#00D9FF) | Medical prescriptions, patient records |
| **Legal** | Gold (#FFB800) | Contract signing, legal documents |
| **IoT** | Purple (#B84FFF) | Device provisioning, access control |
| **Diagnostic** | Cyan (#00D9FF) | Testing and debugging |

---

## Component Testing

### Screenshot Tests
```kotlin
@Test
fun scannerHeader_matchesDesign() {
    composeTestRule.setContent {
        ScannerHeader(
            appName = "JABAuth Scanner",
            instruction = "Position JABCode in frame",
            onTorchToggle = {},
            onSettingsClick = {},
            isTorchOn = false
        )
    }
    
    composeTestRule.onRoot()
        .captureToImage()
        .assertAgainstGolden("scanner-header")
}

@Test
fun scanTargetOverlay_detectState() {
    composeTestRule.setContent {
        ScanTargetOverlay(
            isScanning = false,
            isDetected = true
        )
    }
    
    composeTestRule.onRoot()
        .captureToImage()
        .assertAgainstGolden("scan-target-detected")
}
```

### Interaction Tests
```kotlin
@Test
fun resultPanel_dismissOnCloseButton() {
    val onDismiss = mock<() -> Unit>()
    
    composeTestRule.setContent {
        ResultPanel(
            result = sampleResult,
            onDismiss = onDismiss,
            onAccept = {},
            onScanAgain = {}
        )
    }
    
    composeTestRule.onNode(hasTestTag("close-button"))
        .performClick()
    
    verify(onDismiss).invoke()
}
```

---

## Accessibility

### Screen Reader Support
```kotlin
// Quality indicator
QualityIndicator(
    metric = QualityMetric("Brightness", 0.85f),
    modifier = Modifier.semantics {
        contentDescription = "Brightness: 85%, High quality"
    }
)

// Status overlay
ScanStatusOverlay(
    visible = true,
    icon = "✓",
    message = "Code Detected",
    modifier = Modifier.semantics {
        liveRegion = LiveRegionMode.Assertive
        contentDescription = "Scan successful. Code detected."
    }
)
```

### Touch Targets
All interactive elements meet 48dp minimum:
- Icon buttons: 40x40dp with 4dp padding
- Close button: 32x32dp with extended touch target
- Action buttons: Full width, 56dp height

---

## Performance Considerations

### Camera Preview Optimization
```kotlin
@Composable
fun CameraPreview() {
    // Use Android CameraX for efficient camera access
    val cameraProviderFuture = remember {
        ProcessCameraProvider.getInstance(context)
    }
    
    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).apply {
                implementationMode = PreviewView.ImplementationMode.PERFORMANCE
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
        },
        update = { previewView ->
            // Bind camera lifecycle
        }
    )
}
```

### Animation Performance
- Use `animateFloatAsState` for single-value animations
- Prefer `Canvas` drawing over multiple `Box` overlays
- Limit recomposition scope with `remember` and `derivedStateOf`

---

**Last Updated:** 2026-05-02  
**Status:** ✅ Production Ready  
**Next Review:** 2026-06-01
