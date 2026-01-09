# JABCode Implementation: Technical Glossary

**Version:** 1.0.0  
**Last Updated:** 2026-01-09

---

## Core Concepts

### JABCode
**JABCode** (Just Another Bar Code) - ISO/IEC 23634 standard for color 2D barcodes supporting 4-256 colors per module.

### Color Mode (Nc)
**Color Mode** - Determines number of colors used in barcode. Nc = 0-7 corresponds to 4, 8, 16, 32, 64, 128, 256 colors.

### Module
**Module** - Single color cell in barcode grid. Smallest addressable unit.

### Symbol
**Symbol** - Complete JABCode barcode, may contain multiple modules arranged in grid.

### Version
**Version** - Determines barcode size. Version 1 = 21×21 modules, each increment adds 4 modules per side.

---

## Encoding/Decoding Terms

### Palette
**Palette** - Set of RGB colors used for encoding. Fixed for modes 0-5, interpolated for modes 6-7.

### Master Palette
**Master Palette** - Primary barcode's color palette in multi-symbol codes.

### Slave Palette
**Slave Palette** - Secondary barcode's palette, references master palette.

### Threshold
**Threshold** - RGB value midpoint used to distinguish black from dark colors during decoding.

### LDPC (Low-Density Parity-Check)
**LDPC** - Error correction algorithm used in JABCode. Can correct ~30% bit error rate maximum.

### ECC Level
**ECC Level** - Error Correction Code level (0-10). Higher = more redundancy = larger barcode.

---

## Color Science

### RGB
**RGB** - Red-Green-Blue color space. Device-dependent, not perceptually uniform.

### CIE LAB
**CIE LAB** - Perceptually uniform color space. L = lightness, a = green-red, b = blue-yellow.

### CIE XYZ
**CIE XYZ** - Intermediate color space for RGB → LAB conversion.

### Delta-E (ΔE)
**Delta-E** - Perceptual color distance metric. ΔE < 1 = imperceptible, ΔE > 10 = very noticeable.

### CIEDE2000
**CIEDE2000** - Most accurate Delta-E formula accounting for perceptual non-uniformities.

### Gamma Correction
**Gamma Correction** - Non-linear transformation compensating for display/camera response curves.

### Color Temperature
**Color Temperature** - Light source characteristic measured in Kelvin (K). 5500K = daylight.

---

## Barcode Structure

### Finder Pattern
**Finder Pattern** - Large square markers in corners used for barcode detection and orientation.

### Alignment Pattern
**Alignment Pattern** - Internal control points in larger barcodes (version ≥ 6) for geometric correction.

### Metadata
**Metadata** - Barcode header containing color mode, version, ECC level, and data length.

### Payload
**Payload** - Actual data encoded in barcode (after metadata and ECC).

### Parity Bits
**Parity Bits** - LDPC error correction bits, critical for decode success.

---

## Phase 1 Terms

### Option B
**Force Larger Barcodes** - Ensure minimum barcode version ≥ 6 to include alignment patterns.

### Option C
**Median Filtering** - Image enhancement removing salt-and-pepper noise before decoding.

### Salt-and-Pepper Noise
**Salt-and-Pepper Noise** - Random bright/dark pixels, common in digital images.

---

## Phase 2 Terms

### Adaptive Palette
**Adaptive Palette** - Environment-optimized color set (vs. fixed palette).

### Environment Profile
**Environment Profile** - Lighting and display characteristics (ambient light, gamma, color temp).

### Error Profile
**Error Profile** - Confusion matrix tracking which colors get misidentified.

### Confusion Matrix
**Confusion Matrix** - NxN matrix where entry [i,j] = probability color i decoded as color j.

### Error-Aware Encoding
**Error-Aware Encoding** - Color assignment strategy avoiding error-prone patterns.

### Critical Modules
**Critical Modules** - Barcode positions affecting LDPC parity bits, prioritized for accuracy.

### Hybrid Mode
**Hybrid Mode** - Using different color modes for different barcode regions.

### Region Map
**Region Map** - Spatial partitioning defining which barcode areas use which color modes.

### Iterative Decoding
**Iterative Decoding** - Multi-pass decode with feedback from partial LDPC results.

### Confidence Map
**Confidence Map** - Per-module decode certainty tracking (0.0-1.0).

### Convergence
**Convergence** - Iterative decode reaching stable solution (no further improvement).

---

## Testing Terms

### TDD (Test-Driven Development)
**TDD** - Write failing test → implement → refactor cycle.

### Integration Test
**Integration Test** - Test multiple components working together.

### Unit Test
**Unit Test** - Test single function or class in isolation.

### Code Coverage
**Code Coverage** - Percentage of code lines executed during tests. Target: 95%+.

### JaCoCo
**JaCoCo** - Java Code Coverage tool generating HTML/XML reports.

### Round-Trip Test
**Round-Trip Test** - Encode data → decode → verify matches original.

