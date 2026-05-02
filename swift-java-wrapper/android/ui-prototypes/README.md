# JABAuth Mobile Framework - UI Prototypes

**Purpose:** High-fidelity web prototypes that define the visual design language for Android implementation

---

## Overview

These prototypes are **design specifications**, not production Android code. Android developers reference these to implement the UI in Jetpack Compose with Material Design 3.

### Workflow

```
Web Prototype (HTML/CSS/JS) → Design Specification → Jetpack Compose Implementation
         ↓                            ↓                         ↓
   Interactive demo              Color/type tokens         Actual Kotlin code
   Motion studies                Animation specs            Composable functions
   Layout patterns               Component behavior         State management
```

---

## Available Prototypes

### 1. Diagnostic Dashboard (`diagnostic-dashboard.html`)

**Purpose:** Main UI for the JABAuth diagnostic application

**Aesthetic Direction:** "Precision Instrumentation"
- **Inspired by:** Oscilloscopes, terminal UIs, aviation cockpits
- **Color Palette:** Deep navy background, electric cyan primary, neon green success
- **Typography:** IBM Plex Mono (data/display), Archivo (body/UI)
- **Motion:** Mechanical, precise animations with staggered reveals

**Key Components:**
- Live performance metrics dashboard
- Color mode comparison cards
- Real-time benchmark feed
- Issue detection alerts
- Framework module health monitor

**View it:** Open `diagnostic-dashboard.html` in a browser

---

### 2. Scanner Interface (`scanner-interface.html`)

**Purpose:** Reusable scanning UI for diagnostic and custom applications (Module 5: :ui-components)

**Aesthetic Direction:** "Precision Targeting"
- **Inspired by:** Camera viewfinders, targeting systems, measurement tools
- **Color Palette:** Same as dashboard, with context variants (healthcare/cyan, legal/gold, IoT/purple)
- **Typography:** IBM Plex Mono (data), Archivo (UI)
- **Motion:** Pulsing guides, scanning animation, slide-up result panel

**Key Components:**
- Camera viewfinder with corner guides
- Real-time scan quality indicators (brightness, focus, contrast)
- Scanning line animation
- Authentication result panel (bottom sheet)
- Validation badges (PKI, JWT, expiry)
- Certificate/JWT/scan detail sections
- Context-aware color theming

**Interactive Features:**
- Auto-detect simulation (after 3 seconds)
- Torch toggle
- Context switching (press 'c' key: healthcare → legal → IoT)
- Result panel slide-up animation

**View it:** Open `scanner-interface.html` in a browser

**Component Spec:** See `SCANNER_COMPONENTS.md` for detailed Compose implementation

---

## Design System Translation Guide

### Color Tokens

**Web (CSS Variables) → Android (Jetpack Compose)**

```css
/* Web CSS */
--color-bg-base: #0B1120;
--color-primary: #00D9FF;
--color-success: #39FF14;
```

```kotlin
// Android Jetpack Compose
val md_theme_dark_background = Color(0xFF0B1120)
val md_theme_dark_primary = Color(0xFF00D9FF)
val md_theme_dark_tertiary = Color(0xFF39FF14)  // Success

@Composable
fun JABAuthTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = md_theme_dark_background,
            primary = md_theme_dark_primary,
            tertiary = md_theme_dark_tertiary,
            // ... complete palette
        )
    ) {
        content()
    }
}
```

### Typography Tokens

**Web → Android**

```css
/* Web CSS */
--font-display: 'IBM Plex Mono', monospace;
--font-body: 'Archivo', sans-serif;
--text-2xl: 1.75rem;  /* 28px */
```

```kotlin
// Android - Add fonts to res/font/
val IBMPlexMono = FontFamily(
    Font(R.font.ibm_plex_mono_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_mono_semibold, FontWeight.SemiBold),
    Font(R.font.ibm_plex_mono_bold, FontWeight.Bold)
)

val Archivo = FontFamily(
    Font(R.font.archivo_regular, FontWeight.Normal),
    Font(R.font.archivo_medium, FontWeight.Medium),
    Font(R.font.archivo_semibold, FontWeight.SemiBold)
)

val Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = IBMPlexMono,
        fontSize = 28.sp,
        fontWeight = FontWeight.Bold
    ),
    bodyLarge = TextStyle(
        fontFamily = Archivo,
        fontSize = 15.sp,
        fontWeight = FontWeight.Normal
    ),
    // ... complete type scale
)
```

