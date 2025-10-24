# Research Plan: JABCode High-Color Roundtrip Reliability

## Executive Summary

**Objective**: Enable reliable encode→decode roundtrips for ≥16 color modes (HEXADECIMAL, MODE_32, MODE_64, MODE_128, MODE_256) without test-only workarounds.

**Current Status**: 
- ✅ Encoding works perfectly (native palette generation functional)
- ✅ 4 and 8 color roundtrips stable (QUATERNARY, OCTAL)
- ❌ ≥16 color decoding unreliable without forced parameters

**Blockers**:
1. **Nc detection**: Cannot reliably identify color count from barcode pattern
2. **Classifier**: Cannot accurately map scanned pixels → palette colors
3. **Error correction**: Requires forced ECL/mask parameters for decode success

**Timeline Estimate**: 6-12 weeks (depending on findings)

---

## Phase 1: Diagnostic Baseline & Root Cause Analysis

**Duration**: 1-2 weeks  
**Goal**: Understand exactly where and why decoding fails

### Task 1.1: Establish Diagnostic Infrastructure
**Owners**: Research team  
**Effort**: 2-3 days

**Actions**:
- [x] Inventory existing debug APIs (already done - see `HighColorRoundtripTest.java`)
- [ ] Create systematic test harness for batch diagnostics
- [ ] Add logging framework to capture decoder state at each stage
- [ ] Implement golden test set (10-20 barcodes per color mode)

**Diagnostic APIs Available**:
```java
// Detector stage
debugDetectorStatsPtr(bitmap, mode) → [status, Nc, side, msz, AP coords...]
debugDecodeExInfoPtr(bitmap, mode) → [status, Nc, ECL, dimensions...]

// Classifier stage  
getClassifierStats(size) → [total, black, nonblack, margin, colors, mode, histogram...]
setClassifierMode(0|1) → 0=palette-based, 1=raw RGB

// Pipeline stage
getDecodePipelineDebug(12) → [p1, p2, bpm, mask, wc, wr, Pg, Pn, ...]
getPart2Debug(68) → [count, wc, wr, mask, bits...]

// LDPC/ECC stage
getLdpcInputDebug(0|1, size) → pre/post correction bits
getRawModuleSample(count) → raw module color values

// Palette comparison
getDecoderPaletteDebug(len) → actual palette used
getEncoderDefaultPalette(colorCount, len) → expected palette
```

**Deliverables**:
- Automated diagnostic report generator
- CSV output: mode, payload_size, nc_detected, nc_expected, ecl_detected, ecl_expected, decode_status, failure_stage
- Visual diff tool: encoder palette vs decoder palette

### Task 1.2: Failure Mode Classification
**Owners**: Research team  
**Effort**: 3-5 days

**Actions**:
- [ ] Run golden test set through decoder without workarounds
- [ ] Classify failure modes:
  - Type A: Nc detection wrong (detector stage)
  - Type B: Nc correct, palette mapping wrong (classifier stage)
  - Type C: Nc and palette correct, ECC fails (LDPC stage)
  - Type D: Metadata decode fails (Part II)
  - Type E: Mask pattern detection wrong
- [ ] Quantify failure distribution per color mode
- [ ] Identify "easy" vs "hard" payloads

**Success Criteria**:
- Documented failure taxonomy
- Percentage breakdown by failure type per color mode
- Identified correlation patterns (payload size, pattern complexity, etc.)

### Task 1.3: Current Workaround Analysis
**Owners**: Research team  
**Effort**: 2-3 days

**Actions**:
- [ ] Document exactly what each workaround fixes:
  - `setForceNc(expectedNc)` → bypasses detector.c Nc detection
  - `setForceEcl(wc, wr)` → bypasses Part II ECL parsing
  - `setNcThresholds(70, 0.05)` → loosens black pixel and std dev thresholds
  - `setUseDefaultPaletteHighColor(1)` → uses encoder palette instead of detected
  - Mask sweeping → brute-forces correct demask pattern
