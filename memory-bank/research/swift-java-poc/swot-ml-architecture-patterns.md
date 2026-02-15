# SWOT Analysis: ML Architecture Patterns for JABCode Scanning

## Overview

This document compares four ML integration architecture patterns for camera-based JABCode scanning:

1. **Pre-filter Pipeline** - ML detection before decode
2. **Parallel Pipeline** - Native-first with ML fallback
3. **Confidence-Routed Pipeline** - ML-guided routing
4. **Streaming-Optimized Pipeline** - Continuous capture for fountain codes (RFC 6330)

---

## Pattern A: Pre-filter Pipeline

```
Camera Frame → ML Detection → Crop Region → JABCode Decode
```

### Strengths

| Strength | Impact | Details |
|----------|--------|---------|
| **Reduced decode attempts** | High | Only decode when ML detects a barcode |
| **Smaller decode region** | High | Cropped image = faster native decode |
| **Handles cluttered scenes** | High | ML isolates barcode from background |
| **Multi-code support** | Medium | Can detect multiple barcodes per frame |
| **Orientation hints** | Medium | Detection can provide rotation info |

### Weaknesses

| Weakness | Impact | Mitigation |
|----------|--------|------------|
| **ML overhead on every frame** | High | Throttle to 5-10 fps |
| **Detection model required** | High | Need to train/obtain JABCode detector |
| **False negatives** | Medium | Missed detections = missed decodes |
| **Latency floor** | Medium | ML inference adds 10-25ms minimum |
| **Model size** | Low | +2-5 MB APK size |

### Opportunities

| Opportunity | Likelihood | Value |
|-------------|------------|-------|
| **Batch detection** | High | Detect multiple codes, decode in parallel |
| **Progressive refinement** | Medium | Use detection confidence to adjust decode params |
| **Cross-format support** | High | Same detector for QR, DataMatrix, JABCode |
| **Edge deployment** | Medium | On-device ML improving rapidly |

### Threats

| Threat | Likelihood | Impact |
|--------|------------|--------|
| **Model drift** | Low | JABCode appearance is standardized |
| **Device fragmentation** | Medium | ML inference speed varies by device |
| **Training data bias** | Medium | Model may fail on unseen conditions |
| **Maintenance burden** | Medium | Model updates, version management |

### Best For
- Cluttered environments (retail shelves, warehouses)
- Multi-barcode scanning
- Mixed barcode format apps

---

## Pattern B: Parallel Pipeline (Fallback Enhancement)

```
Camera Frame ─┬─→ JABCode Decode (primary)
              └─→ ML Enhancement (if decode fails)
```

### Strengths

| Strength | Impact | Details |
|----------|--------|---------|
| **Fast path for clean captures** | High | No ML overhead when native succeeds |
| **Graceful degradation** | High | ML only invoked when needed |
| **Lower average latency** | High | Most frames skip ML entirely |
| **Simpler ML requirements** | Medium | Enhancement model, not detection |
| **Battery efficient** | Medium | ML runs infrequently |

### Weaknesses

| Weakness | Impact | Mitigation |
|----------|--------|------------|
| **Worst-case latency** | High | Failed decode + ML = 2x processing |
| **Enhancement limitations** | Medium | Can't fix fundamentally bad captures |
| **No detection benefit** | Medium | Still decodes full frame |
| **Sequential bottleneck** | Medium | Can't parallelize easily |

### Opportunities

| Opportunity | Likelihood | Value |
|-------------|------------|-------|
| **Adaptive thresholds** | High | Learn when to skip native attempt |
| **Enhancement caching** | Medium | Reuse enhanced frames |
| **Quality metrics** | High | Use decode failure patterns to improve |
| **Hybrid enhancement** | Medium | Combine multiple enhancement techniques |

### Threats

| Threat | Likelihood | Impact |
|--------|------------|--------|
| **User frustration** | Medium | Slow path feels inconsistent |
| **Enhancement model quality** | Medium | Poor enhancement = still fails |
| **Resource spikes** | Low | Sudden ML load on failure |

### Best For
- Controlled environments with occasional challenges
- Battery-sensitive applications
- Apps where most captures are clean

---

## Pattern C: Confidence-Routed Pipeline

```
Camera Frame → ML Confidence → High? → Fast Decode
                             → Low?  → Enhanced Decode
```

### Strengths

