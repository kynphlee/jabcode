# Create Screen UI/UX Audit

## Screenshot Reference
`Screenshot_20260124_205635_QR Scanner.jpg`

---

## Screen Overview

The Create screen allows users to generate barcodes from various content types. It presents a scrollable list of content categories, each with a distinctive icon and color.

---

## Layout Structure

```
┌─────────────────────────────────────────────┐
│ [Scan]  [Create]  [History]  [Settings]    │  ← Primary Navigation (Top)
├─────────────────────────────────────────────┤
│                                             │
│  ○ Use "Share" in other apps               │
│                                             │
│  ○ Content from clipboard                  │
│                                             │
│  ○ Website                                 │
│                                             │
│  ○ Contact                                 │
│                                             │
│  ○ Wi-Fi                                   │
│                                             │
│  ○ Location                                │
│                                             │
│  ○ Event                                   │
│                                             │
│  ○ More QR codes                           │
│                                             │
│  ○ Barcodes and other 2D codes             │
│                                             │
└─────────────────────────────────────────────┘
```

---

## Component Breakdown

### 1. Primary Navigation Bar

Same as Scan screen, but with "Create" tab active (orange underline).

### 2. Content Type List

| Item | Icon | Icon Color | Description |
|------|------|------------|-------------|
| Use "Share" in other apps | Share arrow | Gray | System share integration |
| Content from clipboard | Clipboard | Orange | Paste clipboard content |
| Website | Globe | Teal/Cyan | URL/website link |
| Contact | Person | Yellow | vCard contact info |
| Wi-Fi | Wi-Fi signal | Purple | Wi-Fi network credentials |
| Location | Map pin | Red | GPS coordinates |
| Event | Calendar | Orange | Calendar event |
| More QR codes | QR grid | Orange | Additional QR types |
| Barcodes and other 2D codes | Barcode lines | Orange | 1D/2D barcode formats |

**List Item Specifications:**

| Property | Value |
|----------|-------|
| Item height | ~72dp |
| Icon size | 40dp (circular background) |
| Icon background | Colored circle |
| Icon foreground | White icon |
| Text size | 16sp |
| Text color | White (`#FFFFFF`) |
| Padding left | 16dp |
| Padding vertical | 16dp |
| Divider | None (clean list) |

### 3. Icon Design Pattern

Each icon uses a **circular colored background** with a **white foreground icon**:

```
┌──────────────────────────────────────┐
│  ┌────┐                              │
│  │ 🌐 │  Website                     │
│  └────┘                              │
│   ↑                                  │
│   Teal circle with white globe       │
└──────────────────────────────────────┘
```

**Color Assignments:**

| Content Type | Background Color | Hex (Approximate) |
|--------------|------------------|-------------------|
| Share | Gray | `#757575` |
| Clipboard | Orange | `#FF9800` |
| Website | Teal | `#00BCD4` |
| Contact | Yellow/Amber | `#FFC107` |
| Wi-Fi | Purple | `#9C27B0` |
| Location | Red | `#F44336` |
| Event | Orange | `#FF9800` |
| More QR codes | Orange | `#FF9800` |
| Barcodes | Orange | `#FF9800` |

---

## Interaction Patterns

### List Item Tap
1. User taps content type
2. Ripple effect on item
3. Navigate to input form for that type

### Share Integration
- "Use Share in other apps" enables receiving shared content
- App appears in system share sheet
- Shared text/URL auto-populates barcode generator

### Clipboard Detection
- "Content from clipboard" checks clipboard on tap
- If valid content found, pre-fills input
- If empty, shows "Clipboard is empty" message

---

## Content Type Forms (Expected)

Each content type leads to a specific input form:

### Website Form
```
┌─────────────────────────────────────┐
│ Website                             │
├─────────────────────────────────────┤
│ URL                                 │
│ ┌─────────────────────────────────┐ │
│ │ https://                        │ │
│ └─────────────────────────────────┘ │
│                                     │
│         [Generate QR Code]          │
└─────────────────────────────────────┘
```

### Contact Form
```
┌─────────────────────────────────────┐
│ Contact                             │
├─────────────────────────────────────┤
│ Name     [___________________]      │
│ Phone    [___________________]      │
│ Email    [___________________]      │
│ Address  [___________________]      │
│                                     │
│         [Generate QR Code]          │
└─────────────────────────────────────┘
```

