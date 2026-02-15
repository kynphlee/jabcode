# 16+ Color Mode Finder Pattern Detection Failure Analysis

**Date:** 2026-01-29  
**Issue:** Camera decoder successfully detects 4/8-color JABCodes but fails to detect 16+ color modes  
**Error:** "No JABCode found in image" - finder pattern detection fails before any decode attempt

## Specification Audit Results

### Finder Pattern Requirements (ISO-IEC-23634 Section 6.3)

**Critical Finding:** Finder patterns use **FIXED colors** regardless of data module color mode:
- **Black** (0, 0, 0)
- **Cyan** (0, 255, 255)  
- **Yellow** (255, 255, 0)

These are part of the base 8-color palette and remain constant across all color modes (4, 8, 16, 32, 64, 128, 256).

### Finder Pattern Detection Algorithm

Per Section 6.3, the detection process:

1. **Scans G channel horizontally** for C1-C2 alternating 0/255 pattern
2. **Crosschecks B channel** for same pattern
3. **Verifies R channel** has constant 0 (for UL/LL) or alternating 0/255 (for UR/LR)
4. **Validates layer ratios** p:1:1:1:q with 50% tolerance
5. **Crosschecks in multiple directions** (horizontal, vertical, diagonal)

**Expected behavior:** Finder patterns should be detectable in ALL color modes because they use the same binary colors (0 or 255 in each channel).

### Color Classification/Binarization (ISO-IEC-23634 Section 6.2)

The pre-processing algorithm:

1. **Divides image into blocks** (default 32×32 pixels)
2. **Calculates block averages** for R, G, B channels separately
3. **Classifies each pixel** based on comparison to block average:
   - If R, G, B all < block average → **Black (0, 0, 0)**
   - If std dev < 0.08 and R, G, B all > average → **White (255, 255, 255)**
   - If std dev ≥ 0.08: Set max channel to 255, min to 0, middle based on ratios

**Problem:** This algorithm assumes a **limited color palette** for proper binarization.

## Root Cause Analysis

### Why 16+ Color Modes Fail

**16-color palette** (per Annex G, Table G.1):
```
Index   R     G     B     (Intermediate values present)
0       0     0     0     
1       0     0     255   
4       85    0     0     ← INTERMEDIATE VALUE
5       85    0     255   ← INTERMEDIATE VALUE
8       170   0     0     ← INTERMEDIATE VALUE
12      255   0     0     
14      255   255   0     (Yellow - finder pattern color)
```

**The issue:**

1. **Block average contamination:**
   - Data modules with R=85, R=170 shift block averages
   - Finder pattern pixels (R=0 or R=255) may be misclassified relative to contaminated averages
   - Example: If block average R=100 (due to mix of 85/170 values), then:
     - Black finder pixel (R=0) correctly classified as < average ✓
     - Yellow finder pixel (R=255) correctly classified as > average ✓
     - **BUT** intermediate data pixels (R=85, R=170) also get binarized, creating noise

2. **Standard deviation threshold failure:**
   - Spec uses 0.08 std dev threshold to distinguish "colorful" vs "grayscale" pixels
   - With intermediate values (85, 170), std dev calculations produce false positives
   - Pixels that should be distinct colors get collapsed to white or black

3. **Pattern recognition confusion:**
   - Finder pattern scanner looks for **exact** p:C1:C2:C1:C2:q layer widths
   - Intermediate-value data modules near finder pattern edges disrupt layer boundary detection
   - Scanner may find partial matches but fail crosscheck validation (Step i, Section 6.3)

### Specific Failure Modes

**Observed:** "No JABCode found in image"

**Likely causes:**
1. **Insufficient found-counter:** Candidates detected but FC < 3 threshold (Section 6.3, step after m)
2. **Failed crosscheck:** Horizontal scan succeeds but vertical/diagonal scans fail (Section 6.3, step i)
3. **Layer ratio validation failure:** Intermediate values disrupt AB, BC, CD measurements (Section 6.3, step f)

## Specification Compliance Issues

### What the Spec Says

**Section 6.2** - Color classification:
> "After the colour classification process, C1 and C2 shall have a value of either 0 or 255 in each colour channel, R, G, and B."

**Annex G** - Extended color modes:
> "If more than eight colours are used for closed, user defined applications, the following guideline should be considered."

**Note:** 16+ color modes are described in **Annex G (informative)** - not normative requirements.

### The Disconnect

- **Normative sections (6.2, 6.3)** assume **binary binarization** (0 or 255 only)
- **Informative Annex G** introduces **intermediate values** (85, 170, 36, 73, 109, etc.)
- **Finder patterns use binary colors** (0, 255) but sit in an image with **non-binary data modules**

**The binarization algorithm is optimized for 4/8-color modes only.**

## Required Fixes

### Option 1: Enhanced Binarization (Spec-Compliant)

Modify color classification to preserve finder pattern detection:

1. **Two-pass classification:**
   - First pass: Detect and isolate finder pattern candidate regions
   - Second pass: Apply different binarization thresholds inside vs outside finder regions

2. **Adaptive block averaging:**
   - Exclude extreme values (0, 255) from block average calculations
   - Prevents finder pattern pixels from contaminating averages

3. **Luminance-based finder detection:**
   - Use Y = 0.299R + 0.587G + 0.114B for initial finder pattern search
   - Binarize luminance only, ignore chroma for pattern detection
   - Apply color validation only AFTER pattern geometry confirmed

### Option 2: Robust Pattern Matching

Enhance finder pattern scanner resilience:

1. **Relaxed layer validation:**
   - Increase tolerance from 40% to 60% for layer size consistency
   - Allow for noise at layer boundaries from intermediate colors

2. **Multi-threshold scanning:**
   - Try multiple binarization thresholds (e.g., 64, 128, 192)
   - Accept pattern if ANY threshold produces valid crosscheck

3. **Core-first detection:**
   - Search for solid color cores (5×5 module centers)
   - Expand outward to validate layer structure
   - Less sensitive to edge noise

### Option 3: Metadata-Assisted Detection (Hybrid)

For known-clean synthetic images or controlled environments:

1. **Provide color mode hint** to decoder
2. **Apply mode-specific binarization:**
   - 4/8-color: Use spec algorithm (Section 6.2)
   - 16+ color: Use luminance-based detection + direct RGB matching

## Recommended Implementation

**Short-term (mobile app):**
- Implement Option 1, approach 3: Luminance-based finder detection
- Minimal changes to existing detector.c
- Preserves spec compliance for 4/8-color modes

**Long-term (library improvement):**
- Submit issue to JABCode reference implementation
- Propose normative update for 16+ color mode detection
- Add test suite for all color modes with camera-captured images

## Testing Requirements

1. **Generate test images** for each mode with same data content
2. **Test on physical displays** (not just synthetic bitmaps)
3. **Vary lighting conditions** (normal, low-light, high-contrast)
4. **Measure detection rate** (% successful finder pattern detection)
5. **Compare performance** across color modes

## References

- ISO/IEC 23634:2022 Section 6.2 (Pre-processing and color classification)
- ISO/IEC 23634:2022 Section 6.3 (Locating finder patterns)  
- ISO/IEC 23634:2022 Annex G (Guidelines for module colour selection)
- Test results: 4-color ✅, 8-color ✅, 16-color ❌, 32+ color ❌

---

**Next Steps:**
1. Implement luminance-based finder pattern detection
2. Test with 16/32/64/128-color modes
3. Validate against printed JABCodes (not just screen display)