| Strength | Impact | Details |
|----------|--------|---------|
| **Intelligent routing** | High | Right tool for each frame |
| **Predictable latency** | Medium | Avoids worst-case sequential path |
| **Optimized resource use** | High | Enhancement only when beneficial |
| **Learning potential** | High | Confidence model improves over time |
| **User experience** | Medium | Consistent perceived performance |

### Weaknesses

| Weakness | Impact | Mitigation |
|----------|--------|------------|
| **Two ML models** | High | Confidence + enhancement = complexity |
| **Confidence calibration** | High | Miscalibration = wrong routing |
| **Development complexity** | High | More code paths to test |
| **Training data needs** | High | Need labeled difficulty examples |
| **Threshold tuning** | Medium | Requires experimentation |

### Opportunities

| Opportunity | Likelihood | Value |
|-------------|------------|-------|
| **Online learning** | Medium | Adapt confidence model to user patterns |
| **Multi-tier routing** | Medium | More than 2 paths (fast/medium/slow) |
| **Feedback loop** | High | Decode results improve confidence model |
| **A/B testing** | High | Compare routing strategies |

### Threats

| Threat | Likelihood | Impact |
|--------|------------|--------|
| **Over-engineering** | High | Complexity may not justify benefits |
| **Confidence model errors** | Medium | Wrong routing = poor UX |
| **Maintenance overhead** | High | Two models to maintain |
| **Debugging difficulty** | Medium | Hard to trace routing decisions |

### Best For
- High-volume scanning with variable conditions
- Apps with strong analytics/feedback infrastructure
- Teams with ML expertise

---

## Pattern D: Streaming-Optimized Pipeline

```
Camera Stream → Frame Buffer → Parallel Decode → Symbol Accumulator → RaptorQ Decoder
                    ↓                                    ↓
              ML Frame Selector              Completion Tracker (k of n)
```

### Strengths

| Strength | Impact | Details |
|----------|--------|--------|
| **Continuous data accumulation** | Critical | Collects symbols until RaptorQ threshold reached |
| **Tolerates partial failures** | High | Missing frames don't block - fountain codes are redundant |
| **High throughput** | High | Parallel decode of buffered frames |
| **Graceful degradation** | High | Works with lossy captures (motion blur, occlusion) |
| **Optimal for large payloads** | Critical | Only viable pattern for multi-KB data transfer |
| **No re-scan needed** | High | Accumulates until complete, user just holds camera |

### Weaknesses

| Weakness | Impact | Mitigation |
|----------|--------|------------|
| **Memory pressure** | High | Limit buffer size, discard low-confidence frames |
| **Complex state management** | High | Track which symbols received, handle duplicates |
| **RaptorQ library dependency** | Medium | Add RFC 6330 implementation (+500KB) |
| **Battery consumption** | Medium | Continuous camera + decode = high power |
| **UI complexity** | Medium | Need progress indicator, completion feedback |
| **Latency to first result** | High | Must accumulate k symbols before any decode |

### Opportunities

| Opportunity | Likelihood | Value |
|-------------|------------|-------|
| **Adaptive frame rate** | High | Slow down when accumulating, speed up when missing symbols |
| **Predictive completion** | Medium | Estimate time to completion based on capture rate |
| **Multi-code parallelism** | High | Decode multiple JABCodes in frame simultaneously |
| **Quality-weighted accumulation** | Medium | Prioritize high-confidence decodes |
| **Resume capability** | Medium | Save state, resume streaming session later |
| **Offline-first data transfer** | High | Air-gapped data transfer via printed codes |

### Threats

| Threat | Likelihood | Impact |
|--------|------------|--------|
| **User impatience** | High | Users may abandon before completion |
| **Symbol sequence errors** | Medium | Out-of-order or duplicate handling complexity |
| **Memory exhaustion** | Medium | Long streams on low-memory devices |
| **Thermal throttling** | Medium | Continuous processing causes device heating |
| **RaptorQ patent concerns** | Low | RFC 6330 is royalty-free, but verify |

### Best For
- Large data transfer (multi-KB payloads)
- Air-gapped/offline data exchange
- Document/file transfer via printed codes
- Scenarios where re-scanning is impractical
- RFC 6330 RaptorQ fountain code implementations

### Architecture Components

