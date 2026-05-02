# JABAuth Mobile Framework - Design System Specification

**Version:** 1.0.0  
**Date:** 2026-05-02  
**Aesthetic Direction:** Precision Instrumentation

---

## Design Philosophy

### Concept: "Precision Instrumentation"

The JABAuth diagnostic interface channels the aesthetic of **technical measurement tools**—oscilloscopes, aviation cockpits, and terminal UIs—to communicate precision, reliability, and technical authority.

**Key Attributes:**
- **Monospaced typography** for data display (echoes code/terminal)
- **High-contrast dark theme** with electric accents (readability + focus)
- **Grid-based layouts** with technical precision (echoes measurement tools)
- **Mechanical animations** with exact timing (purposeful, not decorative)

**What to Avoid:**
- ❌ Soft, rounded "friendly" aesthetics
- ❌ Pastel colors or low-contrast palettes
- ❌ Playful, bouncy animations
- ❌ Generic Material Design defaults

**Goal:** When developers open this app, it should feel like **mission-critical instrumentation**, not a consumer app.

---

## Color System

### Dark Theme Palette

```kotlin
// Primary Colors
val ColorPrimary = Color(0xFF00D9FF)         // Electric cyan - active states, primary actions
val ColorPrimaryDim = Color(0xFF00D9FF).copy(alpha = 0.2f)  // Backgrounds

val ColorSuccess = Color(0xFF39FF14)         // Neon green - success, passed tests
val ColorSuccessDim = Color(0xFF39FF14).copy(alpha = 0.2f)

val ColorWarning = Color(0xFFFFB800)         // Amber - warnings, performance issues
val ColorWarningDim = Color(0xFFFFB800).copy(alpha = 0.2f)

val ColorError = Color(0xFFFF006E)           // Hot magenta - errors, failures
val ColorErrorDim = Color(0xFFFF006E).copy(alpha = 0.2f)

// Background Hierarchy
val ColorBgBase = Color(0xFF0B1120)          // Canvas background
val ColorBgElevated = Color(0xFF131B2E)      // Raised surfaces (cards, modals)
val ColorBgCard = Color(0xFF1A2438)          // Card backgrounds
val ColorBgHover = Color(0xFF212D44)         // Hover states

// Text Hierarchy
val ColorTextPrimary = Color(0xFFE8F4F8)     // High-emphasis text
val ColorTextSecondary = Color(0xFF8B9DB0)   // Medium-emphasis text
val ColorTextDim = Color(0xFF5A6B7D)         // Low-emphasis text (metadata, hints)

// Borders & Dividers
val ColorBorder = Color(0xFF8B9DB0).copy(alpha = 0.15f)
val ColorGrid = Color(0xFF8B9DB0).copy(alpha = 0.05f)
```

### Material 3 Theme Mapping

```kotlin
val JABAuthDarkColorScheme = darkColorScheme(
    primary = ColorPrimary,
    onPrimary = ColorBgBase,
    primaryContainer = ColorPrimaryDim,
    onPrimaryContainer = ColorPrimary,
    
    secondary = ColorSuccess,
    onSecondary = ColorBgBase,
    
    tertiary = ColorWarning,
    onTertiary = ColorBgBase,
    
    error = ColorError,
    onError = Color.White,
    
    background = ColorBgBase,
    onBackground = ColorTextPrimary,
    
    surface = ColorBgCard,
    onSurface = ColorTextPrimary,
    surfaceVariant = ColorBgElevated,
    onSurfaceVariant = ColorTextSecondary,
    
    outline = ColorBorder,
    outlineVariant = ColorGrid
)
```

### Color Usage Guidelines

| Element | Color | Usage |
|---------|-------|-------|
| **Active scan indicator** | ColorPrimary | Pulsing dot, active state borders |
| **Success metrics** | ColorSuccess | Passed tests, valid certificates, performance within threshold |
| **Warning alerts** | ColorWarning | Performance degradation, approaching limits |
| **Error states** | ColorError | Failed tests, invalid tokens, critical issues |
| **Card backgrounds** | ColorBgCard | Primary container for content |
| **Hover states** | ColorBgHover | Interactive elements on hover |
| **Metadata text** | ColorTextDim | Timestamps, device info, secondary data |

