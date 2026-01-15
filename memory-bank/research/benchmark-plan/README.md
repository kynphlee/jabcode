# JABCode Performance Benchmark Plan

## Quick Start

This directory contains a comprehensive, phased implementation plan for establishing formal performance benchmarks for the JABCode Panama FFM wrapper.

### Why Benchmarks?

Currently, the JABCode implementation has:
- ✅ **205 tests** proving functional correctness
- ✅ **7 color modes** (4-128 colors) fully working
- ✅ **Cascaded encoding** support
- ❌ **No performance quantification**

Benchmarks will provide:
- 📊 **Quantified performance** across all color modes
- 🔍 **Regression detection** for future changes
- 📈 **Optimization guidance** (where to focus effort)
- 🎯 **User guidance** on mode selection trade-offs

---

## Documentation Structure

```
benchmark-plan/
├── 00-index.md              # Navigation hub
├── README.md                # This file (quick start)
├── 01-overview.md           # Strategic context & goals
├── 01a-phase0-native-profiling.md  # ✅ Native optimization (8-12h) COMPLETE
├── 02-phase1-jmh-setup.md   # JMH infrastructure (4-6h)
├── 03-phase2-core-benchmarks.md    # Core encoding (6-8h)
├── 04-phase3-advanced-metrics.md   # Advanced metrics (8-10h)
└── 05-phase4-ci-integration.md     # CI/CD integration (4-6h)
```

**Total Effort:** 30-42 hours (4-6 working days)  
**Completed:** 10 hours (Phase 0) ✅  
**Remaining:** 20-32 hours (Phases 1-4)

---

## Phase 0: Native Optimization ✅ COMPLETE

**Completed:** 2026-01-14 (10 hours)

Before implementing JMH benchmarks, we optimized the native C library to establish a clean baseline:

**Key Achievements:**
- 🎯 **33% decoder speedup** (41ms → 27.3ms)
- 🔍 **Identified LDPC as 75% bottleneck** through C-side profiling
- ⚡ **Matrix caching implemented** - 53% LDPC reduction
- 📊 **Native baseline documented** - 27.3ms decode time
- 💡 **Critical discovery** - Clean data requires 0 LDPC iterations

**Why This Matters:**
Phase 0 optimization means JMH benchmarks in subsequent phases can accurately measure **Java FFM overhead** separately from native performance. Without this baseline, we couldn't distinguish between slow FFM marshalling and slow C code.

**Full Details:** See [01a-phase0-native-profiling.md](01a-phase0-native-profiling.md)

---

## Getting Started

### Step 1: Read the Context
Start with **[01-overview.md](01-overview.md)** to understand:
- Current performance blind spots
- Strategic value of benchmarks
- Success criteria
- Risk mitigation

### Step 2: Follow the Phases
Execute each phase sequentially:

1. **[Phase 1](02-phase1-jmh-setup.md)** - Set up JMH infrastructure
   - Add dependencies
   - Configure Maven
   - Create first benchmark
   - Run `/test-coverage-update`

2. **[Phase 2](03-phase2-core-benchmarks.md)** - Core encoding benchmarks
   - All color modes (1-6)
   - Message size variations
   - ECC level impact
   - Run `/test-coverage-update`

3. **[Phase 3](04-phase3-advanced-metrics.md)** - Advanced metrics
   - Decoding performance
   - Memory profiling
   - FFM overhead
   - Run `/test-coverage-update`

4. **[Phase 4](05-phase4-ci-integration.md)** - CI integration
   - GitHub Actions workflow
   - Regression detection
   - Performance reports
   - Run `/test-coverage-update`

### Step 3: Validate Results
After all phases:
- [ ] Baseline metrics documented
- [ ] Regression detection active
- [ ] CI pipeline green
- [ ] Performance reports generated

---

## Prerequisites

### Required
- **JDK 21+** (Panama FFM support)
- **Maven 3.8+**
- **Native library** (`libjabcode.so` built)
- **Test suite passing** (205 tests, 0 failures)

### Knowledge
- Java performance tuning basics
- JMH concepts (warmup, iterations, forks)
- Maven project structure
- Basic statistics (mean, p50, p99)

---

## Key Design Decisions

### 1. JMH Over Custom Framework
**Why:** Industry standard, handles warmup/JIT/GC correctly, excellent reporting

### 2. Separate Maven Module
**Why:** Isolate benchmark dependencies, avoid polluting main classpath

### 3. Per-Phase Test Coverage
**Why:** Ensure benchmark code itself is tested, maintain TDD discipline

### 4. CI Integration from Start
**Why:** Catch regressions early, historical tracking, automated reporting

---

## What You'll Measure

### Performance Metrics
- **Encoding time** per color mode (nanoseconds/operation)
- **Decoding time** per color mode
- **Throughput** (operations/second)
- **Memory usage** (heap + native)

### Comparative Analysis
- Mode 2 (8-color) vs Mode 5 (64-color) vs Mode 6 (128-color)
- Single symbol vs cascaded (2, 3, 5 symbols)
- ECC level impact (3, 5, 7, 9)
- Message size scaling (100B → 100KB)

### FFM Overhead
- Native call latency
- Memory copy costs
- Struct allocation overhead

---

## Success Criteria

### Functional
- [ ] All color modes benchmarked (1-6)
- [ ] Encoding + decoding measured
- [ ] Memory profiling complete
- [ ] Baseline results documented

### Quality
- [ ] CV (Coefficient of Variation) < 5% for stable benchmarks
- [ ] Benchmark code has >80% test coverage
- [ ] All benchmarks pass in CI
- [ ] Reports generated automatically

### Operational
- [ ] CI pipeline runs benchmarks on PRs
- [ ] Regression alerts configured
- [ ] Historical trends tracked
- [ ] Performance SLAs defined

---

## Non-Goals

This plan does **NOT** cover:
- ❌ Optimization work (only measurement)
- ❌ Distributed benchmarking (single-machine only)
- ❌ Production monitoring (CI/dev only)
- ❌ External system integration (pure Java)

---

## Risk Mitigation

### Risk: JVM Warmup Variability
**Mitigation:** JMH handles warmup iterations, multiple forks, proper statistics

### Risk: Native Library State
**Mitigation:** Isolate each benchmark run, measure native call overhead separately

### Risk: CI Resource Constraints
**Mitigation:** Run benchmarks on dedicated agents, cache results, timeout limits

### Risk: Benchmark Maintenance Burden
**Mitigation:** Co-locate with tests, fail fast on errors, automated regression checks

---

## Quick Reference Commands

```bash
# Phase 1: Setup
cd panama-wrapper
mvn clean install
cd benchmarks
mvn verify

# Phase 2: Run specific benchmark
mvn exec:exec -Dbenchmark=EncodingBenchmark

# Phase 3: Generate reports
mvn jmh:run -Djmh.output=results.json

# Phase 4: CI integration
git push origin feature/benchmarks
# Watch GitHub Actions for results
```

---

## Next Steps

**Ready to start?**
1. Read [01-overview.md](01-overview.md) for strategic context
2. Begin [Phase 1](02-phase1-jmh-setup.md) implementation
3. Run `/test-coverage-update` after each phase
4. Review results and adjust plan as needed

**Questions or issues?**
- Check phase documents for troubleshooting sections
- Review [codebase audit](../panama-poc/codebase-audit/README.md) for context
- Consult [spec audit](../panama-poc/codebase-audit/jabcode-spec-audit/00-index.md) for requirements

---

**Status:** Ready for implementation (2026-01-12)