```kotlin
// Core streaming components
class StreamingDecoder {
    private val frameBuffer = CircularBuffer<CameraFrame>(capacity = 30)
    private val symbolAccumulator = SymbolAccumulator()
    private val raptorQDecoder = RaptorQDecoder()
    
    fun onFrame(frame: CameraFrame) {
        frameBuffer.add(frame)
        
        // Parallel decode buffered frames
        val decoded = frameBuffer.parallelMap { 
            JABCodeMobile.decodeFromCamera(it.bitmap) 
        }.filterNotNull()
        
        // Accumulate unique symbols
        decoded.forEach { result ->
            val symbol = parseStreamingSymbol(result)
            symbolAccumulator.add(symbol)
        }
        
        // Check RaptorQ completion
        if (symbolAccumulator.hasEnoughSymbols()) {
            val payload = raptorQDecoder.decode(symbolAccumulator.symbols)
            onComplete(payload)
        }
    }
}

// Symbol accumulator with deduplication
class SymbolAccumulator {
    private val symbols = mutableMapOf<Int, EncodingSymbol>()
    private var sourceBlockCount = 0
    private var symbolsNeeded = 0
    
    fun add(symbol: StreamingSymbol) {
        if (sourceBlockCount == 0) {
            sourceBlockCount = symbol.sourceBlockCount
            symbolsNeeded = symbol.k  // Minimum symbols for decode
        }
        symbols[symbol.esi] = symbol.data  // ESI = Encoding Symbol ID
    }
    
    fun hasEnoughSymbols(): Boolean = symbols.size >= symbolsNeeded
    fun progress(): Float = symbols.size.toFloat() / symbolsNeeded
}
```

### UI/UX Requirements

| Element | Purpose |
|---------|--------|
| **Progress bar** | Show k/n symbols collected |
| **Frame rate indicator** | Show capture speed |
| **Quality indicator** | Show decode success rate |
| **Completion animation** | Celebrate successful transfer |
| **Cancel button** | Allow user to abort |
| **Estimated time** | "~5 seconds remaining" |

### Performance Targets

| Metric | Target | Notes |
|--------|--------|-------|
| Frame capture rate | 15-30 fps | Balance speed vs battery |
| Decode throughput | 10+ symbols/sec | Parallel processing |
| Memory usage | <50 MB | Buffer + accumulator |
| Time to 1KB payload | <10 seconds | With 8-color JABCode |
| Time to 10KB payload | <60 seconds | With 64-color JABCode |

---

## Comparative Matrix

| Criterion | Pre-filter | Parallel | Confidence-Routed | Streaming-Optimized |
|-----------|------------|----------|-------------------|---------------------|
| **Implementation Complexity** | Medium | Low | High | High |
| **ML Model Requirements** | Detection | Enhancement | Confidence + Enhancement | Frame Selection (optional) |
| **Best-case Latency** | Medium | Fast | Fast | N/A (continuous) |
| **Worst-case Latency** | Medium | Slow | Medium | N/A (continuous) |
| **Battery Impact** | Medium | Low | Medium | High |
| **Cluttered Scene Handling** | Excellent | Poor | Good | Good |
| **Clean Capture Speed** | Medium | Excellent | Excellent | N/A |
| **APK Size Impact** | +2-5 MB | +2-5 MB | +4-8 MB | +2-5 MB (+RaptorQ) |
| **Maintenance Burden** | Medium | Low | High | High |
| **Debugging Ease** | Good | Good | Poor | Medium |
| **Large Payload Support** | Poor | Poor | Poor | Excellent |
| **Partial Failure Tolerance** | Poor | Poor | Poor | Excellent |

---

## Scoring Summary

| Pattern | Strengths | Weaknesses | Opportunities | Threats | Net Score |
|---------|-----------|------------|---------------|---------|-----------|
| **Pre-filter** | 5 | 5 | 4 | 4 | +0 |
| **Parallel** | 5 | 4 | 4 | 3 | +2 |
| **Confidence-Routed** | 5 | 5 | 4 | 4 | +0 |
| **Streaming-Optimized** | 6 | 6 | 6 | 5 | +1 |

---

## Decision Framework

### Choose Pre-filter When:
- ✅ Scanning in cluttered environments
- ✅ Need to detect multiple barcodes per frame
- ✅ Mixed barcode format support needed
- ✅ Detection accuracy is critical
- ❌ Battery life is primary concern
- ❌ Most captures are clean/controlled

### Choose Parallel When:
- ✅ Most captures succeed without ML
- ✅ Battery efficiency matters
- ✅ Simple implementation preferred
- ✅ Occasional challenging conditions
- ❌ Cluttered scenes are common
- ❌ Consistent latency required