- [ ] Measure impact of removing each workaround individually
- [ ] Identify minimal workaround set for 80% success rate

**Success Criteria**:
- Decision tree: which workaround fixes which failure type
- Pareto chart: impact of each workaround on success rate

---

## Phase 2: Nc Detection Tuning

**Duration**: 2-3 weeks  
**Goal**: Reliably detect Nc ∈ {3, 4, 5, 6, 7} (16/32/64/128/256 colors) without `setForceNc()`

### Background: Nc Detection Algorithm

From JABCode spec and `detector.c`:
- **Nc** (color number code) is encoded in alignment patterns
- Nc = log₂(color_count) - 1
  - Nc=3 → 16 colors
  - Nc=4 → 32 colors
  - Nc=5 → 64 colors
  - Nc=6 → 128 colors
  - Nc=7 → 256 colors
- Detection uses:
  - Black pixel ratio threshold
  - Color variance (standard deviation)
  - Alignment pattern analysis

### Task 2.1: Characterize Nc Detection Failures
**Owners**: Research team  
**Effort**: 4-5 days

**Actions**:
- [ ] Collect 100+ failed Nc detections from diagnostic runs
- [ ] For each failure, log:
  - Expected Nc vs detected Nc
  - Black pixel ratio (from `debugDetectorStatsPtr`)
  - Color variance/std dev
  - Alignment pattern coordinates and pixel values
  - Image quality metrics (sharpness, noise, compression artifacts)
- [ ] Plot detection accuracy vs:
  - Image resolution
  - Compression level (PNG vs JPEG)
  - Module size
  - Background color
- [ ] Identify systematic biases (e.g., always detects Nc-1, Nc+1)

**Success Criteria**:
- Confusion matrix: expected Nc vs detected Nc
- Correlation analysis: which image properties affect detection
- Root cause hypothesis for top 3 failure patterns

### Task 2.2: Threshold Optimization
**Owners**: Research team  
**Effort**: 3-4 days

**Actions**:
- [ ] Current thresholds in `detector.c`:
  - Find hardcoded threshold values for black ratio
  - Find hardcoded threshold for std dev
  - Document current logic
- [ ] Grid search threshold space:
  - Black threshold: [50, 55, 60, 65, 70, 75, 80]
  - Std dev threshold: [0.02, 0.03, 0.04, 0.05, 0.06, 0.08, 0.10]
- [ ] Evaluate each combination on golden test set
- [ ] Consider adaptive thresholds based on:
  - Image histogram
  - Module size
  - Symbol dimensions

**Success Criteria**:
- Optimal threshold pair for each Nc value
- 90%+ Nc detection accuracy on golden test set
- No false positives (detecting higher Nc than actual)

### Task 2.3: Alignment Pattern Analysis Enhancement
**Owners**: Research team  
**Effort**: 5-7 days

**Actions**:
- [ ] Study upstream JABCode implementation for Nc encoding in APs
- [ ] Implement enhanced AP extraction:
  - Sub-pixel sampling for better precision
  - Noise filtering before color quantization
  - Multiple sampling points per AP module
- [ ] Cross-reference all 4 APs for consistency check
- [ ] Implement voting mechanism if APs disagree on Nc

**Code Investigation**:
```c
// In detector.c, locate:
// - scanFinderPattern() 
// - detectAlignmentPattern()
// - How Nc is extracted from AP pattern
// - Threshold constants used
```

**Success Criteria**:
- Enhanced AP reader with 95%+ accuracy on synthetic barcodes
- Documented Nc encoding scheme in APs
- Test suite validating AP extraction at various resolutions

---

## Phase 3: Classifier Tuning

**Duration**: 2-4 weeks  
**Goal**: Accurately map scanned pixel colors → palette indices without `setUseDefaultPaletteHighColor(1)`

### Background: Color Classification Challenge