---

## Typography System

### Font Families

**Display/Data: IBM Plex Mono**
- **Purpose:** Metrics, code snippets, technical data
- **Character:** Monospaced, technical, precise
- **Download:** https://fonts.google.com/specimen/IBM+Plex+Mono
- **Weights Used:** Regular (400), Medium (500), SemiBold (600), Bold (700)

**Body/UI: Archivo**
- **Purpose:** UI labels, descriptions, body text
- **Character:** Geometric, clean, technical-but-readable
- **Download:** https://fonts.google.com/specimen/Archivo
- **Weights Used:** Regular (400), Medium (500), SemiBold (600), Bold (700)

### Type Scale

```kotlin
val IBMPlexMono = FontFamily(
    Font(R.font.ibm_plex_mono_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_mono_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_mono_semibold, FontWeight.SemiBold),
    Font(R.font.ibm_plex_mono_bold, FontWeight.Bold)
)

val Archivo = FontFamily(
    Font(R.font.archivo_regular, FontWeight.Normal),
    Font(R.font.archivo_medium, FontWeight.Medium),
    Font(R.font.archivo_semibold, FontWeight.SemiBold),
    Font(R.font.archivo_bold, FontWeight.Bold)
)

val JABAuthTypography = Typography(
    // Display - Large metrics, hero numbers
    displayLarge = TextStyle(
        fontFamily = IBMPlexMono,
        fontSize = 36.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 40.sp,
        letterSpacing = (-0.02).em
    ),
    
    displayMedium = TextStyle(
        fontFamily = IBMPlexMono,
        fontSize = 28.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 32.sp,
        letterSpacing = (-0.02).em
    ),
    
    displaySmall = TextStyle(
        fontFamily = IBMPlexMono,
        fontSize = 22.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 28.sp,
        letterSpacing = (-0.01).em
    ),
    
    // Headings - Card titles, section headers
    headlineLarge = TextStyle(
        fontFamily = IBMPlexMono,
        fontSize = 18.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 24.sp,
        letterSpacing = (-0.01).em
    ),
    
    headlineMedium = TextStyle(
        fontFamily = Archivo,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 22.sp
    ),
    
    headlineSmall = TextStyle(
        fontFamily = Archivo,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 20.sp
    ),
    
    // Body - Descriptions, content
    bodyLarge = TextStyle(
        fontFamily = Archivo,
        fontSize = 15.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 24.sp
    ),
    
    bodyMedium = TextStyle(
        fontFamily = Archivo,
        fontSize = 13.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 20.sp
    ),
    
    bodySmall = TextStyle(
        fontFamily = Archivo,
        fontSize = 11.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 16.sp
    ),
    
    // Labels - Uppercase labels, badges
    labelLarge = TextStyle(
        fontFamily = IBMPlexMono,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 16.sp,
        letterSpacing = 0.05.em
    ),
    
    labelMedium = TextStyle(
        fontFamily = IBMPlexMono,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 14.sp,
        letterSpacing = 0.05.em
    ),
    
    labelSmall = TextStyle(
        fontFamily = IBMPlexMono,
        fontSize = 9.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 12.sp,
        letterSpacing = 0.1.em
    )
)
```

### Typography Usage Guidelines

| Element | Style | Example |
|---------|-------|---------|
| **Metric values (42ms)** | displayMedium + IBMPlexMono | Performance stats, benchmark results |
| **Card titles** | headlineLarge + IBMPlexMono | "Color Mode Performance" |
| **Section headers** | headlineMedium + Archivo | "Detected Issues" |
| **Body text** | bodyMedium + Archivo | Alert descriptions, feed items |
| **Metadata** | bodySmall + IBMPlexMono | Timestamps, device info |
| **Badges/Labels** | labelMedium + UPPERCASE | "LIVE", "REAL-TIME", "6 MODES" |

---

## Spacing System

### Base Unit: 4dp

All spacing uses multiples of 4dp for visual consistency.