### Choose Confidence-Routed When:
- ✅ High-volume scanning (1000s/day)
- ✅ Strong analytics infrastructure
- ✅ Team has ML expertise
- ✅ Variable conditions are common
- ❌ Simple deployment needed
- ❌ Limited development resources

---

## Recommendation for JABCode

### Primary: Start with Native-Only

Before adding ML, validate that native decode handles your use cases:

```kotlin
// Phase 0: Native only
val result = JABCodeMobile.decodeFromCamera(bitmap)
```

### If ML Needed: Parallel Pipeline (Pattern B)

For most JABCode applications, **Parallel Pipeline** offers the best tradeoff:

1. **Fast path** for controlled captures (majority of use)
2. **ML fallback** for challenging conditions
3. **Lowest complexity** to implement and maintain
4. **Battery efficient** - ML only when needed

```kotlin
// Phase 1: Parallel with enhancement fallback
var result = JABCodeMobile.decodeFromCamera(bitmap)
if (result == null) {
    val enhanced = imageEnhancer.enhance(bitmap)
    result = JABCodeMobile.decodeFromCamera(enhanced)
}
```

### If Cluttered Scenes: Pre-filter Pipeline (Pattern A)

If field testing reveals detection issues in cluttered environments:

1. Train/obtain JABCode detection model
2. Implement pre-filter pipeline
3. Crop to detected region before decode

```kotlin
// Phase 2: Pre-filter for cluttered scenes
val detections = jabcodeDetector.detect(bitmap)
for (detection in detections) {
    val cropped = cropToRegion(bitmap, detection.boundingBox)
    val result = JABCodeMobile.decodeFromCamera(cropped)
    if (result != null) return result
}
```

### Avoid Confidence-Routed Unless:
- You have dedicated ML engineering resources
- Analytics show clear bimodal difficulty distribution
- Volume justifies complexity (10,000+ scans/day)

### Choose Streaming-Optimized When:
- ✅ Transferring large payloads (>1 KB)
- ✅ Using RFC 6330 RaptorQ fountain codes
- ✅ Air-gapped/offline data transfer needed
- ✅ Re-scanning is impractical or impossible
- ✅ Partial capture tolerance required
- ❌ Single small barcode scanning
- ❌ Battery life is critical
- ❌ Instant results expected

---

## Implementation Priority

| Priority | Pattern | Trigger | Effort |
|----------|---------|---------|--------|
| 1 | Native-only | Default | 1 day |
| 2 | Parallel | >10% decode failures | 3-5 days |
| 3 | Pre-filter | Cluttered scene issues | 1-2 weeks |
| 4 | Streaming-Optimized | Large payload / fountain code use case | 2-3 weeks |
| 5 | Confidence-Routed | High volume + variable conditions | 2-4 weeks |

---

## Hybrid Architecture: Pipeline Switcher

Yes, sir. Implementing a **dynamic pipeline switcher** is not only possible but recommended for production applications. This allows runtime selection based on context, user preference, or automatic detection.

### Architecture Overview

```
                              ┌─────────────────────┐
                              │  Pipeline Selector  │
                              │  (Strategy Pattern) │
                              └──────────┬──────────┘
                                         │
         ┌───────────────┬───────────────┼───────────────┬───────────────┐
         ▼               ▼               ▼               ▼               ▼
    ┌─────────┐    ┌──────────┐    ┌───────────┐    ┌──────────┐    ┌─────────┐
    │ Native  │    │ Parallel │    │ Pre-filter│    │Streaming │    │Confidence│
    │  Only   │    │ Fallback │    │    ML     │    │ Optimized│    │ Routed  │
    └─────────┘    └──────────┘    └───────────┘    └──────────┘    └─────────┘
```

### Implementation: Strategy Pattern