- **Encoder**: Uses mathematically-spaced RGB grid palette
- **Decoder**: Must reconstruct palette from scanned image
- **Problem**: Image acquisition introduces:
  - Color shift (lighting, camera sensor)
  - Compression artifacts (JPEG)
  - Gamut mapping (display/print)
  - Noise and blur

### Task 3.1: Palette Reconstruction Analysis
**Owners**: Research team  
**Effort**: 5-6 days

**Actions**:
- [ ] Compare encoder palette vs decoder palette for 100 test cases
- [ ] Measure color distance (ΔE, Euclidean RGB distance)
- [ ] Identify systematic shifts:
  - Brightness offset
  - Saturation drift
  - Hue rotation
  - Gamma correction mismatch
- [ ] Visualize palettes in Lab color space for perceptual analysis
- [ ] Test on multiple image formats: PNG, JPEG (various quality), TIFF

**Diagnostic Workflow**:
```java
int colorCount = mode.getColorCount();
int[] encPal = getEncoderDefaultPalette(colorCount, colorCount * 3);
int[] decPal = getDecoderPaletteDebug(colorCount * 3);
// Compute color distance for each palette entry
// Identify worst-case color pairs (too close after shift)
```

**Success Criteria**:
- Documented color shift patterns per image format
- Histogram of ΔE values: encoder vs decoder palette
- Identified "problem colors" with high confusion rate

### Task 3.2: Classifier Mode Evaluation
**Owners**: Research team  
**Effort**: 3-4 days

**Actions**:
- [ ] Compare two classifier modes:
  - Mode 0: Palette-based (current default)
  - Mode 1: Raw RGB k-NN or clustering
- [ ] Evaluate both modes on:
  - Classification accuracy (% correct module assignments)
  - Robustness to image degradation
  - Runtime performance
- [ ] Analyze classifier statistics:
  - `avg_margin_micro` → how close colors are to decision boundaries
  - Histogram → color usage distribution
- [ ] Test hybrid approach: use mode 1 for palette reconstruction, mode 0 for data decode

**Success Criteria**:
- Decision matrix: when to use each classifier mode
- 95%+ module classification accuracy on clean images
- 85%+ accuracy on JPEG Q=80 compressed images

### Task 3.3: Adaptive Palette Learning
**Owners**: Research team  
**Effort**: 7-10 days

**Actions**:
- [ ] Implement palette learning from finder/alignment patterns:
  - These patterns use known colors (per JABCode spec)
  - Sample FP and AP pixel values
  - Build color mapping: scanned RGB → expected palette index
- [ ] K-means clustering on module samples to derive palette
- [ ] Color space conversion (RGB → Lab) for perceptual clustering
- [ ] Outlier rejection for noisy pixels
- [ ] Validate learned palette against encoder default
- [ ] Implement confidence scoring for learned palette

**Algorithm Sketch**:
```python
# Pseudocode
def learn_palette(image, finder_patterns, alignment_patterns, Nc):
    # Sample known-color regions
    fp_samples = sample_pixels(finder_patterns)  # Black, Cyan, Magenta, Yellow
    ap_samples = sample_pixels(alignment_patterns)  # Known pattern
    
    # Build color transform
    color_map = fit_color_transform(expected_colors, scanned_colors)
    
    # Apply to expected palette
    encoder_palette = get_default_palette(Nc)
    learned_palette = apply_transform(encoder_palette, color_map)
    
    # Refine with K-means on data modules
    data_samples = sample_all_modules(image, excluding=[FP, AP])
    refined_palette = kmeans(data_samples, k=2^(Nc+1), init=learned_palette)
    
    return refined_palette, confidence_score
```

**Success Criteria**:
- Learned palette matches encoder palette within ΔE < 10 for 90% of colors
- Decode success rate improves by 30%+ without forced palette
- Works across PNG and JPEG (Q≥80)

---

## Phase 4: Error Correction Optimization

**Duration**: 2-3 weeks  
**Goal**: Handle high-color symbols without `setForceEcl()` or mask sweeping

### Background: ECC Parameters

