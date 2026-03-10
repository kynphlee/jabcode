# Settings Screen UI/UX Audit

## Screenshot Reference
`Screenshot_20260124_205648_QR Scanner.jpg`

---

## Screen Overview

The Settings screen provides app configuration options organized into logical sections. It uses a standard Android settings pattern with section headers, toggle switches, and navigation items.

---

## Layout Structure

```
+---------------------------------------------+
| [Scan]  [Create]  [History]  [Settings]    |  <- Primary Navigation (Top)
+---------------------------------------------+
|                                             |
|  General                                    |  <- Section Header
|                                             |
|  Theme                                      |
|  Very dark                                  |  <- Setting with value
|                                             |
|  Help & Feedback                            |  <- Navigation item
|                                             |
|  Share QR Scanner                           |
|  Share the link to QR Scanner with...       |  <- Item with description
|                                             |
+---------------------------------------------+
|  Scan controls                              |  <- Section Header
+---------------------------------------------+
|                                             |
|  Open websites automatically          [OFF] |  <- Toggle
|                                             |
|  Continuous scanning                  [ON]  |
|  Only save scans to history, skip...        |
|                                             |
|  Duplicate barcodes                   [ON]  |
|  Store duplicate barcodes in history        |
|                                             |
|  Confirm scans manually               [OFF] |
|  Avoid accidental scans                     |
|                                             |
|  Play sound                           [OFF] |
|                                             |
|  Vibrate                              [ON]  |
|                                             |
+---------------------------------------------+
```

---

## Component Breakdown

### 1. Section Headers

| Section | Items |
|---------|-------|
| General | Theme, Help & Feedback, Share |
| Scan controls | 6 toggle settings |

**Specifications:**

