# Hybrid Modes Subsystem

**Subsystem ID:** E4  
**Priority:** Medium  
**Estimated Effort:** 2-3 weeks  
**Dependencies:** E1, E2, E3  
**Impact:** +5-8% reliability for modes 3-7

---

## 🎯 Objective

Use different color modes for different barcode regions to maximize reliability while maintaining high data capacity.

---

## 📋 Problem Statement

### Uniform Color Density Issues

**Current Approach:**
```
Entire barcode uses same color mode:
├─ Metadata: Mode 5 (64 colors)
├─ Data region: Mode 5 (64 colors)
├─ Finder patterns: Mode 5 (64 colors)
└─ One size fits all

Problem:
├─ Metadata needs reliability (critical)
├─ Data region needs capacity (volume)
└─ Finder patterns need visibility (alignment)
```

---

## 🎯 Hybrid Solution

### Region-Specific Color Modes

**Strategic Mode Assignment:**
```
Finder Patterns: Mode 2 (8 colors)
├─ Maximum visibility
├─ Cyan corners unmistakable
└─ Critical for alignment

Metadata Region: Mode 2-3 (8-16 colors)
├─ High reliability needed
├─ Small data volume
└─ Can afford lower density

Data Region: Mode 5 (64 colors)
├─ High capacity needed
├─ LDPC protected
└─ Acceptable error rate

Result: Best of both worlds
```

---

## 🏗️ Architecture

```
┌─────────────────────────────────────────────┐
│         Hybrid Modes Subsystem              │
├─────────────────────────────────────────────┤
│                                              │
│  ┌──────────────┐    ┌──────────────┐      │
│  │ Region       │───►│ Mode         │      │
│  │ Classifier   │    │ Selector     │      │
│  └──────────────┘    └──────┬───────┘      │
│                              │               │
│  ┌──────────────────────────▼────────┐     │
│  │    Hybrid Encoder/Decoder         │     │
│  │  (Multi-mode support)             │     │
│  └───────────────────────────────────┘     │
│                                              │
└──────────────────────────────────────────────┘
```

---

## 📊 Expected Improvements

**Mode 5 Example:**
```
Uniform Mode 5: 47-57% pass rate

Hybrid (Mode 2 metadata + Mode 5 data):
├─ Metadata: 100% reliable (Mode 2)
├─ Data: 47-57% (Mode 5, LDPC protected)
└─ Overall: 52-62% (+5-10% improvement)

Why: Metadata errors eliminated
```

---

## 🔬 Technical Approach

### Region Definition

```c
typedef enum {
    REGION_FINDER,
    REGION_METADATA,
    REGION_DATA,
    REGION_ECC
} jab_region_type;

typedef struct {
    jab_region_type region;
    jab_int32 color_mode;  // Nc value for this region
    jab_byte* palette;     // Region-specific palette
} jab_hybrid_region;
```

### Encoding Strategy

```c
void encode_hybrid_barcode(jab_encode* enc) {
    // Finder patterns: Always Mode 2
    encode_region(enc, REGION_FINDER, 2);
    
    // Metadata: Mode 2-3 (based on size)
    encode_region(enc, REGION_METADATA, 2);
    
    // Data: Use requested mode
    encode_region(enc, REGION_DATA, enc->color_number);
    
    // ECC: Can use any mode (redundant)
    encode_region(enc, REGION_ECC, enc->color_number);
}
```

---

## 📁 Implementation Files

- `src/jabcode/hybrid_encoder.c`
- `src/jabcode/hybrid_decoder.c`
- `src/jabcode/region_classifier.c`

---

## 🚀 Session Guides

- `SESSIONS_1-2_REGIONS.md` - Region classification
- `SESSIONS_3-4_ENCODING.md` - Hybrid encoding
- `SESSIONS_5-6_DECODING.md` - Hybrid decoding

---

**Status:** 📋 Designed  
**Dependencies:** E1, E2, E3  
**Impact:** Improves metadata reliability significantly