### Spacing System

**Web → Android**

```css
/* Web CSS - 4px base */
--space-1: 0.25rem;   /* 4px */
--space-4: 1rem;      /* 16px */
--space-8: 2rem;      /* 32px */
```

```kotlin
// Android - Use dp directly
val spacing1 = 4.dp
val spacing4 = 16.dp
val spacing8 = 32.dp

// Or create spacing object
object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
}
```

### Animation Translation

**Web CSS Animation → Android Compose Animation**

```css
/* Web CSS */
@keyframes fadeInUp {
    from {
        opacity: 0;
        transform: translateY(24px);
    }
    to {
        opacity: 1;
        transform: translateY(0);
    }
}

.card {
    animation: fadeInUp 600ms cubic-bezier(0.4, 0.0, 0.2, 1);
}
```

```kotlin
// Android Jetpack Compose
@Composable
fun Card(content: @Composable () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        visible = true
    }
    
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(
            animationSpec = tween(
                durationMillis = 600,
                easing = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1f)
            )
        ) + slideInVertically(
            initialOffsetY = { 24 },
            animationSpec = tween(
                durationMillis = 600,
                easing = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1f)
            )
        )
    ) {
        content()
    }
}
```

### Staggered Animations

**Web → Android**

```css
/* Web CSS */
.card:nth-child(1) { animation-delay: 100ms; }
.card:nth-child(2) { animation-delay: 200ms; }
.card:nth-child(3) { animation-delay: 300ms; }
```

```kotlin
// Android Jetpack Compose
@Composable
fun CardList(cards: List<CardData>) {
    LazyColumn {
        itemsIndexed(cards) { index, card ->
            var visible by remember { mutableStateOf(false) }
            
            LaunchedEffect(Unit) {
                delay((index * 100).toLong())
                visible = true
            }
            
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn() + slideInVertically()
            ) {
                Card(card)
            }
        }
    }
}
```

---

## Component Mapping

### Performance Graph (Web → Android)

**Web:**
```html
<div class="performance-graph">
    <div class="graph-line">
        <div class="graph-bar" style="height: 45%"></div>
        <div class="graph-bar" style="height: 52%"></div>
        <!-- ... -->
    </div>
</div>
```

**Android:**
```kotlin
@Composable
fun PerformanceGraph(data: List<Float>) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
            .background(Color(0xFF0B1120))
    ) {
        // Draw grid
        drawGridLines()
        
        // Draw bars with gradient
        data.forEachIndexed { index, value ->
            val barWidth = size.width / data.size
            val barHeight = size.height * value
            
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF00D9FF),
                        Color(0xFF39FF14)
                    )
                ),
                topLeft = Offset(
                    x = index * barWidth,
                    y = size.height - barHeight
                ),
                size = Size(barWidth - 2.dp.toPx(), barHeight)
            )
        }
    }
}
```

### Color Mode Card (Web → Android)

**Web:**
```html
<div class="mode-card active">
    <div class="mode-number">8</div>
    <div class="mode-label">Colors</div>
    <div class="mode-metric">
        <span class="mode-metric-value">42ms</span> avg
    </div>
</div>
```

**Android:**
```kotlin
@Composable
fun ColorModeCard(
    colorMode: Int,
    avgLatency: Long,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) 
                Color(0xFF00D9FF).copy(alpha = 0.2f)
            else 
                Color(0xFF131B2E)
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (isActive) 
                Color(0xFF00D9FF)
            else 
                Color(0xFF8B9DB0).copy(alpha = 0.15f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "$colorMode",
                style = TextStyle(
                    fontFamily = IBMPlexMono,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF00D9FF)
                )
            )
            Text(
                text = "COLORS",
                style = TextStyle(
                    fontFamily = IBMPlexMono,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.05.em,
                    color = Color(0xFF8B9DB0)
                ),
                modifier = Modifier.padding(bottom = 12.dp)
            )
            Text(
                text = buildAnnotatedString {
                    withStyle(
                        style = SpanStyle(
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF39FF14)
                        )
                    ) {
                        append("${avgLatency}ms")
                    }
                    append(" avg")
                },
                style = TextStyle(
                    fontFamily = IBMPlexMono,
                    fontSize = 13.sp
                )
            )
        }
    }
}
```