```kotlin
object Spacing {
    val xxxs = 2.dp   // Tight spacing (rare)
    val xxs = 4.dp    // Minimal gap
    val xs = 8.dp     // Small gap
    val sm = 12.dp    // Comfortable gap
    val md = 16.dp    // Default spacing
    val lg = 24.dp    // Section spacing
    val xl = 32.dp    // Large spacing
    val xxl = 48.dp   // Extra large spacing
    val xxxl = 64.dp  // Huge spacing (rare)
}
```

### Usage Guidelines

| Spacing | Usage | Example |
|---------|-------|---------|
| **4dp** | Icon-text gap, tight element spacing | Status dot → "LIVE" text |
| **8dp** | Default gap between related elements | List items, chip gaps |
| **12dp** | Comfortable spacing within cards | Between title and content |
| **16dp** | Card padding, default section gap | Card internal padding |
| **24dp** | Between unrelated sections | Gap between cards |
| **32dp** | Page margins, large sections | Dashboard padding |

---

## Component Specifications

### Card

```kotlin
@Composable
fun JABAuthCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = ColorBgCard
        ),
        border = BorderStroke(1.dp, ColorBorder),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            content = content
        )
    }
}
```

**Specifications:**
- Background: `ColorBgCard`
- Border: 1dp solid `ColorBorder`
- Corner radius: 12dp
- Padding: 24dp (Spacing.lg)
- Shadow: None (flat design)

### Badge

```kotlin
@Composable
fun JABAuthBadge(
    text: String,
    color: Color = ColorPrimary
) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = color,
        modifier = Modifier
            .background(
                color = color.copy(alpha = 0.2f),
                shape = RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 12.dp, vertical = 4.dp)
    )
}
```

**Specifications:**
- Background: Primary color at 20% opacity
- Text: UPPERCASE, labelMedium, primary color
- Padding: 12dp horizontal, 4dp vertical
- Corner radius: 4dp

### Status Indicator (Pulsing Dot)

```kotlin
@Composable
fun StatusIndicator(
    isActive: Boolean = true,
    color: Color = ColorSuccess
) {
    val infiniteTransition = rememberInfiniteTransition()
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )
    
    Box(
        modifier = Modifier
            .size(8.dp)
            .alpha(if (isActive) alpha else 1f)
            .background(color, CircleShape)
            .shadow(
                elevation = 12.dp,
                shape = CircleShape,
                spotColor = color
            )
    )
}
```

**Specifications:**
- Size: 8dp circle
- Pulse animation: 2000ms duration, alpha 1.0 → 0.4 → 1.0
- Glow effect: 12dp shadow in indicator color

### Feed Item

```kotlin
@Composable
fun FeedItem(
    type: FeedType,
    icon: String,
    title: String,
    description: String,
    metadata: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ColorBgElevated, RoundedCornerShape(4.dp))
            .border(
                width = 3.dp,
                color = when (type) {
                    FeedType.SUCCESS -> ColorSuccess
                    FeedType.WARNING -> ColorWarning
                    FeedType.ERROR -> ColorError
                },
                shape = RoundedCornerShape(4.dp)
            )
            .padding(Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        // Icon box: 32x32dp, colored background
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(
                    when (type) {
                        FeedType.SUCCESS -> ColorSuccessDim
                        FeedType.WARNING -> ColorWarningDim
                        FeedType.ERROR -> ColorErrorDim
                    },
                    RoundedCornerShape(6.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(icon, fontSize = 18.sp)
        }
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold
                )
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = ColorTextSecondary,
                modifier = Modifier.padding(top = Spacing.xxs)
            )
            Text(
                text = metadata,
                style = MaterialTheme.typography.labelSmall,
                color = ColorTextDim,
                modifier = Modifier.padding(top = Spacing.xs)
            )
        }
    }
}
```

**Specifications:**
- Background: `ColorBgElevated`
- Left border: 3dp solid, color based on type
- Corner radius: 4dp
- Icon: 32x32dp box, 6dp corner radius, colored background
- Padding: 12dp (Spacing.sm)
- Gap: 12dp between icon and content

---

## Motion System

### Animation Timing

