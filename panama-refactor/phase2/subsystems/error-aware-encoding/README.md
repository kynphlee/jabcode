# Error-Aware Encoding Subsystem

**Subsystem ID:** E3  
**Priority:** Medium  
**Estimated Effort:** 2 weeks  
**Dependencies:** E1 (LAB Color Space)  
**Impact:** +5-10% reliability improvement for modes 3-7

---

## 🎯 Objective

Strategically avoid problematic color transitions during encoding to minimize systematic errors, especially for the weak R-channel (85-unit spacing) in modes 3-5 and dual weak channels in modes 6-7.

---

## 📋 Problem Statement

### Uniform Encoding Ignores Color Difficulty

**Current Approach:**
```
Encoder treats all colors equally:
├─ (0,0,0) → (85,0,0): Difficult (R-channel 85-unit)
├─ (0,0,0) → (0,255,0): Easy (G-channel 255-unit)
└─ No awareness of discrimination difficulty
```

**The Problem:**
- Critical data may encode using problematic R transitions
- Error-prone colors used as frequently as robust colors
- No strategic color selection for important bits

---

## 🎯 Solution: Strategic Color Allocation

### Core Concept

**Color Difficulty Classification:**
```
Robust Colors (255-unit transitions):
├─ Mode 2 primaries: Easy to discriminate
├─ G/B channels in Mode 3-5: More reliable
└─ Use for critical data (metadata, high-priority)

Problematic Colors (85-unit transitions):
├─ R-channel gradations: Harder to discriminate
├─ Mid-tone colors: Confusion-prone
└─ Use for less critical data (payload, ECC-protected)
```

**Encoding Strategy:**
```
High-priority bits → Robust color transitions
Low-priority bits → Allow problematic colors
Result: Reduce critical bit errors
```

---

## 🏗️ Architecture

```
┌────────────────────────────────────────────────────┐
│      Error-Aware Encoding Subsystem                │
├────────────────────────────────────────────────────┤
│                                                     │
│  ┌──────────────────┐    ┌──────────────────┐     │
│  │ Color Difficulty │───►│ Strategic        │     │
│  │ Classifier       │    │ Encoder          │     │
│  └──────────────────┘    └────────┬─────────┘     │
│                                   │                │
│  ┌──────────────────┐             │                │
│  │ Data Priority    │─────────────┘                │
│  │ Analyzer         │                              │
│  └──────────────────┘                              │
│           │                                         │
│           ▼                                         │
│  ┌──────────────────────────────────────┐          │
│  │   Optimized Bit → Color Mapping      │          │
│  └──────────────────────────────────────┘          │
│                                                     │
└─────────────────────────────────────────────────────┘
```

---

## 📊 Expected Improvements

| Mode | Baseline (+LAB+Adaptive) | +Error-Aware | Total |
|------|--------------------------|--------------|-------|
| 3 | 54-63% | +5-8% | 59-71% |
| 4 | 46-57% | +5-8% | 51-65% |
| 5 | 47-57% | +8-12% | 55-69% |
| 6 | 33-41% | +5-7% | 38-48% |
| 7 | 26-33% | +3-5% | 29-38% |

---

## 🔬 Technical Approach

### Color Difficulty Scoring

**Assign difficulty score to each color pair:**
```c
typedef struct {
    jab_int32 from_color;
    jab_int32 to_color;
    jab_float difficulty;  // 0.0 (easy) to 1.0 (hard)
} jab_color_transition;

jab_float compute_difficulty(jab_lab_color c1, jab_lab_color c2) {
    jab_float deltaE = delta_e_76(c1, c2);
    
    // Map ΔE to difficulty
    if (deltaE > 20.0) return 0.0;  // Very easy
    if (deltaE > 10.0) return 0.3;  // Easy
    if (deltaE > 5.0)  return 0.6;  // Moderate
    return 1.0;                      // Difficult
}
```

### Data Priority Classification

**Metadata > Payload:**
```
Critical data (low error tolerance):
├─ Version info
├─ Color mode
├─ Data length
└─ Use robust colors only

Payload data (ECC protected):
├─ Actual message content
├─ Protected by LDPC
└─ Can use problematic colors

ECC data (most tolerant):
├─ Error correction parity
├─ Redundant by design
└─ Use any colors
```

### Encoding Optimization

**Constrained Color Selection:**
```c
jab_int32 select_color_for_bit_sequence(
    jab_byte* bits,
    jab_int32 bit_count,
    jab_boolean is_critical
) {
    jab_int32* candidates = get_candidate_colors(bits);
    
    if (is_critical) {
        // Filter to robust colors only
        candidates = filter_robust_colors(candidates);
    }
    
    // Select best from remaining candidates
    return select_optimal_color(candidates, current_color);
}
```

---

## 📁 Implementation Files

- `src/jabcode/error_aware_encoder.c`
- `src/jabcode/error_aware_encoder.h`
- `src/jabcode/color_difficulty.c`

---

## 🚀 Session Guides

- `SESSIONS_1-2_DIFFICULTY.md` - Color difficulty scoring
- `SESSIONS_3-4_INTEGRATION.md` - Encoder integration
- `SESSIONS_5-6_VALIDATION.md` - Testing and optimization

---

**Status:** 📋 Designed  
**Dependencies:** E1 (LAB for difficulty scoring)  
**Next:** Implement difficulty classification