- **ECL (Error Correction Level)**: Encoded in Part II metadata
- **wc**: Error correction capability (columns)
- **wr**: Error correction capability (rows)  
- **Mask pattern**: 0-7, affects data encoding
- **Problem**: Part II decode often fails for ≥16 colors, preventing ECL extraction

### Task 4.1: Part II Metadata Robustness
**Owners**: Research team  
**Effort**: 4-5 days

**Actions**:
- [ ] Analyze Part II decode failures:
  - Collect `getPart2Debug()` output for 100 failed cases
  - Identify bit error patterns in Part II bits
  - Measure success rate per Nc value
- [ ] Test Reed-Solomon vs LDPC for Part II
- [ ] Implement Part II decode with multiple attempts:
  - Try all 8 mask patterns
  - Majority voting if multiple succeed
- [ ] Add Part II validation checks:
  - Sanity check ECL values (wc, wr must be in valid ranges)
  - Cross-validate with symbol dimensions

**Code Investigation**:
```c
// In decoder.c, locate:
// - decodeMaster() → Part II decode
// - How ECL (wc, wr) is extracted
// - Validation logic for metadata
```

**Success Criteria**:
- Part II decode success rate >95% on clean images
- Documented valid ranges for wc/wr per symbol size
- Fallback strategy when Part II decode fails

### Task 4.2: LDPC Parameter Tuning
**Owners**: Research team  
**Effort**: 5-7 days

**Actions**:
- [ ] Study LDPC decoder in `ldpc.c`:
  - Understand current parameterization
  - Identify assumptions for ≤8 colors that break for ≥16
- [ ] Analyze LDPC failure modes:
  - Collect `getLdpcInputDebug()` pre/post correction bits
  - Measure bit error rate before/after LDPC
  - Identify uncorrectable error patterns
- [ ] Test LDPC decoder with:
  - Different iteration counts
  - Alternative belief propagation algorithms
  - Soft-decision vs hard-decision decoding
- [ ] Consider interleaving impact:
  - High-color modes may need different interleaving patterns

**Success Criteria**:
- LDPC corrects 90%+ of correctable errors for ≥16 colors
- Documented LDPC parameter recommendations per Nc
- Test suite validating LDPC at various noise levels

### Task 4.3: Mask Pattern Detection
**Owners**: Research team  
**Effort**: 3-4 days

**Actions**:
- [ ] Understand mask pattern encoding (see JABCode spec)
- [ ] Current workaround sweeps all 8 masks → expensive
- [ ] Implement mask detection from Part II or symbol features
- [ ] Test heuristic approaches:
  - Choose mask with best data uniformity score
  - Detect from pattern in data modules
  - Use majority voting across multiple decode attempts
- [ ] Validate detection accuracy on golden test set

**Success Criteria**:
- Mask detection accuracy >90% without brute-force sweep
- Decode speedup by avoiding 7 extra attempts
- Fallback to sweep if confidence is low

---

## Phase 5: Integration & Validation

**Duration**: 1-2 weeks  
**Goal**: Remove all workarounds and achieve production-ready roundtrips

### Task 5.1: Workaround Removal
**Owners**: Research team  
**Effort**: 3-4 days

**Actions**:
- [ ] Disable test-only debug controls in production code paths
- [ ] Remove from `HighColorRoundtripTest.java`:
  - `setForceNc(expectedNc)` → use tuned detector
  - `setForceEcl(wc, wr)` → use robust Part II decode
  - `setNcThresholds(...)` → use optimized thresholds as defaults
  - `setUseDefaultPaletteHighColor(1)` → use learned palette
  - Mask sweeping → use mask detection
- [ ] Run full test suite without workarounds
- [ ] Measure regression: ensure 4/8 color modes still pass

**Success Criteria**:
- All workarounds removed from test code
- Test suite passes with natural decoder behavior
- No performance degradation for ≤8 color modes

### Task 5.2: Real-World Validation
**Owners**: QA team  
**Effort**: 5-7 days

