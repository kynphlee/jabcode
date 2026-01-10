# Iterative Decoding Subsystem

**Subsystem ID:** E5  
**Priority:** High  
**Estimated Effort:** 3 weeks  
**Dependencies:** E1 (LAB Color Space)  
**Impact:** +10-15% reliability improvement for modes 3-7

---

## 🎯 Objective

Use multi-pass decoding with LDPC feedback to refine ambiguous color decisions, especially for problematic 85-unit and 36-unit spacing in modes 3-7.

---

## 📋 Problem Statement

### Single-Pass Decoding Limitations

**Current Approach:**
```
One-shot color decision:
├─ Sample module color
├─ Find nearest palette color (RGB/LAB)
├─ Decode bit pattern
├─ No refinement
└─ Final answer

Problem:
├─ Ambiguous colors decided once
├─ No use of LDPC feedback
├─ No spatial context exploitation
└─ Systematic errors propagate
```

---

## 🎯 Iterative Solution

### Multi-Pass Refinement

**Core Concept:**
```
Pass 1: Initial decode (best guess)
Pass 2: LDPC error detection
Pass 3: Refine ambiguous colors
Pass 4: Re-decode with confidence
Iterate until convergence
```

**Benefits:**
- Use LDPC parity check feedback
- Refine uncertain color decisions
- Exploit spatial correlation
- Iteratively improve accuracy

---

## 🏗️ Architecture

```
┌────────────────────────────────────────────────┐
│       Iterative Decoding Subsystem             │
├────────────────────────────────────────────────┤
│                                                 │
│  ┌──────────────┐     ┌──────────────┐        │
│  │ Initial      │────►│ LDPC         │        │
│  │ Decode       │     │ Check        │        │
│  └──────────────┘     └──────┬───────┘        │
│                              │                 │
│                              ▼                 │
│  ┌──────────────┐     ┌──────────────┐        │
│  │ Confidence   │◄────│ Error        │        │
│  │ Scoring      │     │ Localization │        │
│  └──────┬───────┘     └──────────────┘        │
│         │                                      │
│         ▼                                      │
│  ┌──────────────┐     ┌──────────────┐        │
│  │ Color        │────►│ Re-decode    │        │
│  │ Refinement   │     │ (Pass N)     │        │
│  └──────────────┘     └──────┬───────┘        │
│                              │                 │
│                              ▼                 │
│                       Converged? ──No─┐       │
│                              │         │       │
│                             Yes        │       │
│                              ▼         │       │
│                          Success   ◄───┘       │
│                                                 │
└─────────────────────────────────────────────────┘
```

---

## 📊 Expected Improvements

| Mode | Baseline (+LAB+Adaptive+Error) | +Iterative | Total |
|------|-------------------------------|------------|-------|
| 3 | 59-71% | +10-14% | 69-85% ✅ |
| 4 | 51-65% | +8-12% | 59-77% |
| 5 | 55-69% | +10-15% | 65-84% ✅ |
| 6 | 38-48% | +8-12% | 46-60% |
| 7 | 29-38% | +5-8% | 34-46% |

**Key Insight:** Iterative decoding pushes Mode 3 and Mode 5 into production viability!

---

## 🔬 Technical Approach

### Confidence Scoring

**Assign confidence to each color decision:**
```c
typedef struct {
    jab_int32 color_index;     // Chosen color
    jab_float confidence;      // 0.0 (uncertain) to 1.0 (certain)
    jab_float second_best_de;  // Distance to 2nd choice
} jab_color_decision;

jab_float compute_confidence(
    jab_lab_color observed,
    jab_lab_color* palette,
    jab_int32 color_count
) {
    // Find nearest and second-nearest
    jab_float min_de = INFINITY;
    jab_float second_de = INFINITY;
    
    for (int i = 0; i < color_count; i++) {
        jab_float de = delta_e_76(observed, palette[i]);
        if (de < min_de) {
            second_de = min_de;
            min_de = de;
        } else if (de < second_de) {
            second_de = de;
        }
    }
    
    // Confidence = separation between 1st and 2nd choice
    jab_float separation = second_de - min_de;
    if (separation > 10.0) return 1.0;  // Very confident
    if (separation > 5.0)  return 0.8;  // Confident
    if (separation > 2.0)  return 0.5;  // Uncertain
    return 0.2;                          // Very uncertain
}
```