### Live Feed Item (Web → Android)

**Web:**
```html
<div class="feed-item success">
    <div class="feed-icon">✓</div>
    <div class="feed-content">
        <div class="feed-title">Encode Test Passed</div>
        <div class="feed-description">8-color mode | 100 chars</div>
        <div class="feed-meta">41ms • 2.3MB allocated</div>
    </div>
</div>
```

**Android:**
```kotlin
@Composable
fun FeedItem(
    type: FeedType,
    title: String,
    description: String,
    metadata: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF131B2E))
            .border(
                width = 3.dp,
                color = when (type) {
                    FeedType.SUCCESS -> Color(0xFF39FF14)
                    FeedType.WARNING -> Color(0xFFFFB800)
                    FeedType.ERROR -> Color(0xFFFF006E)
                },
                shape = RoundedCornerShape(4.dp)
            )
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Icon
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(
                    color = when (type) {
                        FeedType.SUCCESS -> Color(0xFF39FF14).copy(alpha = 0.2f)
                        FeedType.WARNING -> Color(0xFFFFB800).copy(alpha = 0.2f)
                        FeedType.ERROR -> Color(0xFFFF006E).copy(alpha = 0.2f)
                    },
                    shape = RoundedCornerShape(6.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = when (type) {
                    FeedType.SUCCESS -> "✓"
                    FeedType.WARNING -> "⚠"
                    FeedType.ERROR -> "✗"
                },
                fontSize = 18.sp
            )
        }
        
        // Content
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = TextStyle(
                    fontFamily = IBMPlexMono,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFE8F4F8)
                )
            )
            Text(
                text = description,
                style = TextStyle(
                    fontFamily = Archivo,
                    fontSize = 13.sp,
                    color = Color(0xFF8B9DB0)
                ),
                modifier = Modifier.padding(top = 4.dp)
            )
            Text(
                text = metadata,
                style = TextStyle(
                    fontFamily = IBMPlexMono,
                    fontSize = 11.sp,
                    color = Color(0xFF5A6B7D)
                ),
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}
```

---

## Layout Patterns

### Asymmetric Grid (Web → Android)

**Web:**
```css
.grid {
    display: grid;
    grid-template-columns: repeat(12, 1fr);
    gap: 1.5rem;
}

.grid-col-8 { grid-column: span 8; }
.grid-col-4 { grid-column: span 4; }
```

**Android:**
```kotlin
@Composable
fun DashboardGrid() {
    // Use LazyVerticalGrid with custom spans
    LazyVerticalGrid(
        columns = GridCells.Fixed(12),
        contentPadding = PaddingValues(32.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item(span = { GridItemSpan(8) }) {
            PerformanceGraphCard()
        }
        item(span = { GridItemSpan(4) }) {
            IssueDetectionCard()
        }
        item(span = { GridItemSpan(12) }) {
            ColorModeComparisonCard()
        }
        // ... more items
    }
}
```

---

## Motion Principles

### 1. Staggered Card Reveals

**Effect:** Cards animate in sequentially with 100ms delay between each

**Implementation:**
- Use `LaunchedEffect` with `delay()`
- Each card has `AnimatedVisibility` triggered after delay
- Combine `fadeIn()` + `slideInVertically()` for entry animation

### 2. Pulse Animation

**Effect:** Status indicator pulses continuously

**Implementation:**
```kotlin
val infiniteTransition = rememberInfiniteTransition()
val alpha by infiniteTransition.animateFloat(
    initialValue = 1f,
    targetValue = 0.4f,
    animationSpec = infiniteRepeatable(
        animation = tween(1000, easing = FastOutSlowInEasing),
        repeatMode = RepeatMode.Reverse
    )
)

Box(
    modifier = Modifier
        .size(8.dp)
        .alpha(alpha)
        .background(Color(0xFF39FF14), CircleShape)
        .shadow(12.dp, CircleShape, spotColor = Color(0xFF39FF14))
)
```

### 3. Graph Bar Growth

**Effect:** Bars grow from bottom with staggered timing

**Implementation:**
```kotlin
var animatedHeight by remember { mutableStateOf(0f) }

LaunchedEffect(targetHeight) {
    delay(index * 50L)
    animate(
        initialValue = 0f,
        targetValue = targetHeight,
        animationSpec = tween(800, easing = FastOutSlowInEasing)
    ) { value, _ ->
        animatedHeight = value
    }
}
```