```kotlin
// Pipeline interface
interface ScanPipeline {
    val name: String
    val description: String
    suspend fun process(frame: CameraFrame): DecodeResult?
    fun isApplicable(context: ScanContext): Boolean
}

// Concrete implementations
class NativePipeline : ScanPipeline {
    override val name = "Native"
    override val description = "Fast native decode, no ML"
    
    override suspend fun process(frame: CameraFrame): DecodeResult? {
        return JABCodeMobile.decodeFromCamera(frame.bitmap)
    }
    
    override fun isApplicable(context: ScanContext) = true // Always available
}

class ParallelPipeline(
    private val enhancer: ImageEnhancer
) : ScanPipeline {
    override val name = "Parallel"
    override val description = "Native-first with ML enhancement fallback"
    
    override suspend fun process(frame: CameraFrame): DecodeResult? {
        // Try native first
        JABCodeMobile.decodeFromCamera(frame.bitmap)?.let { return it }
        
        // Fallback to enhanced
        val enhanced = enhancer.enhance(frame.bitmap)
        return JABCodeMobile.decodeFromCamera(enhanced)
    }
    
    override fun isApplicable(context: ScanContext) = context.mlAvailable
}

class PrefilterPipeline(
    private val detector: JABCodeDetector
) : ScanPipeline {
    override val name = "Pre-filter"
    override val description = "ML detection before decode"
    
    override suspend fun process(frame: CameraFrame): DecodeResult? {
        val detections = detector.detect(frame.bitmap)
        for (detection in detections) {
            val cropped = cropToRegion(frame.bitmap, detection.boundingBox)
            JABCodeMobile.decodeFromCamera(cropped)?.let { return it }
        }
        return null
    }
    
    override fun isApplicable(context: ScanContext) = 
        context.mlAvailable && context.environment == Environment.CLUTTERED
}

class StreamingPipeline(
    private val raptorQDecoder: RaptorQDecoder
) : ScanPipeline {
    override val name = "Streaming"
    override val description = "Continuous capture for fountain codes"
    
    private val accumulator = SymbolAccumulator()
    
    override suspend fun process(frame: CameraFrame): DecodeResult? {
        val decoded = JABCodeMobile.decodeFromCamera(frame.bitmap) ?: return null
        val symbol = parseStreamingSymbol(decoded)
        accumulator.add(symbol)
        
        return if (accumulator.hasEnoughSymbols()) {
            val payload = raptorQDecoder.decode(accumulator.symbols)
            DecodeResult(payload, isComplete = true)
        } else {
            DecodeResult(progress = accumulator.progress(), isComplete = false)
        }
    }
    
    override fun isApplicable(context: ScanContext) = 
        context.expectsLargePayload || context.streamingMode
}
```

### Pipeline Selector

```kotlin
class PipelineSelector(
    private val pipelines: List<ScanPipeline>,
    private val preferences: UserPreferences
) {
    // Manual selection
    fun selectByName(name: String): ScanPipeline {
        return pipelines.find { it.name == name } 
            ?: pipelines.first() // Fallback to native
    }
    
    // Automatic selection based on context
    fun selectForContext(context: ScanContext): ScanPipeline {
        // User override takes precedence
        preferences.preferredPipeline?.let { pref ->
            pipelines.find { it.name == pref }?.let { return it }
        }
        
        // Auto-select based on context
        return when {
            context.streamingMode -> pipelines.filterIsInstance<StreamingPipeline>().firstOrNull()
            context.environment == Environment.CLUTTERED -> pipelines.filterIsInstance<PrefilterPipeline>().firstOrNull()
            context.recentFailureRate > 0.1f -> pipelines.filterIsInstance<ParallelPipeline>().firstOrNull()
            else -> pipelines.filterIsInstance<NativePipeline>().firstOrNull()
        } ?: pipelines.first()
    }
    
    // Adaptive selection with learning
    fun selectAdaptive(context: ScanContext, history: ScanHistory): ScanPipeline {
        val stats = history.getStatsForContext(context)
        
        // Find pipeline with best success rate for this context
        return pipelines
            .filter { it.isApplicable(context) }
            .maxByOrNull { stats.successRate(it.name) }
            ?: pipelines.first()
    }
}
```

### Scan Context

```kotlin
data class ScanContext(
    val environment: Environment = Environment.UNKNOWN,
    val lightLevel: LightLevel = LightLevel.NORMAL,
    val mlAvailable: Boolean = true,
    val streamingMode: Boolean = false,
    val expectsLargePayload: Boolean = false,
    val recentFailureRate: Float = 0f,
    val batteryLevel: Float = 1f,
    val thermalState: ThermalState = ThermalState.NOMINAL
)

enum class Environment { UNKNOWN, CONTROLLED, CLUTTERED, LOW_LIGHT, OUTDOOR }
enum class LightLevel { LOW, NORMAL, BRIGHT }
enum class ThermalState { NOMINAL, WARM, THROTTLING }
```

### UI Integration

