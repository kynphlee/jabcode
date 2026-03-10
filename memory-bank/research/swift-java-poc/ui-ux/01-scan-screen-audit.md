# Scan Screen UI/UX Audit

## Screenshot Reference
`Screenshot_20260124_205627_QR Scanner.jpg`

---

## Screen Overview

The Scan screen is the primary interface for camera-based barcode scanning. It features a full-screen camera preview with an overlay scan frame and contextual action buttons.

---

## Layout Structure

```
┌─────────────────────────────────────────────┐
│ [Scan]  [Create]  [History]  [Settings]    │  ← Primary Navigation (Top)
├─────────────────────────────────────────────┤
│   [Light]      [Scan image]      [Help]    │  ← Secondary Toolbar
├─────────────────────────────────────────────┤
│                                             │
│                                             │
│         ┌─────────────────────┐             │
│         │                     │             │
│         │    SCAN FRAME       │             │  ← Camera Preview
│         │    (Orange border)  │             │
│         │                     │             │
│         └─────────────────────┘             │
│                                             │
│                                             │
├─────────────────────────────────────────────┤
│   [−]  ════════●════════════  [+]          │  ← Zoom Slider
└─────────────────────────────────────────────┘
```

---

## Component Breakdown

### 1. Primary Navigation Bar (Top)

| Element | Icon | Label | State |
|---------|------|-------|-------|
| Scan | `[ ]` corners | "Scan" | **Active** (orange underline) |
| Create | Pencil | "Create" | Inactive |
| History | Clock | "History" | Inactive |
| Settings | Gear | "Settings" | Inactive |

**Specifications:**
- Height: ~56dp
- Background: `#000000` (black)
- Active indicator: Orange underline (`#FF9800`)
- Icon size: 24dp
- Label size: 12sp
- Icon + label vertical stack

### 2. Secondary Toolbar

| Element | Icon | Label | Function |
|---------|------|-------|----------|
| Light | Lightbulb | "Light" | Toggle torch/flash |
| Scan image | Image/gallery | "Scan image" | Pick image from gallery |
| Help | Question mark | "Help" | Show help/instructions |

**Specifications:**
- Height: ~48dp
- Background: Semi-transparent black overlay
- Icon color: White (`#FFFFFF`)
- Label color: White (`#FFFFFF`)
- Horizontal distribution: Space-evenly

### 3. Scan Frame Overlay

**Visual Design:**
- Orange corner brackets (not full rectangle)
- Corner length: ~40dp per side
- Stroke width: 4dp
- Color: `#FF9800` (orange)
- Inner area: Transparent (camera shows through)
- Outer area: Semi-transparent dark overlay (~50% black)

**Dimensions:**
- Width: ~70% of screen width
- Aspect ratio: 1:1 (square)
- Centered horizontally and vertically in camera area

**Animation (Recommended):**
- Subtle pulse or glow when actively scanning
- Color change on successful detection

### 4. Zoom Slider

| Element | Description |
|---------|-------------|
| Minus icon | Zoom out (left) |
| Slider track | Horizontal progress bar |
| Slider thumb | Orange circle indicator |
| Plus icon | Zoom in (right) |

**Specifications:**
- Track color: `#666666` (gray)
- Thumb color: `#FF9800` (orange)
- Icon color: White
- Position: Bottom of screen, above system nav
- Padding: 16dp horizontal

---

## Interaction Patterns

### Tap-to-Focus
- User taps anywhere in camera preview
- Focus indicator appears at tap location
- Auto-focus triggered

### Torch Toggle
- Tap "Light" button
- Icon fills/highlights when active
- Torch turns on/off

### Gallery Scan
- Tap "Scan image"
- System image picker opens
- Selected image analyzed for barcodes

### Zoom Control
- Drag slider thumb left/right
- Pinch-to-zoom on camera preview (alternative)
- Zoom level persists during session

---

## States

### Scanning (Default)
- Camera preview active
- Scan frame visible
- Waiting for barcode detection

### Barcode Detected
- Scan frame highlights (color change or animation)
- Haptic feedback (vibration)
- Result displayed or action triggered

### Low Light
- "Light" button may pulse or highlight
- Hint text: "Tap Light for better scanning"

### Permission Denied
- Camera preview replaced with permission request
- "Grant camera permission" button
- Explanation text

---

## Accessibility

| Feature | Implementation |
|---------|----------------|
| Content descriptions | All icons have labels |
| Touch targets | Minimum 48dp × 48dp |
| Color contrast | Orange on black = 4.5:1+ ratio |
| Screen reader | "Scan tab, selected. Camera viewfinder active." |

---

## Implementation Notes

### Android Components

```kotlin
// Layout structure
ConstraintLayout
├── TabLayout (primary navigation)
├── LinearLayout (secondary toolbar)
│   ├── ImageButton (light)
│   ├── ImageButton (scan image)
│   └── ImageButton (help)
├── PreviewView (CameraX)
├── ScanOverlayView (custom view)
└── SeekBar (zoom slider)
```

### Key Classes

| Component | Android Class |
|-----------|---------------|
| Camera preview | `androidx.camera.view.PreviewView` |
| Scan overlay | Custom `View` with `Canvas` drawing |
| Navigation | `com.google.android.material.tabs.TabLayout` |
| Zoom slider | `SeekBar` or `Slider` (Material) |

### CameraX Integration

```kotlin
// Bind zoom to slider
camera.cameraControl.setLinearZoom(sliderValue) // 0.0f to 1.0f

// Torch control
camera.cameraControl.enableTorch(isEnabled)

// Tap-to-focus
val factory = previewView.meteringPointFactory
val point = factory.createPoint(x, y)
camera.cameraControl.startFocusAndMetering(
    FocusMeteringAction.Builder(point).build()
)
```

---

## JABCode Adaptations

For JABCode scanning, consider these modifications:

| Standard QR | JABCode Adaptation |
|-------------|-------------------|
| Square scan frame | Keep square (JABCode is square) |
| Single color detection | Multi-color awareness indicator |
| Simple feedback | Show color mode detected (4/8/16/etc.) |
| Standard zoom | May need closer zoom for small codes |

### Color Mode Indicator (Optional)

```
┌─────────────────────┐
│  Detected: 8-color  │  ← Small badge below scan frame
└─────────────────────┘
```

---

## Metrics to Track

| Metric | Purpose |
|--------|---------|
| Time to first scan | UX performance |
| Torch usage rate | Lighting conditions |
| Gallery scan usage | Feature adoption |
| Zoom level distribution | Typical scanning distance |

---

*Document created: 2026-01-24*
*Related: [index.md](index.md)*