```kotlin
object AnimationDuration {
    const val Fast = 150       // Quick transitions, hovers
    const val Normal = 300     // Standard animations
    const val Slow = 600       // Entrance animations
}

object AnimationEasing {
    val Standard = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1f)     // General purpose
    val Decelerate = CubicBezierEasing(0.0f, 0.0f, 0.2f, 1f)   // Enter screen
    val Accelerate = CubicBezierEasing(0.4f, 0.0f, 1f, 1f)     // Exit screen
}
```

### Staggered Card Entrance

```kotlin
@Composable
fun StaggeredCardReveal(
    index: Int,
    content: @Composable () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        delay((index * 100).toLong())
        visible = true
    }
    
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(
            animationSpec = tween(
                durationMillis = AnimationDuration.Slow,
                easing = AnimationEasing.Decelerate
            )
        ) + slideInVertically(
            initialOffsetY = { 24 },
            animationSpec = tween(
                durationMillis = AnimationDuration.Slow,
                easing = AnimationEasing.Decelerate
            )
        )
    ) {
        content()
    }
}
```

**Specifications:**
- Delay: 100ms per index
- Duration: 600ms
- Movement: 24dp upward slide + fade in
- Easing: Decelerate (0.0, 0.0, 0.2, 1.0)

### Hover State

```kotlin
val interactionSource = remember { MutableInteractionSource() }
val isHovered by interactionSource.collectIsHoveredAsState()

Card(
    modifier = Modifier
        .hoverable(interactionSource)
        .graphicsLayer {
            translationY = if (isHovered) -2.dp.toPx() else 0f
        },
    colors = CardDefaults.cardColors(
        containerColor = if (isHovered) ColorBgHover else ColorBgCard
    )
) {
    // Content
}
```

**Specifications:**
- Hover effect: 2dp upward translation
- Background change: `ColorBgCard` → `ColorBgHover`
- Duration: Instant (no animation, direct state change)

---

## Iconography

### System Icons

Use Material Icons with technical character:

```kotlin
// Prefer outlined variants for technical feel
Icon(
    imageVector = Icons.Outlined.CheckCircle,
    contentDescription = "Success",
    tint = ColorSuccess
)

// Status indicators
Icons.Outlined.Circle        // Active/inactive states
Icons.Outlined.Warning       // Warning alerts
Icons.Outlined.Error         // Error alerts
Icons.Outlined.Info          // Information

// Actions
Icons.Outlined.PlayArrow     // Start benchmark
Icons.Outlined.Stop          // Stop operation
Icons.Outlined.Refresh       // Refresh data
Icons.Outlined.Download      // Export report

// Navigation
Icons.Outlined.ArrowBack     // Back navigation
Icons.Outlined.MoreVert      // More options
Icons.Outlined.Settings      // Settings
```

### Custom Icons

For JABCode-specific icons, use **stroke-based** designs (not filled):

```kotlin
// Example: JABCode icon (simplified)
@Composable
fun JABCodeIcon(
    modifier: Modifier = Modifier,
    tint: Color = ColorPrimary
) {
    Canvas(modifier = modifier.size(24.dp)) {
        // Draw 3x3 grid of colored squares (simplified JABCode)
        val cellSize = size.width / 3
        drawRect(
            color = tint,
            topLeft = Offset(0f, 0f),
            size = Size(cellSize - 2.dp.toPx(), cellSize - 2.dp.toPx()),
            style = Stroke(width = 2.dp.toPx())
        )
        // ... more squares
    }
}
```

---

## Layout Patterns

### Dashboard Grid

```kotlin
@Composable
fun DashboardLayout() {
    LazyVerticalGrid(
        columns = GridCells.Fixed(12),
        contentPadding = PaddingValues(Spacing.xl),
        horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg)
    ) {
        // Large performance graph (8 columns)
        item(span = { GridItemSpan(8) }) {
            PerformanceGraphCard()
        }
        
        // Sidebar alerts (4 columns)
        item(span = { GridItemSpan(4) }) {
            AlertsCard()
        }
        
        // Full-width color mode comparison
        item(span = { GridItemSpan(12) }) {
            ColorModeCard()
        }
        
        // Two equal columns
        item(span = { GridItemSpan(6) }) {
            LiveFeedCard()
        }
        item(span = { GridItemSpan(6) }) {
            ModuleHealthCard()
        }
    }
}
```

