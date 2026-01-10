# Iterative Decoding: Performance Analysis

## Performance Targets

- Per-iteration overhead: < 50% of initial decode
- Typical iterations: 2-3
- Max iterations: 5-10 (configurable)
- Total decode time: < 2× single-pass

## Optimization Strategies

### Early Termination
Stop immediately when LDPC successful.

### Confidence Caching
Reuse confidence scores across iterations.

### Sparse Refinement
Only re-decode low-confidence modules, not entire barcode.

### Parallel Processing (Future)
Refine independent modules in parallel.

## Benchmarking

Measure decode time vs pass rate trade-off.
Find optimal max_iterations parameter.
