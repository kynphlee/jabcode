# JABCode Performance Benchmarking - Strategic Overview

## Executive Summary

The JABCode Panama FFM implementation is **functionally complete** (85-90% of spec checklist) but lacks **quantified performance characteristics**. This plan establishes formal benchmarks to measure, track, and optimize performance across all color modes and encoding scenarios.

**Current State:**
- ✅ 205 functional tests passing
- ✅ 7 color modes working (4-128 colors)
- ✅ Cascaded encoding support
- ❌ No performance baselines
- ❌ No regression detection
- ❌ No optimization guidance

**Target State:**
- ✅ Quantified performance per color mode
- ✅ Automated regression detection in CI
- ✅ Clear optimization targets identified
- ✅ User guidance on mode trade-offs

---

## Why Benchmarks Matter Now

### 1. Multiple Code Paths = Hidden Regressions
With 7 color modes + cascading + ECC variations, you have **hundreds of execution paths**. A fix in one area can silently break performance elsewhere.

**Example Scenario:**
```
Developer optimizes Mode 6 interpolation
  → Accidentally slows Mode 3 palette lookup
  → No tests fail (functional correctness intact)
  → Users report "new version feels slower"
  → No data to diagnose which mode regressed
```

**With benchmarks:** CI catches the regression before merge.

### 2. Panama FFM is New Territory
Project Panama is a **preview feature** (JEP 454). Performance characteristics vs JNI are unknown:

**Questions We Can't Answer:**
- Is FFM overhead acceptable for high-throughput use cases?
- Should we batch native calls differently?
- Are memory copies a bottleneck?
- Does SegmentAllocator choice matter?

**Benchmarks reveal:** Concrete FFM overhead measurements guide architectural decisions.

### 3. Optimization Requires Data
**Current approach:** Guess where bottlenecks are, optimize, hope it helps.

**Data-driven approach:**
```
Benchmark reveals:
  Mode 5 encoding: 45ms total
    - Palette lookup: 8ms (18%)
    - Bit packing: 31ms (69%)  ← OPTIMIZE THIS
    - Native calls: 6ms (13%)
```

Now you know **exactly** where to focus effort.

### 4. User Guidance Gap
Users ask: "Should I use 32-color or 64-color?"

**Without benchmarks:** Vague advice ("try both, see what works").

**With benchmarks:**
```
Message: 5KB, ECC=5

Mode 4 (32-color):
  Encoding: 38ms
  Symbol: 92×92 (8,464 modules)
  
Mode 5 (64-color):
  Encoding: 42ms (+11%)
  Symbol: 78×78 (6,084 modules, -28%)

Trade-off: 11% slower, 28% smaller
```

Users make **informed decisions** with data.

---

## Strategic Goals

### Primary Goals

**G1: Establish Performance Baselines**
- Measure encoding time for all color modes (1-6)
- Measure decoding time for all color modes
- Document baseline metrics as reference

**G2: Enable Regression Detection**
- Integrate benchmarks into CI pipeline
- Set acceptable performance variance thresholds
- Alert on significant regressions (>10% slowdown)

**G3: Identify Optimization Targets**
- Profile hot paths (palette, bit packing, FFM calls)
- Measure memory allocation patterns
- Quantify FFM overhead vs theoretical minimum

**G4: Guide User Decisions**
- Document performance trade-offs per color mode
- Measure cascade overhead (1 vs 2 vs 5 symbols)
- Quantify ECC level impact

### Secondary Goals

**G5: Competitive Analysis**
- Compare Panama FFM vs JNI (if feasible)
- Benchmark against reference implementations
- Establish performance positioning

**G6: Platform Profiling**
- Measure on x86_64, ARM64 (if available)
- Quantify embedded/mobile viability
- Document platform-specific tuning

---

## Success Criteria

### Quantitative Metrics

**Coverage:**
- [ ] All 6 operational color modes benchmarked
- [ ] Both encoding and decoding measured
- [ ] At least 4 message sizes tested (100B, 1KB, 10KB, 100KB)
- [ ] At least 3 ECC levels tested (3, 5, 7)