**Actions**:
- [ ] Generate diverse test set:
  - Payloads: 10B, 50B, 100B, 500B, 1KB
  - Color modes: 16, 32, 64, 128, 256
  - ECC levels: 1, 2, 3, 4, 5
  - Image formats: PNG, JPEG Q=[70, 80, 90, 100]
  - Resolutions: 300dpi, 150dpi, 72dpi
- [ ] Test decode with real scanner/camera:
  - Print barcodes on paper
  - Scan with flatbed scanner
  - Photograph with smartphone camera
  - Test in various lighting conditions
- [ ] Measure success rates:
  - Digital roundtrip (encode → save → decode): target >99%
  - Print-scan roundtrip: target >90%
  - Camera capture: target >80%

**Success Criteria**:
- Digital roundtrip: >99% success for all modes
- Print-scan: >90% for 16/32/64, >80% for 128/256
- Camera: >80% for 16/32/64, >70% for 128/256
- Zero false positives (wrong decode better than failure)

### Task 5.3: Performance Benchmarking
**Owners**: Performance team  
**Effort**: 2-3 days

**Actions**:
- [ ] Measure decode latency per color mode
- [ ] Profile CPU/memory usage
- [ ] Identify performance bottlenecks (classifier, LDPC, etc.)
- [ ] Optimize hot paths if needed
- [ ] Compare vs ≤8 color baseline

**Success Criteria**:
- ≥16 color decode within 2x latency of 8-color
- Memory usage within acceptable bounds (<100MB per decode)
- No memory leaks in long-running tests

### Task 5.4: Documentation & Release
**Owners**: Tech writing + maintainers  
**Effort**: 3-4 days

**Actions**:
- [ ] Update `ColorModeConverter.isHighColorNativeEnabled()`:
  - Change default to `true`
  - Remove "topology tuning" comment
- [ ] Update README with supported color modes:
  - 4/8 colors: production-ready roundtrip
  - 16/32/64 colors: production-ready roundtrip (new!)
  - 128/256 colors: production-ready roundtrip (new!)
- [ ] Document known limitations:
  - Camera capture requires good lighting
  - JPEG compression should be Q≥80
  - Recommended DPI for print: ≥300
- [ ] Create migration guide for users of test-only APIs
- [ ] Bump version to 1.1.0 (minor version for new capability)

**Success Criteria**:
- Feature flag enabled by default
- Comprehensive documentation published
- Release notes highlighting high-color roundtrip support

---

## Success Metrics

### Phase-Level KPIs

| Phase | Key Metric | Target |
|-------|------------|--------|
| Phase 1 | Failure taxonomy completeness | 100% of failures classified |
| Phase 2 | Nc detection accuracy | ≥95% on golden test set |
| Phase 3 | Palette learning ΔE | <10 for 90% of colors |
| Phase 4 | Part II decode success | ≥95% on clean images |
| Phase 5 | Digital roundtrip success | ≥99% for all ≥16 modes |

### Overall Project Goals

- [ ] **Roundtrip Reliability**: ≥99% digital roundtrip for 16/32/64/128/256 colors
- [ ] **No Workarounds**: All test-only debug controls removed from production paths
- [ ] **Performance**: ≥16 color decode within 2x latency of 8-color baseline
- [ ] **Backward Compatibility**: ≤8 color modes maintain 100% success rate
- [ ] **Real-World Readiness**: ≥80% success for camera-captured images (good lighting)

---

## Risk Management

### High-Risk Items

1. **Palette learning may fail for extreme lighting**
   - **Mitigation**: Implement multiple color space transforms, fallback to encoder palette
   - **Contingency**: Document lighting requirements, provide image preprocessing tools

2. **Nc detection may be inherently ambiguous for noisy images**
   - **Mitigation**: Implement confidence scoring, reject low-confidence decodes early
   - **Contingency**: Allow manual Nc hint parameter for applications that know expected mode