| Property | Value |
|----------|-------|
| Text color | Orange (#FF9800) |
| Text size | 14sp |
| Text style | Medium/Semi-bold |
| Padding top | 24dp |
| Padding bottom | 8dp |
| Padding horizontal | 16dp |

### 2. Setting Items

#### Type A: Value Display (Theme)

```
+---------------------------------------------+
|  Theme                                      |
|  Very dark                                  |
+---------------------------------------------+
```

| Property | Value |
|----------|-------|
| Title | 16sp, white |
| Value/subtitle | 14sp, gray (#B3B3B3) |
| Height | ~72dp |
| Tap action | Opens selection dialog |

#### Type B: Navigation Item (Help & Feedback)

```
+---------------------------------------------+
|  Help & Feedback                            |
+---------------------------------------------+
```

| Property | Value |
|----------|-------|
| Title | 16sp, white |
| Height | ~56dp |
| Tap action | Navigate to screen |

#### Type C: Item with Icon (Share QR Scanner)

```
+---------------------------------------------+
| [<]  Share QR Scanner                       |
|      Share the link to QR Scanner with...   |
+---------------------------------------------+
```

| Property | Value |
|----------|-------|
| Icon | 24dp, left side |
| Title | 16sp, white |
| Description | 14sp, gray |
| Height | ~72dp |

#### Type D: Toggle Setting

```
+---------------------------------------------+
|  Continuous scanning                  [ON]  |
|  Only save scans to history, skip...        |
+---------------------------------------------+
```

| Property | Value |
|----------|-------|
| Title | 16sp, white |
| Description | 14sp, gray (optional) |
| Toggle | Material Switch, right-aligned |
| Toggle ON color | Orange (#FF9800) |
| Toggle OFF color | Gray (#757575) |
| Height | 72dp (with description) or 56dp (without) |

---

## Settings Inventory

### General Section

| Setting | Type | Current Value | Description |
|---------|------|---------------|-------------|
| Theme | Selection | "Very dark" | App theme selection |
| Help & Feedback | Navigation | - | Support/FAQ screen |
| Share QR Scanner | Action | - | Share app link |

### Scan Controls Section

| Setting | Type | Default | Description |
|---------|------|---------|-------------|
| Open websites automatically | Toggle | OFF | Auto-open URLs after scan |
| Continuous scanning | Toggle | ON | Keep scanning after success |
| Duplicate barcodes | Toggle | ON | Allow duplicate history entries |
| Confirm scans manually | Toggle | OFF | Require tap to confirm |
| Play sound | Toggle | OFF | Audio feedback on scan |
| Vibrate | Toggle | ON | Haptic feedback on scan |

---

## Interaction Patterns

### Theme Selection
1. Tap "Theme"
2. Bottom sheet or dialog appears
3. Options: "Light", "Dark", "Very dark", "System default"
4. Select option
5. Theme applies immediately
6. Dialog closes

### Toggle Switch
1. Tap toggle or row
2. Toggle animates to new state
3. Setting saved immediately
4. No confirmation needed

### Share App
1. Tap "Share QR Scanner"
2. System share sheet opens
3. Pre-filled with app store link
4. User selects share target

---

## Theme Options

| Theme | Background | Surface | Text |
|-------|------------|---------|------|
| Light | #FFFFFF | #F5F5F5 | #000000 |
| Dark | #121212 | #1E1E1E | #FFFFFF |
| Very dark | #000000 | #1A1A1A | #FFFFFF |
| System | Follows OS | Follows OS | Follows OS |

---

## JABCode-Specific Settings

Additional settings for JABCode scanner:

### JABCode Section

| Setting | Type | Default | Description |
|---------|------|---------|-------------|
| Default color mode | Selection | 8 | Preferred color mode for creation |
| Default ECC level | Selection | 3 | Error correction level |
| Show color mode badge | Toggle | ON | Display detected color mode |
| Enable streaming mode | Toggle | OFF | RFC 6330 RaptorQ support |

### Advanced Section

| Setting | Type | Default | Description |
|---------|------|---------|-------------|
| Decode timeout | Selection | 500ms | Max decode attempt time |
| Frame throttle | Selection | 100ms | Min time between frames |
| Save decode images | Toggle | OFF | Debug: save processed frames |

---

## States

### Default
- All settings visible
- Toggles reflect current values
- Scrollable if content exceeds screen

### Theme Change
- Immediate visual update
- No restart required
- Smooth transition animation

### Setting Changed
- Toggle animates
- Value persists to SharedPreferences
- No toast/confirmation (silent save)

---

## Accessibility

| Feature | Implementation |
|---------|----------------|
| Section headers | Announced as headings |
| Toggle state | "On" / "Off" announced |
| Descriptions | Read after title |
| Touch targets | Full row tappable |

---

## Implementation Notes

### Android Components

```kotlin
// Using Jetpack Preference Library
class SettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
    }
}
```

### Preference XML Structure

```xml
<?xml version="1.0" encoding="utf-8"?>
<PreferenceScreen xmlns:app="http://schemas.android.com/apk/res-auto">

    <PreferenceCategory app:title="General">
        
        <ListPreference
            app:key="theme"
            app:title="Theme"
            app:entries="@array/theme_entries"
            app:entryValues="@array/theme_values"
            app:defaultValue="very_dark" />
            
        <Preference
            app:key="help"
            app:title="Help &amp; Feedback" />
            
        <Preference
            app:key="share"
            app:title="Share QR Scanner"
            app:summary="Share the link to QR Scanner with your friends"
            app:icon="@drawable/ic_share" />
            
    </PreferenceCategory>

    <PreferenceCategory app:title="Scan controls">
        
        <SwitchPreferenceCompat
            app:key="auto_open_urls"
            app:title="Open websites automatically"
            app:defaultValue="false" />
            
        <SwitchPreferenceCompat
            app:key="continuous_scanning"
            app:title="Continuous scanning"
            app:summary="Only save scans to history, skip actions post-scan"
            app:defaultValue="true" />
            
        <SwitchPreferenceCompat
            app:key="duplicate_barcodes"
            app:title="Duplicate barcodes"
            app:summary="Store duplicate barcodes in history"
            app:defaultValue="true" />
            
        <SwitchPreferenceCompat
            app:key="confirm_scans"
            app:title="Confirm scans manually"
            app:summary="Avoid accidental scans"
            app:defaultValue="false" />
            
        <SwitchPreferenceCompat
            app:key="play_sound"
            app:title="Play sound"
            app:defaultValue="false" />
            
        <SwitchPreferenceCompat
            app:key="vibrate"
            app:title="Vibrate"
            app:defaultValue="true" />
            
    </PreferenceCategory>

</PreferenceScreen>
```

### Theme Implementation

```kotlin
object ThemeManager {
    fun applyTheme(theme: String) {
        when (theme) {
            "light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            "dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            "very_dark" -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                // Apply OLED-black theme overlay
            }
            "system" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }
}
```

### SharedPreferences Keys

```kotlin
object PreferenceKeys {
    const val THEME = "theme"
    const val AUTO_OPEN_URLS = "auto_open_urls"
    const val CONTINUOUS_SCANNING = "continuous_scanning"
    const val DUPLICATE_BARCODES = "duplicate_barcodes"
    const val CONFIRM_SCANS = "confirm_scans"
    const val PLAY_SOUND = "play_sound"
    const val VIBRATE = "vibrate"
    
    // JABCode specific
    const val DEFAULT_COLOR_MODE = "default_color_mode"
    const val DEFAULT_ECC_LEVEL = "default_ecc_level"
    const val SHOW_COLOR_BADGE = "show_color_badge"
    const val STREAMING_MODE = "streaming_mode"
}
```

---

## Material Design Compliance

| Guideline | Implementation |
|-----------|----------------|
| Switch placement | Right side of row |
| Section headers | Colored, smaller text |
| Touch feedback | Ripple effect |
| Spacing | 16dp horizontal padding |
| Dividers | Optional, between sections |

---

## Metrics to Track

| Metric | Purpose |
|--------|---------|
| Theme distribution | User preference |
| Toggle change frequency | Feature discovery |
| Continuous scanning usage | Workflow preference |
| Sound vs vibrate preference | Feedback preference |

---

*Document created: 2026-01-24*
*Related: [index.md](index.md), [01-scan-screen-audit.md](01-scan-screen-audit.md), [02-create-screen-audit.md](02-create-screen-audit.md), [03-history-screen-audit.md](03-history-screen-audit.md)*