**Quality:**
- [ ] Coefficient of Variation (CV) < 5% for stable benchmarks
- [ ] At least 5 warmup iterations per benchmark
- [ ] At least 10 measurement iterations per benchmark
- [ ] At least 3 forks per benchmark (JVM isolation)

**Integration:**
- [ ] CI pipeline runs benchmarks on PRs
- [ ] Results stored and tracked over time
- [ ] Regression alerts configured (<10% threshold)
- [ ] HTML reports generated automatically

### Qualitative Outcomes

**Decision Support:**
- [ ] Clear recommendation: which mode for which use case
- [ ] Documented trade-offs: speed vs density vs reliability
- [ ] Guidance on cascade usage (when worth the overhead)

**Optimization Readiness:**
- [ ] Top 3 bottlenecks identified
- [ ] Potential improvement areas quantified
- [ ] FFM vs JNI comparison (if feasible)

**Knowledge Capture:**
- [ ] Baseline metrics documented in memory-bank
- [ ] Performance characteristics added to user docs
- [ ] Lessons learned from benchmarking captured

---

## Scope and Boundaries

### In Scope

**Encoding:**
- All color modes 1-6 (skip 7 due to malloc crash)
- Message sizes: 100B, 1KB, 10KB, 100KB
- ECC levels: 3, 5, 7, 9
- Symbol counts: 1, 2, 3, 5 (cascade)

**Decoding:**
- All color modes 1-6
- Round-trip time measurement
- Error recovery performance (future)

**System:**
- Memory usage (heap + native)
- FFM overhead (call latency, copy costs)
- Throughput (messages/second)

### Out of Scope

**Not Included:**
- ❌ Network I/O (pure computation only)
- ❌ Distributed benchmarking (single-machine)
- ❌ Production monitoring (CI/dev only)
- ❌ External system integration
- ❌ GPU acceleration
- ❌ Actual optimization work (measurement only)

**Deferred:**
- ⏳ Mode 7 (256-color) until malloc crash fixed
- ⏳ Platform-specific tuning (focus x86_64 first)
- ⏳ Competitive benchmarks vs other barcode libs
- ⏳ Real-world workload simulation

---

## Key Metrics Defined

### 1. Encoding Time
**Definition:** Time from `encode(message, config)` call to PNG bytes returned.

**Measurement:** Nanoseconds per operation (ns/op)

**What's Included:**
- Palette generation (if not cached)
- Bit packing
- Native encoder call
- Memory marshalling (Java ↔ native)
- PNG generation

**What's Excluded:**
- File I/O (measure in-memory only)
- Test setup overhead
- JVM warmup time

### 2. Decoding Time
**Definition:** Time from PNG bytes to decoded message string.

**Measurement:** Nanoseconds per operation (ns/op)

**Components:**
- PNG parsing
- Native decoder call
- Palette extraction/reconstruction
- Bit unpacking
- Memory unmarshalling

### 3. Throughput
**Definition:** Operations (encode or decode) per second under sustained load.

**Measurement:** ops/second

**Purpose:** Assess suitability for high-volume use cases (e.g., real-time generation).

### 4. Memory Usage
**Definition:** Peak memory consumption during operation.

**Measurement:** Bytes allocated (heap + native)

**Components:**
- Java heap allocations
- Native memory (via Arena)
- Temporary buffers
- Cached palettes

### 5. FFM Overhead
**Definition:** Time spent in Panama FFM infrastructure vs actual work.

**Measurement:** Percentage of total time

**Breakdown:**
- MethodHandle invocation
- Memory segment allocation
- Data marshalling (Java ↔ native)
- SegmentAllocator overhead

---

## Performance Budget

Based on typical JABCode use cases, establish acceptable thresholds:

### Target Metrics (Mode 2, 8-color baseline)