### Wi-Fi Form
```
┌─────────────────────────────────────┐
│ Wi-Fi                               │
├─────────────────────────────────────┤
│ Network name (SSID)                 │
│ ┌─────────────────────────────────┐ │
│ │                                 │ │
│ └─────────────────────────────────┘ │
│ Password                            │
│ ┌─────────────────────────────────┐ │
│ │ ●●●●●●●●                        │ │
│ └─────────────────────────────────┘ │
│ Security: [WPA/WPA2 ▼]              │
│ □ Hidden network                    │
│                                     │
│         [Generate QR Code]          │
└─────────────────────────────────────┘
```

---

## JABCode Adaptations

For JABCode creation, add these options:

### Additional Settings for JABCode

| Setting | Options | Default |
|---------|---------|---------|
| Color Mode | 4, 8, 16, 32, 64, 128 colors | 8 |
| ECC Level | 0-9 | 3 |
| Module Size | 8-20 px | 12 |

### JABCode-Specific Create Options

```
┌─────────────────────────────────────┐
│  ○ Text / Data                     │  ← Free-form text
│  ○ File (small)                    │  ← Binary data
│  ○ Streaming (RaptorQ)             │  ← RFC 6330 fountain codes
└─────────────────────────────────────┘
```

### Color Mode Selector UI

```
┌─────────────────────────────────────┐
│ Color Mode                          │
│ ┌─────┬─────┬─────┬─────┬─────┬───┐│
│ │  4  │  8  │ 16  │ 32  │ 64  │128││
│ └─────┴─────┴─────┴─────┴─────┴───┘│
│         ↑ Selected (orange)         │
└─────────────────────────────────────┘
```

---

## States

### Default
- Full list visible
- All items tappable

### After Selection
- Navigate to form screen
- Back button returns to list

### Generated Barcode
- Show barcode image
- Share/Save options
- "Create another" button

---

## Accessibility

| Feature | Implementation |
|---------|----------------|
| Content descriptions | "Create [type] barcode" |
| Touch targets | Full row tappable (72dp height) |
| Color contrast | White text on black = 21:1 ratio |
| Icon meaning | Color + icon + text (redundant) |

---

## Implementation Notes

### Android Components

```kotlin
// Layout structure
CoordinatorLayout
├── TabLayout (primary navigation)
└── RecyclerView (content type list)
    └── ContentTypeAdapter
        └── ContentTypeViewHolder
```

### Data Model

```kotlin
data class ContentType(
    val id: String,
    val title: String,
    val iconRes: Int,
    val iconBackgroundColor: Int,
    val formDestination: NavDirections
)

val contentTypes = listOf(
    ContentType("share", "Use \"Share\" in other apps", R.drawable.ic_share, Color.GRAY, ...),
    ContentType("clipboard", "Content from clipboard", R.drawable.ic_clipboard, Color.ORANGE, ...),
    ContentType("website", "Website", R.drawable.ic_globe, Color.CYAN, ...),
    // ...
)
```

### RecyclerView Item Layout

```xml
<LinearLayout
    android:layout_width="match_parent"
    android:layout_height="72dp"
    android:orientation="horizontal"
    android:paddingHorizontal="16dp"
    android:gravity="center_vertical"
    android:background="?selectableItemBackground">

    <ImageView
        android:id="@+id/icon"
        android:layout_width="40dp"
        android:layout_height="40dp"
        android:background="@drawable/circle_background"
        android:padding="8dp"
        android:tint="@color/white" />

    <TextView
        android:id="@+id/title"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:layout_marginStart="16dp"
        android:textSize="16sp"
        android:textColor="@color/white" />

</LinearLayout>
```

---

## Metrics to Track

| Metric | Purpose |
|--------|---------|
| Content type selection distribution | Feature popularity |
| Form completion rate | UX friction |
| Share vs manual creation | Integration usage |
| Barcode save/share rate | Output utility |

---

*Document created: 2026-01-24*
*Related: [index.md](index.md), [01-scan-screen-audit.md](01-scan-screen-audit.md)*
