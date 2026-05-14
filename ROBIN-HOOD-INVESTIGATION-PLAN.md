# Robin Hood Investigation Plan (Phase 2H Contingency)

**Status:** CONTINGENCY (Activate if Phase 2G achieves detection but NOT decode)  
**Author:** Bayesian Council FP2-HORIZONTAL-001 (Robin Hood's Advocate dissent)  
**Date:** 2026-05-14  
**Trigger:** FP2 detection sustained BUT zero successful decodes after Phase 2G

---

## Executive Summary

**Hypothesis:** After 7 iterations of geometric tolerance tuning, continued zero-decode status suggests **non-geometric root causes** are now dominant blockers.

**Robin Hood's Core Argument:**
> "We're optimizing locally (tolerance) without validating globally (decode logic). Pattern detection success does NOT guarantee decode success. Time to investigate root causes beyond geometry."

---

## Investigation Trigger Conditions

### **ACTIVATE Robin Hood Investigation IF:**

1. ✅ **Phase 2G deployed** (FP2 horizontal + vertical both at 3.5x)
2. ✅ **FP2 detection sustained** (≥1 pattern per frame over 2 hours)
3. ❌ **Zero successful decodes** (4-pattern lock never achieved)
4. ❌ **Horizontal failures persist** (>20% despite 3.5x boost)

### **DO NOT ACTIVATE IF:**
- Phase 2G achieves first successful decode → Continue momentum path
- Horizontal failures drop below 20% AND decodes imminent → Give more time
- False positives exceed 5% → Rollback to Phase 2F, different issue

---

## Investigation Framework

### **Three-Axis Analysis**

```
Axis 1: COLOR VALIDATION (Geometric pass, color fail)
Axis 2: STATE MERGE LOGIC (State counting too aggressive)
Axis 3: SELECTION LOGIC (Pattern detected but rejected)
```

**Rationale:** Tolerance fixes geometry. If geometry is now adequate (detection proves this), failures must be in color/state/selection domains.

---

## Axis 1: Color Validation Investigation

### **Hypothesis**
FP2 patterns pass geometric checks but fail color consistency validation.

### **Diagnostic Steps**

#### **Step 1.1: Add Color Diagnostic Logging**

**Location:** `src/jabcode/detector.c` → `crossCheckColor()`

```c
// In crossCheckColor function (around line 800-850)
jab_boolean crossCheckColor(jab_bitmap* ch, jab_int32 core_color, 
                            jab_int32 module_size, jab_int32 core_size,
                            jab_int32 centerx, jab_int32 centery, jab_int32 dir)
{
    // ADD THIS DIAGNOSTIC
    jab_boolean color_result = /* existing color check logic */;
    
    if (!color_result) {
        JAB_REPORT_INFO(("COLOR_CHECK_FAIL: pos=(%d,%d), dir=%d, core=%d, expected=%d, tolerance=±%d",
            centerx, centery, dir, /* actual_color */, core_color, /* tolerance */));
    }
    
    return color_result;
}
```

**Data to capture:**
- Position of failure
- Direction of scan
- Expected core color vs actual measured color
- Color tolerance threshold

#### **Step 1.2: FP2-Specific Color Tracking**

```c
// In crossCheckPattern (around line 829-950)
if(fp->type == FP2) {
    JAB_REPORT_INFO(("FP2_COLOR: Checking R=%d, G=%d, B=%d channels at (%.1f,%.1f)",
        ch[0]->pixel[...], ch[1]->pixel[...], ch[2]->pixel[...],
        fp->center.x, fp->center.y));
}
```

### **Analysis Questions**
1. Do FP2 patterns fail color checks at higher rate than FP0/1/3?
2. Are failures clustered in specific channels (R/G/B)?
3. Do failures correlate with module boundaries (edge effects)?
4. Is screen color reproduction causing systematic color shifts?

### **Decision Tree**
```
Color failures > 30% of FP2 rejections?
├─ YES → Investigate color tolerance boost or calibration
└─ NO  → Proceed to Axis 2 (State Merge)
```

---

## Axis 2: State Merge Logic Investigation

### **Hypothesis**
Even with 3.5x geometric tolerance, state merge threshold is counting states incorrectly (merging when shouldn't, or not merging when should).

### **Diagnostic Steps**

#### **Step 2.1: State Count Tracking**

**Location:** `crossCheckPatternVertical()` and `crossCheckPatternHorizontal()`

```c
// In crossCheckPatternVertical (around line 555-645)
jab_boolean crossCheckPatternVertical(..., jab_int32 type)
{
    // EXISTING: state_merge_threshold logic
    
    // ADD THIS for FP2
    if (type == FP2) {
        JAB_REPORT_INFO(("FP2_STATE_MERGE: threshold=%d, raw_states=[%d,%d,%d,%d,%d], merged_states=[%d,%d,%d,%d,%d]",
            state_merge_threshold,
            /* raw state counts before merge */,
            state_count[0], state_count[1], state_count[2], state_count[3], state_count[4]));
    }
}
```

#### **Step 2.2: Per-Direction State Analysis**

Track state merging separately for h/v/d:
```
FP2 Vertical: threshold=1, merges=X, failures=Y%
FP2 Horizontal: threshold=1, merges=X, failures=Y%
FP2 Diagonal: threshold=1, merges=X, failures=Y%
```

### **Analysis Questions**
1. Are failures correlated with high merge counts?
2. Does threshold=1 (screen mode) cause over-merging for FP2?
3. Would FP2-specific threshold=0 (no merging) improve success?

### **Experimental Test**
```c
// TEMPORARY TEST CODE
#if SCREEN_DISPLAY_MODE
    jab_int32 state_merge_threshold = (type == FP2) ? 0 : 1;  // FP2: no merge
#else
    jab_int32 state_merge_threshold = 3;
#endif
```

### **Decision Tree**
```
State merge correlation with failures?
├─ YES → Deploy FP2-specific threshold adjustment
└─ NO  → Proceed to Axis 3 (Selection Logic)
```

---

## Axis 3: Selection Logic Investigation

### **Hypothesis**
Patterns are detected and pass all checks, but `selectBestPatterns()` rejects them due to scoring/ranking logic.

### **Diagnostic Steps**

#### **Step 3.1: Pattern Scoring Visibility**

**Location:** `selectBestPatterns()` (around line 1270-1320)

```c
// In selectBestPatterns
for (each candidate pattern) {
    if (pattern->type == FP2) {
        JAB_REPORT_INFO(("FP2_SELECTION: candidate at (%.1f,%.1f), score=%.2f, found_count=%d, rank=%d/%d",
            pattern->center.x, pattern->center.y,
            /* pattern score */, found_count, /* current rank */, total_candidates));
    }
}

// After selection
JAB_REPORT_INFO(("FP2_SELECTED: count=%d (needed %d for decode)", 
    selected_fp2_count, MIN_PATTERNS_FOR_DECODE));
```

#### **Step 3.2: found_count Threshold Analysis**

**Current:** `found_count >= 2` required (Phase 2D change)

**Question:** Is FP2 hitting `found_count < 2` more often than other patterns?

```c
if (found_count < found_count_threshold) {
    if (pattern->type == FP2) {
        JAB_REPORT_INFO(("FP2_REJECT_THRESHOLD: found_count=%d < threshold=%d at (%.1f,%.1f)",
            found_count, found_count_threshold, pattern->center.x, pattern->center.y));
    }
}
```

### **Analysis Questions**
1. Are FP2 patterns scoring lower than FP0/1/3?
2. Is `found_count` threshold rejecting otherwise valid FP2s?
3. Do we need FP2-specific scoring weights?

### **Decision Tree**
```
Selection logic rejects valid FP2s?
├─ YES → Adjust found_count or scoring for FP2
└─ NO  → Problem is in decode stage (post-selection)
```

---

## Axis 4: Interaction Effects (Cross-Axis)

### **Hypothesis**
H/V/D failures are NOT independent—they interact in ways geometric tolerance doesn't address.

### **Diagnostic Approach**

**Correlation Matrix:**
```
Track: (H_pass, V_pass, D_pass) → Decode_success

Pattern analysis:
- H=pass, V=pass, D=fail → 0 decodes?
- H=pass, V=fail, D=pass → 0 decodes?
- H=fail, V=pass, D=pass → 0 decodes?

Expected: If failures independent, ANY two passing should sometimes decode
Observed: If NO decodes despite varied pass/fail patterns → Interaction effect
```

### **Interaction Test**
Add logging at pattern finalization:
```c
JAB_REPORT_INFO(("FP2_COMPLETE: H=%s, V=%s, D=%s, selected=%s",
    h_check ? "PASS" : "FAIL",
    v_check ? "PASS" : "FAIL", 
    d_check ? "PASS" : "FAIL",
    selected ? "YES" : "NO"));
```

Look for patterns like: "Always failing when H=pass AND V=pass but D=fail"

---

## Implementation Timeline

### **Phase 2H-Investigation (6 hours total)**

**Hour 1-2: Setup & Data Collection**
- Add diagnostic logging (all 3 axes)
- Build and deploy instrumented APK
- Capture baseline logs (30 scans minimum)

**Hour 3-4: Analysis**
- Parse logs for patterns
- Generate correlation matrices
- Identify dominant failure mode

**Hour 5-6: Hypothesis Testing**
- Deploy targeted fix (color/state/selection)
- Validate improvement
- Document findings

### **Deliverables**
1. Diagnostic log dataset (annotated)
2. Analysis report with root cause identification
3. Proposed fix (Phase 2H implementation)
4. Updated Council brief for Phase 2H decision

---

## Success Criteria

### **Investigation Successful IF:**
1. ✅ Root cause identified with >70% confidence
2. ✅ Failure mode clearly NOT geometric tolerance
3. ✅ Targeted fix proposed with validation plan
4. ✅ Fix addresses >50% of remaining failures

### **Investigation Inconclusive IF:**
- Multiple failure modes of equal weight
- No clear dominant pattern
- More data needed → Extend investigation 6 more hours

### **Investigation Invalidated IF:**
- Turns out geometry WAS still the issue (rare, but possible)
- False positives are real problem (Cassandra was right)
- External factors (hardware, test setup) dominating

---

## Contingency: If Investigation Fails

### **Fallback Options**

**Option 1: Brute Force Diagonal**
- Deploy 3.5x for FP2 diagonal (complete the tolerance trifecta)
- Risk: May hit false positive ceiling
- Confidence: 40% (diminishing returns territory)

**Option 2: Expert Consultation**
- Review Bugert 2024 paper for color calibration insights
- Search for JABCode community forums/issues
- Consider reaching out to original JABCode authors

**Option 3: Simplification**
- Accept that 8-color mode works, 16+ needs calibration hardware
- Document limitation and focus on other improvements
- Strategic retreat with dignity

---

## Key Insights for Future Iterations

### **Lazarus's Lesson: Detection ≠ Decode**

Pattern detection is NECESSARY but not SUFFICIENT for successful decode.

**Future debugging protocol:**
1. Establish detection baseline
2. THEN investigate decode pipeline
3. Don't conflate the two stages

### **Robin Hood's Reminder: Root Cause > Symptoms**

After N iterations of similar fixes, PAUSE and ask:
- Have we validated the problem model?
- Are we treating symptoms or disease?
- What would falsify our current hypothesis?

### **Historian's Counter: Proven Patterns First**

But also remember: Sometimes the simplest answer IS correct. Tolerance worked 5 times before failing to decode. That's 5/6 = 83% success. Not a pattern to abandon lightly.

---

## Activation Checklist

**Before activating this plan, confirm:**

- [ ] Phase 2G deployed and tested (2+ hours)
- [ ] FP2 detection validated (≥1 pattern per frame)
- [ ] Zero decode attempts successful
- [ ] False positive rate < 5% (not a false positive problem)
- [ ] Team prepared for 6-hour investigation sprint
- [ ] Diagnostic build environment ready
- [ ] Log capture tools configured

**When ready, proceed to Hour 1-2: Setup & Data Collection**

---

**Sir, the contingency plan is prepared. Robin Hood's investigation framework is ready to deploy should Phase 2G achieve detection but fail to deliver decodes.**