| Operation | Message Size | Target | Acceptable | Poor |
|-----------|--------------|--------|------------|------|
| Encoding | 1KB | <50ms | <100ms | >200ms |
| Encoding | 10KB | <200ms | <500ms | >1s |
| Decoding | 1KB | <30ms | <75ms | >150ms |
| Throughput | 1KB | >20 msg/s | >10 msg/s | <5 msg/s |

### Color Mode Overhead (vs Mode 2)

| Mode | Colors | Expected Overhead | Reasoning |
|------|--------|-------------------|-----------|
| 1 | 4 | 0% | Simpler palette |
| 2 | 8 | 0% (baseline) | Default mode |
| 3 | 16 | +10-20% | More palette lookups |
| 4 | 32 | +20-30% | Increased bit packing |
| 5 | 64 | +30-40% | 6-bit packing complexity |
| 6 | 128 | +50-70% | Interpolation overhead |

### Cascade Overhead

| Symbols | Expected Overhead | Max Acceptable |
|---------|-------------------|----------------|
| 2 | +15-25% | +50% |
| 3 | +30-45% | +75% |
| 5 | +60-90% | +150% |

**Rationale:** Multiple symbols require repeated native calls, position initialization, and metadata coordination.

---

## Risk Assessment

### High Risks

**R1: JVM Warmup Instability**
- **Risk:** Cold JVM gives misleading results
- **Mitigation:** JMH handles warmup automatically (5-10 iterations)
- **Detection:** High CV (>10%) indicates warmup issues

**R2: Native Library State Pollution**
- **Risk:** Previous benchmark run affects next run
- **Mitigation:** Isolate via JMH forks (separate JVM per benchmark)
- **Detection:** Results vary significantly between forks

**R3: CI Resource Constraints**
- **Risk:** Shared CI runners give inconsistent results
- **Mitigation:** Use dedicated benchmark agents, cache baseline results
- **Detection:** High variance in CI vs local runs

### Medium Risks

**R4: Benchmark Maintenance Burden**
- **Risk:** Benchmarks become outdated as code evolves
- **Mitigation:** Co-locate with tests, integrate into CI
- **Detection:** Benchmark compilation failures, test coverage drops

**R5: Over-Optimization Trap**
- **Risk:** Focus on micro-optimizations with minimal real-world impact
- **Mitigation:** Benchmark realistic message sizes, prioritize by impact
- **Detection:** Optimizing 100B messages when users encode 10KB

**R6: Regression Alert Fatigue**
- **Risk:** Too many false-positive regression alerts
- **Mitigation:** Set reasonable thresholds (10-15%), require multiple CI runs
- **Detection:** Developers ignore alerts, bypass CI

---

## Implementation Philosophy

### 1. Incremental Value Delivery
Each phase delivers **standalone value**:
- Phase 1: Basic infrastructure → can run simple benchmarks
- Phase 2: Core metrics → know encoding performance
- Phase 3: Advanced metrics → full performance picture
- Phase 4: Automation → continuous monitoring

### 2. Test-Driven Benchmarking
Apply TDD principles to benchmark code itself:
- Benchmark utilities have unit tests
- Result parsers have integration tests
- CI pipeline is tested end-to-end
- Run `/test-coverage-update` after each phase

### 3. Pragmatic Precision
Balance accuracy with effort:
- **High precision:** Core encoding/decoding (tight CV, many iterations)
- **Medium precision:** Memory profiling (acceptable variance)
- **Low precision:** Exploratory metrics (quick measurement)

### 4. Documentation as Code
Benchmark results are **living documentation**:
- Stored in Git (JSON format)
- Tracked over time (trends)
- Visualized in reports (HTML/Markdown)
- Referenced in decisions (PRs, issues)

---

## Dependencies and Prerequisites

### Required Tools

**JDK 21+**
- Panama FFM API support
- Virtual threads (for future parallel benchmarks)
- Record patterns (for clean result objects)

**Maven 3.8+**
- JMH Maven Plugin
- Dependency management
- Profile support (benchmark vs test)