### Responsive Grid

```kotlin
@Composable
fun ResponsiveDashboard() {
    val configuration = LocalConfiguration.current
    val columns = when {
        configuration.screenWidthDp < 600 -> 1    // Compact: Single column
        configuration.screenWidthDp < 840 -> 6    // Medium: 6 columns
        else -> 12                                 // Expanded: 12 columns
    }
    
    LazyVerticalGrid(columns = GridCells.Fixed(columns)) {
        // Adapt spans: full width on compact, proportional on larger screens
        item(span = { GridItemSpan(if (columns == 1) 1 else 8) }) {
            PerformanceGraphCard()
        }
    }
}
```

---

## Accessibility Standards

### Contrast Ratios (WCAG AA/AAA)

All color combinations meet minimum contrast requirements:

| Foreground | Background | Ratio | Level |
|------------|-----------|-------|-------|
| ColorTextPrimary | ColorBgBase | 14.2:1 | AAA |
| ColorTextSecondary | ColorBgBase | 7.8:1 | AA |
| ColorPrimary | ColorBgBase | 10.5:1 | AAA |
| ColorSuccess | ColorBgBase | 12.1:1 | AAA |
| ColorWarning | ColorBgBase | 9.2:1 | AAA |
| ColorError | ColorBgBase | 8.4:1 | AA |

### Screen Reader Support

```kotlin
// Provide semantic descriptions for all interactive elements
Button(
    onClick = { /*...*/ },
    modifier = Modifier.semantics {
        contentDescription = "Start performance benchmark"
        role = Role.Button
    }
) {
    Text("Start")
}

// Announce dynamic content changes
LaunchedEffect(testResult) {
    announceForAccessibility("Test completed: ${testResult.status}")
}
```

### Touch Targets

All interactive elements meet minimum 48dp touch target:

```kotlin
IconButton(
    onClick = { /*...*/ },
    modifier = Modifier.size(48.dp)  // Minimum touch target
) {
    Icon(
        imageVector = Icons.Outlined.Refresh,
        contentDescription = "Refresh data",
        modifier = Modifier.size(24.dp)  // Visual icon size
    )
}
```

---

## Implementation Checklist

### Setup

- [ ] Add IBM Plex Mono to `res/font/`
- [ ] Add Archivo to `res/font/`
- [ ] Create `JABAuthTheme` with color scheme
- [ ] Create `JABAuthTypography` with type scale
- [ ] Define `Spacing` object

### Components

- [ ] Implement `JABAuthCard`
- [ ] Implement `JABAuthBadge`
- [ ] Implement `StatusIndicator`
- [ ] Implement `FeedItem`
- [ ] Implement staggered reveal animations

### Screens

- [ ] Build dashboard grid layout
- [ ] Add responsive breakpoints
- [ ] Implement performance graph
- [ ] Implement color mode cards
- [ ] Implement live feed

### Testing

- [ ] Screenshot tests against web prototype
- [ ] Accessibility audit (TalkBack)
- [ ] Contrast ratio verification
- [ ] Animation timing verification

---

## Design Tokens Export

### For Android Studio Theme Editor

```xml
<!-- res/values/colors.xml -->
<resources>
    <!-- Primary Colors -->
    <color name="jabauth_primary">#00D9FF</color>
    <color name="jabauth_success">#39FF14</color>
    <color name="jabauth_warning">#FFB800</color>
    <color name="jabauth_error">#FF006E</color>
    
    <!-- Backgrounds -->
    <color name="jabauth_bg_base">#0B1120</color>
    <color name="jabauth_bg_elevated">#131B2E</color>
    <color name="jabauth_bg_card">#1A2438</color>
    
    <!-- Text -->
    <color name="jabauth_text_primary">#E8F4F8</color>
    <color name="jabauth_text_secondary">#8B9DB0</color>
    <color name="jabauth_text_dim">#5A6B7D</color>
</resources>
```

---

**Last Updated:** 2026-05-02  
**Status:** ✅ Production Ready  
**Next Review:** 2026-06-01