3. **LDPC may need architectural changes for ≥16 colors**
   - **Mitigation**: Early investigation in Phase 4.2, engage upstream JABCode maintainers if needed
   - **Contingency**: Implement alternative ECC (BCH, Turbo codes) as fallback

### Medium-Risk Items

4. **Classifier tuning may overfit to test set**
   - **Mitigation**: Use cross-validation, test on held-out real-world images
   - **Contingency**: Implement ensemble classifier with multiple strategies

5. **Real-world print-scan may underperform digital**
   - **Mitigation**: Test with multiple printers/scanners early
   - **Contingency**: Document print quality requirements, provide QC tools

### Low-Risk Items

6. **Performance regression for ≤8 colors**
   - **Mitigation**: Continuous benchmarking, fast-path for low-color modes
   - **Contingency**: Maintain separate code paths if needed

---

## Resource Requirements

### Team Composition
- **Research Lead** (1 FTE): Phase planning, architecture decisions
- **Decoder Engineer** (1 FTE): C code in detector.c, decoder.c, ldpc.c
- **Classifier Engineer** (0.5 FTE): Color science, palette learning
- **Test Engineer** (0.5 FTE): Test harness, golden test set, validation
- **QA Engineer** (0.5 FTE for Phase 5): Real-world testing
- **Technical Writer** (0.25 FTE for Phase 5): Documentation

### Infrastructure
- **Compute**: GPU for potential ML-based classifier (optional)
- **Test Lab**: Printers, scanners, cameras for real-world validation
- **CI/CD**: Automated testing pipeline for regression detection

### Dependencies
- **Upstream JABCode**: May need to engage for LDPC/ECC changes
- **Color Science Library**: ΔE calculation, Lab color space conversion (e.g., `colormath` or similar)
- **Image Processing**: OpenCV or Java AWT for preprocessing (already available)

---

## Timeline Summary

| Phase | Duration | Dependencies |
|-------|----------|--------------|
| Phase 1: Diagnostics | 1-2 weeks | None |
| Phase 2: Nc Detection | 2-3 weeks | Phase 1 complete |
| Phase 3: Classifier | 2-4 weeks | Phase 1 complete (can overlap with Phase 2) |
| Phase 4: ECC | 2-3 weeks | Phase 2, 3 partially complete |
| Phase 5: Integration | 1-2 weeks | Phase 2, 3, 4 complete |
| **Total** | **8-14 weeks** | Assumes parallel work on Phases 2-4 |

**Critical Path**: Phase 1 → Phase 2 → Phase 4 → Phase 5 (8-10 weeks minimum)

---

## Next Steps

1. **Immediate** (Week 1):
   - Assign research team
   - Set up diagnostic infrastructure (Task 1.1)
   - Begin failure mode classification (Task 1.2)

2. **Short-term** (Weeks 2-4):
   - Complete Phase 1 diagnostics
   - Start parallel work on Phase 2 (Nc) and Phase 3 (Classifier)
   - Weekly progress reviews

3. **Mid-term** (Weeks 5-10):
   - Complete Phases 2-4
   - Begin integration testing
   - Engage upstream if LDPC changes needed

4. **Long-term** (Weeks 11-14):
   - Real-world validation
   - Performance optimization
   - Documentation and release

---

## Appendix: Quick Wins

If full research plan is too ambitious, consider these incremental improvements:

### Quick Win 1: Optimized Nc Thresholds (1 week)
- Grid search existing thresholds
- Update defaults in `detector.c`
- Expected impact: +20-30% Nc detection accuracy

### Quick Win 2: Palette Normalization (1 week)
- Implement brightness/contrast normalization before classification
- Expected impact: +15-25% classifier accuracy

### Quick Win 3: Robust Part II (1 week)
- Try all 8 masks for Part II decode
- Majority voting
- Expected impact: +10-20% overall success rate

**Combined Quick Wins**: Could achieve ~60-70% success rate in 3 weeks vs ~20-30% current baseline (without workarounds)