**JMH 1.37+**
- Industry-standard benchmarking framework
- Handles warmup, JIT, GC correctly
- Excellent result analysis

**JaCoCo**
- Already configured in project
- Benchmark code should be tested too
- Track coverage via `/test-coverage-update`

### Required State

**Codebase:**
- [ ] All 205 tests passing
- [ ] Native library built (`libjabcode.so`)
- [ ] Modes 1-6 functional
- [ ] Cascaded encoding working

**Environment:**
- [ ] `LD_LIBRARY_PATH` set correctly
- [ ] Sufficient disk space for results (1GB+)
- [ ] CI agent with benchmark label (Phase 4)

---

## Phased Approach Overview

### Phase 1: JMH Infrastructure Setup (4-6 hours)
**Goal:** Establish benchmarking capability

**Deliverables:**
- JMH dependencies added
- Benchmark module structure
- Maven profiles configured
- First "hello world" benchmark running
- Test coverage verified

**Exit Criteria:**
- Can run `mvn jmh:run` successfully
- Benchmark results output to console
- No impact on main module
- `/test-coverage-update` passes

### Phase 2: Core Encoding Benchmarks (6-8 hours)
**Goal:** Measure primary use case (encoding)

**Deliverables:**
- Encoding benchmarks for all modes 1-6
- Message size variations (100B, 1KB, 10KB, 100KB)
- ECC level variations (3, 5, 7, 9)
- Cascade variations (1, 2, 3, 5 symbols)
- Baseline results documented

**Exit Criteria:**
- All color modes benchmarked
- Results stable (CV < 5%)
- Baseline JSON committed
- `/test-coverage-update` passes

### Phase 3: Advanced Metrics (8-10 hours)
**Goal:** Complete performance picture

**Deliverables:**
- Decoding benchmarks
- Memory profiling (heap + native)
- FFM overhead analysis
- Throughput benchmarks
- Comparative analysis (mode vs mode)

**Exit Criteria:**
- Decode performance measured
- Memory usage quantified
- Bottlenecks identified
- `/test-coverage-update` passes

### Phase 4: CI Integration & Analysis (4-6 hours)
**Goal:** Continuous monitoring

**Deliverables:**
- GitHub Actions workflow
- Regression detection logic
- Performance report generation
- Historical result storage
- Alert thresholds configured

**Exit Criteria:**
- CI runs benchmarks on PRs
- Reports generated automatically
- Regressions detected and alerted
- `/test-coverage-update` passes

---

## Expected Outcomes

### Immediate (Post-Phase 2)
- **Know** exactly how fast each color mode is
- **Detect** if a change makes encoding slower
- **Compare** modes to guide user decisions

### Short-Term (Post-Phase 3)
- **Identify** top 3 optimization targets
- **Quantify** FFM overhead vs JNI (if measured)
- **Document** performance characteristics

### Long-Term (Post-Phase 4)
- **Prevent** performance regressions automatically
- **Track** performance trends over time
- **Guide** architectural decisions with data

---

## Metrics for Success

### Process Metrics
- [ ] All phases completed on schedule
- [ ] Test coverage maintained (>80%)
- [ ] No blocking issues encountered
- [ ] Documentation quality high

### Technical Metrics
- [ ] Benchmarks run in <5 minutes
- [ ] Results reproducible (CV < 5%)
- [ ] CI integration stable (>95% success rate)
- [ ] Regression detection accurate (no false positives)

### Business Metrics
- [ ] Performance SLAs defined
- [ ] User guidance improved
- [ ] Optimization ROI measurable
- [ ] Confidence in performance commitments

---

## Next Actions

**Ready to proceed?**

1. ✅ Review this overview
2. ✅ Confirm prerequisites met
3. ➡️ Begin [Phase 1: JMH Setup](02-phase1-jmh-setup.md)
4. ⏳ Execute phases sequentially
5. ⏳ Run `/test-coverage-update` after each phase
6. ⏳ Review and adjust plan as needed

---

**Status:** Planning complete, ready for implementation (2026-01-12)