### LDPC Feedback Integration

**Use parity checks to identify errors:**
```c
jab_boolean* ldpc_identify_errors(
    jab_byte* decoded_bits,
    jab_int32 bit_count,
    jab_ldpc_params* ldpc
) {
    // Run LDPC parity checks
    jab_boolean* syndrome = compute_syndrome(decoded_bits, ldpc);
    
    // Identify modules with parity violations
    jab_boolean* suspect_modules = map_syndrome_to_modules(syndrome);
    
    return suspect_modules;
}
```

### Iterative Refinement

**Refine low-confidence, high-error modules:**
```c
void iterative_decode(jab_decode* dec, jab_int32 max_iterations) {
    jab_color_decision* decisions = initial_decode_with_confidence(dec);
    
    for (int iter = 0; iter < max_iterations; iter++) {
        // LDPC check
        jab_boolean* errors = ldpc_identify_errors(dec->bits, dec->ldpc);
        
        // Find low-confidence modules in error regions
        jab_int32* refine_targets = find_uncertain_errors(
            decisions,
            errors,
            0.5  // confidence threshold
        );
        
        if (refine_targets == NULL) {
            break;  // Converged
        }
        
        // Try alternative color choices for uncertain modules
        refine_color_decisions(dec, refine_targets, decisions);
        
        // Re-run LDPC
        if (ldpc_successful(dec->bits, dec->ldpc)) {
            break;  // Success!
        }
    }
}
```

### Spatial Context Exploitation

**Use neighboring modules for hints:**
```c
jab_int32 refine_with_spatial_context(
    jab_int32 module_x,
    jab_int32 module_y,
    jab_lab_color observed,
    jab_color_decision* neighbor_decisions
) {
    // Check neighbors' confidence
    jab_float avg_neighbor_confidence = 
        average_confidence(neighbor_decisions);
    
    if (avg_neighbor_confidence > 0.8) {
        // Neighbors are confident, use as context
        // Prefer colors similar to confident neighbors
        return select_contextual_color(observed, neighbor_decisions);
    }
    
    // Neighbors uncertain, use pure LAB distance
    return find_nearest_color_lab(observed);
}
```

---

## 📁 Implementation Files

### Core
- `src/jabcode/iterative_decoder.c` - Main iterative loop
- `src/jabcode/iterative_decoder.h` - API definitions
- `src/jabcode/confidence_scorer.c` - Confidence calculation

### Java Wrapper
- `panama-wrapper/.../IterativeDecoder.java`
- `panama-wrapper/.../ConfidenceMetrics.java`

### Tests
- `src/jabcode/test_iterative_decoder.c`
- `panama-wrapper-itest/.../IterativeDecoderTest.java`

---

## 🚀 Session Guides

- `SESSIONS_1-2_CONFIDENCE.md` - Confidence scoring implementation
- `SESSIONS_3-4_LDPC_FEEDBACK.md` - LDPC integration
- `SESSIONS_5-6_ITERATION.md` - Iterative loop and refinement
- `SESSIONS_7-8_VALIDATION.md` - Testing and optimization

---

## 🎯 Success Criteria

### Quantitative
- [ ] Mode 3: Achieve 80%+ pass rate
- [ ] Mode 5: Achieve 75%+ pass rate
- [ ] Convergence: < 5 iterations typical
- [ ] Performance: < 2× decode time vs single-pass

### Qualitative
- [ ] Stable convergence behavior
- [ ] No oscillation between solutions
- [ ] Graceful degradation if no convergence
- [ ] Clear logging of iteration progress

---

**Status:** 📋 Designed, ready for implementation  
**Priority:** HIGH - This is the final push to viability  
**Dependencies:** E1 (LAB for confidence scoring)  
**Impact:** Makes Modes 3 and 5 production-viable!
