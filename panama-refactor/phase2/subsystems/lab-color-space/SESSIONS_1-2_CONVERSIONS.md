# LAB Color Space: Implementation Sessions 1-2

## Session 1: RGB→LAB Conversion (4 hours)

### Objectives
- Implement rgb_to_lab() function
- Implement lab_to_rgb() function
- Unit test conversion accuracy

### Tasks
1. Create lab_color.h with structure definitions
2. Implement linearize_rgb() helper
3. Implement RGB→XYZ transformation
4. Implement XYZ→LAB transformation
5. Implement reverse LAB→RGB
6. Write unit tests for known colors
7. Validate round-trip accuracy

### Deliverables
- `src/jabcode/lab_color.c` with conversions
- `src/jabcode/lab_color.h` with API
- Unit tests passing for 100+ reference colors

## Session 2: ΔE Calculation (4 hours)

### Objectives
- Implement ΔE76 calculator
- Implement ΔE2000 calculator (optional)
- Performance optimization

### Tasks
1. Implement delta_e_76() function
2. Write unit tests for ΔE properties
3. Validate against reference implementation
4. Implement lookup table optimization
5. Performance benchmarking
6. Document API usage

### Deliverables
- ΔE calculators functional and tested
- Performance < 5% overhead target
- Documentation complete