```kotlin
// Settings screen - manual pipeline selection
class ScanSettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val pipelinePreference = ListPreference(requireContext()).apply {
            key = "scan_pipeline"
            title = "Scan Mode"
            entries = arrayOf("Auto", "Fast (Native)", "Enhanced", "Multi-code", "Streaming")
            entryValues = arrayOf("auto", "Native", "Parallel", "Pre-filter", "Streaming")
            setDefaultValue("auto")
            summary = "Select scanning strategy"
        }
        preferenceScreen.addPreference(pipelinePreference)
    }
}

// Runtime switching via FAB or menu
class ScanFragment : Fragment() {
    private var currentPipeline: ScanPipeline = nativePipeline
    
    private fun showPipelineSwitcher() {
        val options = pipelineSelector.pipelines.map { it.name to it.description }
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Select Scan Mode")
            .setItems(options.map { "${it.first}: ${it.second}" }.toTypedArray()) { _, which ->
                currentPipeline = pipelineSelector.selectByName(options[which].first)
                updatePipelineIndicator()
            }
            .show()
    }
    
    private fun updatePipelineIndicator() {
        binding.pipelineBadge.text = currentPipeline.name
    }
}
```

### Automatic Switching Triggers

| Trigger | From | To | Condition |
|---------|------|-----|-----------|
| **Failure rate** | Native | Parallel | >10% failures in last 20 frames |
| **Multi-code detected** | Any | Pre-filter | ML detects 2+ barcodes |
| **Streaming header** | Any | Streaming | First symbol contains RaptorQ metadata |
| **Battery low** | Any | Native | Battery <20% |
| **Thermal throttle** | Any | Native | Device overheating |
| **User gesture** | Any | Any | Long-press or menu selection |

### Seamless Transition

```kotlin
class AdaptiveScanController(
    private val selector: PipelineSelector,
    private val analyzer: FrameAnalyzer
) {
    private var currentPipeline: ScanPipeline = selector.selectByName("Native")
    private val frameHistory = RingBuffer<FrameResult>(20)
    
    fun onFrame(frame: CameraFrame) {
        val context = buildContext()
        
        // Check if pipeline switch needed
        val recommended = selector.selectAdaptive(context, scanHistory)
        if (recommended != currentPipeline) {
            transitionTo(recommended)
        }
        
        // Process with current pipeline
        val result = currentPipeline.process(frame)
        frameHistory.add(FrameResult(frame, result, currentPipeline.name))
        
        handleResult(result)
    }
    
    private fun transitionTo(newPipeline: ScanPipeline) {
        Log.i("Scan", "Switching from ${currentPipeline.name} to ${newPipeline.name}")
        
        // Notify UI
        onPipelineChanged?.invoke(newPipeline)
        
        // Smooth transition - don't discard in-flight work
        if (currentPipeline is StreamingPipeline && newPipeline !is StreamingPipeline) {
            // Warn user about losing streaming progress
            showStreamingAbortWarning()
        }
        
        currentPipeline = newPipeline
    }
    
    private fun buildContext(): ScanContext {
        return ScanContext(
            recentFailureRate = frameHistory.count { it.result == null } / frameHistory.size.toFloat(),
            batteryLevel = batteryManager.batteryLevel,
            thermalState = thermalManager.currentState,
            streamingMode = preferences.streamingMode
        )
    }
}
```

### Benefits of Pipeline Switching

| Benefit | Description |
|---------|-------------|
| **Adaptability** | Right tool for each situation |
| **User control** | Power users can optimize manually |
| **Graceful degradation** | Fall back when resources constrained |
| **Future-proof** | Add new pipelines without refactoring |
| **A/B testing** | Compare pipeline performance in production |
| **Battery optimization** | Switch to lighter pipelines when needed |

### Implementation Effort

| Component | Effort | Priority |
|-----------|--------|----------|
| Pipeline interface + Native | 1 day | P0 |
| Parallel pipeline | 2 days | P1 |
| Manual UI switching | 1 day | P1 |
| Context detection | 2 days | P2 |
| Automatic switching | 3 days | P2 |
| Pre-filter pipeline | 1 week | P3 |
| Streaming pipeline | 2 weeks | P3 |
| Adaptive learning | 1 week | P4 |

**Total: 4-6 weeks** for full implementation, but can ship incrementally.

---

*Document created: 2026-01-24*
*Last updated: 2026-01-25*
*Related: [android-camera-jabcode-integration.md](android-camera-jabcode-integration.md), [swot-camerax-vs-camera2.md](swot-camerax-vs-camera2.md)*