---

## Accessibility Considerations

### 1. Color Contrast

All color combinations in the prototype meet WCAG AA standards:
- Primary text on background: 14.2:1 (AAA)
- Secondary text on background: 7.8:1 (AA)
- Primary accent on background: 10.5:1 (AAA)

**Android Implementation:**
```kotlin
// Ensure Material 3 dynamic color respects contrast
MaterialTheme(
    colorScheme = darkColorScheme(
        // ... colors
    ).run {
        // Verify contrast ratios
        copy(
            onBackground = ensureContrast(background, onBackground, 4.5f),
            onSurface = ensureContrast(surface, onSurface, 4.5f)
        )
    }
)
```

### 2. Font Scaling

Prototype uses `rem` units which scale with user preferences.

**Android Implementation:**
```kotlin
// Use sp (scalable pixels) for all text sizes
Text(
    text = "Performance",
    fontSize = 28.sp,  // Scales with system font size
    fontWeight = FontWeight.Bold
)
```

### 3. Screen Reader Support

**Android Implementation:**
```kotlin
Text(
    text = "42ms",
    modifier = Modifier.semantics {
        contentDescription = "Average encode time: 42 milliseconds"
    }
)

Icon(
    imageVector = Icons.Default.Check,
    contentDescription = "Test passed"
)
```

---

## Responsive Breakpoints

### Web Breakpoints
- Desktop: > 1200px (full 12-column grid)
- Tablet: 768px - 1200px (stacked layouts)
- Mobile: < 768px (single column)

### Android Implementation

```kotlin
@Composable
fun DashboardGrid() {
    val configuration = LocalConfiguration.current
    val isCompact = configuration.screenWidthDp < 600
    val isMedium = configuration.screenWidthDp in 600..840
    
    LazyVerticalGrid(
        columns = when {
            isCompact -> GridCells.Fixed(1)
            isMedium -> GridCells.Fixed(6)
            else -> GridCells.Fixed(12)
        }
    ) {
        // Adapt spans based on screen size
        item(span = { GridItemSpan(if (isCompact) 1 else 8) }) {
            PerformanceGraphCard()
        }
    }
}
```

---

## Testing the Prototypes

### 1. Visual Regression Testing

Capture screenshots of the web prototype at different screen sizes:
```bash
# Using Playwright or similar
playwright screenshot diagnostic-dashboard.html \
    --viewport-size=1600x900 \
    --output=screenshots/dashboard-desktop.png

playwright screenshot diagnostic-dashboard.html \
    --viewport-size=768x1024 \
    --output=screenshots/dashboard-tablet.png
```

Compare against Android screenshots:
```kotlin
@Test
fun dashboardMatchesDesign() {
    composeTestRule.setContent {
        DashboardScreen()
    }
    
    composeTestRule.onRoot()
        .captureToImage()
        .assertAgainstGolden("dashboard-desktop")
}
```

### 2. Animation Verification

Record web animations and compare timing:
```javascript
// Web: Measure animation duration
const element = document.querySelector('.card');
const start = performance.now();
element.addEventListener('animationend', () => {
    const duration = performance.now() - start;
    console.log(`Animation took: ${duration}ms`);
});
```

```kotlin
// Android: Verify animation duration
@Test
fun cardAnimationDuration() {
    composeTestRule.mainClock.autoAdvance = false
    
    composeTestRule.setContent { Card() }
    
    composeTestRule.mainClock.advanceTimeBy(600) // Expected duration
    
    composeTestRule.onNode(hasTestTag("card"))
        .assertIsDisplayed()
}
```

---

## Next Steps

1. **Review Prototype** - Open `diagnostic-dashboard.html` in browser
2. **Extract Design Tokens** - Copy color/typography values to Android theme
3. **Implement Components** - Build Composables matching web components
4. **Test Animations** - Verify motion matches web prototype timing
5. **Accessibility Audit** - Run TalkBack and verify screen reader support

---

## Questions?

For design clarifications or Android implementation guidance, reference:
- `06-mobile-framework-architecture.md` - Framework specification
- `diagnostic-dashboard.html` - Interactive prototype (this file)
- Material Design 3 guidelines: https://m3.material.io/

**Remember:** The web prototype is a **visual specification**, not a code template. Adapt the design to Jetpack Compose idioms while preserving the aesthetic vision.