---

## Panama FFM Terms

### Panama
**Panama Foreign Function & Memory API** - JDK 21+ feature for Java ↔ native code interop.

### FFM
**Foreign Function & Memory** - Panama's mechanism replacing JNI.

### Arena
**Arena** - Memory management scope in Panama FFM, auto-cleanup on close.

### Memory Segment
**Memory Segment** - Panama's abstraction for native memory buffers.

### Downcall
**Downcall** - Java → native C function call via Panama.

### Upcall
**Upcall** - Native C → Java callback via Panama.

---

## Build & Deployment

### libjabcode.so
**libjabcode.so** - Linux shared library containing native JABCode implementation.

### Thin JAR
**Thin JAR** - Java JAR without embedded native libraries (50KB).

### Fat JAR
**Fat JAR** - Java JAR with embedded native libraries for all platforms (~3MB).

### JNI (Java Native Interface)
**JNI** - Legacy mechanism for Java-native interop (pre-Panama).

### NDK (Native Development Kit)
**NDK** - Android toolkit for compiling C/C++ code for ARM/ARM64.

---

## Performance Metrics

### Pass Rate
**Pass Rate** - Percentage of tests successfully decoding (current: 27%, target: 75-85%).

### Decode Time
**Decode Time** - Milliseconds to decode single barcode. Current: ~100ms, target: <500ms.

### Binary Size
**Binary Size** - Native library file size. Current: 500KB, Phase 2: ~800KB.

### Memory Footprint
**Memory Footprint** - RAM usage during encode/decode. Current: ~10MB, Phase 2: ~15MB.

---

## Error Messages

### "LDPC decoding failed"
**LDPC Decoding Failed** - Error rate exceeded LDPC correction capacity (~30% max).

### "No alignment pattern available"
**No Alignment Pattern Available** - Barcode too small (< version 6) lacks internal control points.

### "Invalid color mode"
**Invalid Color Mode** - Nc parameter out of range (must be 0-7).

---

## Mathematical Terms

### Euclidean Distance
**Euclidean Distance** - √(Δr² + Δg² + Δb²). Simple but not perceptually accurate for color.

### Perceptual Uniformity
**Perceptual Uniformity** - Equal numeric differences = equal perceived differences.

### Color Spacing
**Color Spacing** - RGB distance between adjacent palette colors. 256-color mode: 36 units.

### Noise-to-Signal Ratio
**Noise-to-Signal Ratio** - Noise amplitude / color spacing. >25% problematic for decoding.

### LDPC Capacity
**LDPC Capacity** - Maximum correctable error rate, ~30% for JABCode implementation.

---

## Development Workflow

### Session
**Session** - Single focused work period implementing specific deliverables (2-3 hours).

### Milestone
**Milestone** - Major project checkpoint (e.g., Phase 1 complete).

### Quality Gate
**Quality Gate** - Required criteria before proceeding to next phase.

### Feature Flag
**Feature Flag** - Runtime toggle enabling/disabling subsystems for testing.

### Regression Test
**Regression Test** - Ensure new changes don't break existing functionality.

---

## Abbreviations

- **API** - Application Programming Interface
- **CLI** - Command-Line Interface
- **CSV** - Comma-Separated Values
- **DPI** - Dots Per Inch
- **GUI** - Graphical User Interface
- **I/O** - Input/Output
- **ISO** - International Organization for Standardization
- **JAR** - Java Archive
- **JVM** - Java Virtual Machine
- **PNG** - Portable Network Graphics
- **POC** - Proof of Concept
- **RGB** - Red-Green-Blue
- **ROI** - Return on Investment
- **SDK** - Software Development Kit
- **TDD** - Test-Driven Development
- **UI** - User Interface
- **UX** - User Experience
- **V1/V2** - Version 1/Version 2

---

## Platform-Specific

### Linux x64
**Linux x64** - 64-bit Linux on x86-64 architecture. Primary development platform.

### ARM64
**ARM64** - 64-bit ARM architecture, used in mobile devices and newer Macs.

### ELF
**ELF** - Executable and Linkable Format, Linux/Unix binary format.

### LSO
**LSO** - Linux Shared Object (shared library), file extension `.so`.

### Mach-O
**Mach-O** - macOS/iOS binary format for executables and libraries.

### PE32+
**PE32+** - Windows 64-bit executable format.

---

## Reference Standards

### ISO/IEC 23634
**ISO/IEC 23634** - JABCode international standard specification.

### sRGB
**sRGB** - Standard RGB color space with gamma ~2.2.

### D65 Illuminant
**D65 Illuminant** - Standard daylight illuminant (6500K) used in color conversions.

---

**Note:** This glossary focuses on terms specific to this implementation. For general Java/C programming terms, refer to language-specific documentation.

---

**Document Status:** ✅ Complete  
**Maintained By:** AI-Driven Development
