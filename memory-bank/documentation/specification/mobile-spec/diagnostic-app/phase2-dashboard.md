# Phase 2: Dashboard Screen Implementation

**Duration:** 5 days  
**Dependencies:** Phase 1 complete ✅  
**Status:** 🟡 In Progress (Day 1)

---

## Overview

Implement the diagnostic dashboard UI matching `diagnostic-dashboard.html` prototype.

**Coverage Target:** 75%+ (20 tests: 10 screenshot + 5 interaction + 5 integration)

---

## Components to Implement

### **1. DashboardHeader** (Day 1)
- Logo with gradient background
- App name + version
- Live status indicator (pulsing dot)

### **2. MetricsBar** (Day 1)
- 5 live metrics in grid:
  - Avg Encode Time
  - Avg Decode Time
  - Success Rate
  - Active Tests
  - Device Name
- Real-time updates from `DashboardViewModel`

### **3. ColorModeGrid** (Day 2)
- 6 cards for color modes (4, 8, 16, 32, 64, 128)
- Click to select mode
- Show avg latency for each
- Active state highlighting

### **4. PerformanceChart** (Day 3)
- Canvas-based bar graph
- 6 bars (one per color mode)
- Staggered growth animation
- Grid background
- Hover tooltips

### **5. LiveFeed** (Day 4)
- LazyColumn of feed items
- 3 types: success, warning, error
- Icon, title, description, metadata
- Auto-scroll to latest

### **6. AlertSection** (Day 4)
- Warning and error cards
- Dismissible alerts
- Recommendation badges

---

## Testing Strategy

### **Screenshot Tests (10)**
```kotlin
@Test
fun dashboardHeader_matchesPrototype() {
    composeTestRule.setContent {
        DashboardHeader()
    }
    
    composeTestRule.onRoot()
        .captureToImage()
        .assertAgainstGolden("dashboard-header")
}
```

### **Interaction Tests (5)**
```kotlin
@Test
fun colorModeCard_selectsOnClick() {
    val viewModel = DashboardViewModel()
    
    composeTestRule.setContent {
        ColorModeCard(
            colorMode = 8,
            isSelected = false,
            onClick = { viewModel.selectColorMode(8) }
        )
    }
    
    composeTestRule.onNode(hasText("8")).performClick()
    assertEquals(8, viewModel.selectedColorMode.value)
}
```

---

**Reference:** `@/swift-java-wrapper/android/ui-prototypes/diagnostic-dashboard.html`

---

**Last Updated:** 2026-05-02
