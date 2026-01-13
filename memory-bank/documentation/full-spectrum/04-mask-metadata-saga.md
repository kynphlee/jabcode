# The Mask Metadata Saga
**A Detective Story in C and Java** 🔍

*A technical deep-dive into one of the most subtle and devastating bugs in the JABCode encoder, and how we hunted it down.*

---

## The Mystery

**December 2025**. All tests passing for 4-color through 32-color modes. Beautiful. Then we run ColorMode5Test (64-color mode):

```
❌ LDPC decoding failed
❌ LDPC decoding failed  
❌ LDPC decoding failed
```

Every. Single. Test. Failed.

The kicker? The *encoder* reported success. The *decoder* found the barcode. But when it tried to decode the actual data, the LDPC error correction threw up its hands and said "this is nonsense."

Something was corrupting the bit stream between encoding and decoding. But what?

---

## The Investigation Begins

### Clue #1: It Works at Low Color Counts

4-color, 8-color, 16-color, 32-color—all perfect. But 64-color? Complete failure. 128-color? Also broken.

**Hypothesis:** Something special happens at 64+ colors that breaks the decoder.

### Clue #2: Encoding Succeeds, Decoding Fails

The encoder reported successful encoding. The image was created. The decoder found the barcode and extracted *something*. But the data was gibberish.

**Hypothesis:** The problem isn't in locating the barcode, it's in interpreting the data.

### Clue #3: The Logging Trail

We added debug logging to both encoder and decoder to track the mask pattern selection:

**Encoder output:**
```c
[ENCODER] Selected mask_type: 0 (best penalty score)
[ENCODER] Applying mask pattern 0 to data
```

**Decoder output:**
```c
[DECODER] Read mask_type from metadata: 7
[DECODER] Demasking with pattern 7
```

**Wait. What?**

The encoder used mask pattern 0. The decoder read pattern 7 from the metadata. Houston, we have a problem. 🚨

---

## Understanding Masking

Before we go further, let's understand what masking does in JABCode.

### What Is Masking?

JABCode applies a mathematical pattern to the data modules to avoid problematic patterns (like long runs of the same color). Think of it like:

1. **Encoder**: XOR data with mask pattern → creates encoded image
2. **Decoder**: XOR encoded image with same mask pattern → recovers data

**Critical point:** Both sides must use the *same* mask pattern. If they disagree, you get garbage.

### How Is the Mask Pattern Chosen?

The encoder:
1. Tries all possible mask patterns (0-7)
2. Calculates a "penalty score" for each (based on pattern quality)
3. Picks the mask with the lowest penalty
4. **Should write** that mask number to the metadata
5. Applies that mask to the data

The decoder:
1. Reads the mask number from metadata
2. Applies that mask to extract the data

### The Metadata Problem

The encoder was selecting mask pattern 0 (best score), but writing pattern 7 (the default) to the metadata. The decoder faithfully read pattern 7 and used it to demask.

**Result:** Complete bit corruption. The XOR with the wrong pattern scrambled every single bit of data, making LDPC decoding impossible.

---

## The Hunt for the Bug

### Following the Code Path

We traced the encoder's logic:

```c
// encoder.c line ~362
jab_int32 maskCode(jab_encode* enc) {
    jab_int32 best_mask = DEFAULT_MASKING_REFERENCE;  // 7
    jab_float min_penalty = BIG_NUMBER;
    
    for(jab_int32 i = 0; i < 8; i++) {
        // Try each mask pattern
        jab_float penalty = calculatePenalty(enc, i);
        if(penalty < min_penalty) {
            min_penalty = penalty;
            best_mask = i;  // Found better mask
        }
    }
    
    // best_mask now contains 0-7 (the actual best pattern)
    return best_mask;
}
```

So far, so good. The encoder correctly selects the best mask.

### The Metadata Update

Next, we found where the metadata gets written:

```c
// encoder.c line ~906
jab_int32 encodeMasterMetadata(jab_encode* enc) {
    // ... lots of metadata encoding ...
    
    // Encode Part II metadata (includes mask type)
    jab_int32 MSK = DEFAULT_MASKING_REFERENCE;  // ⚠️ Always 7!
    
    // Write MSK to metadata
    convert_dec_to_bin(MSK, metadata_bits, ...);
}
```

**There's the problem!** The encoder initializes MSK to `DEFAULT_MASKING_REFERENCE` (7) and never updates it with the actual mask that was selected.

### The Update Function

We found a function that *should* update the metadata:

```c
// encoder.c line ~1024
jab_int32 updateMasterMetadataPartII(jab_encode* enc, jab_int32 mask_ref) {
    // Re-encode Part II with the ACTUAL mask_ref
    jab_int32 MSK = mask_ref;  // Use the real mask!
    convert_dec_to_bin(MSK, metadata_bits, ...);
}
```

Perfect! This function exists to fix the problem. But is it being called?

### The Smoking Gun

```c
// encoder.c line ~2628 (BEFORE FIX)
if(mask_reference != DEFAULT_MASKING_REFERENCE) {
    if (enc->color_number <= 8) {  // ⚠️ HERE'S THE BUG
        updateMasterMetadataPartII(enc, mask_reference);
        placeMasterMetadataPartII(enc);
    }
}
```

**Found it!**

The encoder only updates the metadata if `color_number <= 8`. For 64-color mode (or 128-color), it *skips* the metadata update entirely, leaving the default value of 7.

---

## The Root Cause

### Why Was This Check Added?

Looking at git history and comments, we found this was a **safety check** added to prevent a different bug:

```c
// CRITICAL: placeMasterMetadataPartII has malloc corruption bug for 256-color mode
// Safe for ≤8 colors originally, needs fixing for higher modes
```

Someone discovered that `placeMasterMetadataPartII()` causes a malloc crash in 256-color mode. As a quick fix, they restricted it to `<= 8` colors.

**The problem:** This safety check was *too broad*. It protected against 256-color malloc crashes, but broke 64-color and 128-color modes by preventing mask metadata updates.

### The Classic Trade-off

This is a textbook example of a defensive fix creating a new bug:

1. **Original problem**: 256-color mode crashes in `placeMasterMetadataPartII()`
2. **Quick fix**: Only call it for `<= 8` colors
3. **Unintended consequence**: 64-color and 128-color mask metadata never gets updated
4. **Result**: LDPC decoding fails for all modes > 8 colors

The fix solved one problem but created another. 😓

---

## The Fix

### Solution 1: Targeted Safety Check

We replaced the broad restriction with a targeted one:

```c
// encoder.c line ~2633 (AFTER FIX)
if(mask_reference != DEFAULT_MASKING_REFERENCE) {
    // CRITICAL: placeMasterMetadataPartII has malloc corruption bug for 256-color mode
    // Safe for ≤128 colors. 256-color needs deeper investigation and fix.
    if (enc->color_number <= 128) {  // ✅ Extended from <= 8
        updateMasterMetadataPartII(enc, mask_reference);
        placeMasterMetadataPartII(enc);
    }
}
```

**Key change:** Extended the check from `<= 8` to `<= 128`.

**Result:**
- 4-color through 128-color: Metadata updates correctly ✅
- 256-color: Still protected from malloc crash ✅
- All working modes: Encoder and decoder use matching mask patterns ✅

### Testing the Fix

```
ColorMode5Test (64-color):  11/11 tests ✅
ColorMode6Test (128-color): 13/13 tests ✅
```

Complete success! Every test that was failing now passes with 100% reliability.

---

## The Deeper Lessons

### Lesson 1: Safety Checks Can Introduce Bugs

The original restriction (`<= 8`) was added as a safety measure, but it was *too restrictive*. When adding safety checks:

1. **Be specific**: Target the exact problem, not a broad category
2. **Document why**: Explain what you're protecting against
3. **Test thoroughly**: Ensure the check doesn't break unrelated functionality
4. **Add TODOs**: Mark temporary fixes for proper resolution later

### Lesson 2: Metadata Synchronization Is Critical

In encoder-decoder systems, any mismatch in critical parameters causes complete failure. Always:

1. **Log both sides**: Add logging to encoder AND decoder for critical values
2. **Verify round-trip**: Test that metadata written by encoder matches what decoder reads
3. **Fail fast**: If metadata looks wrong, fail immediately with clear error
4. **Test all modes**: Don't assume higher modes work just because lower modes do

### Lesson 3: The Debugging Approach

What worked for us:

```
1. Isolate the failure (works at N, fails at N+1)
2. Add comprehensive logging (encoder and decoder)
3. Compare critical parameters (mask_type in this case)
4. Trace the divergence backward (where does mask_type get set?)
5. Find the conditional that prevents correct behavior
6. Understand WHY that conditional exists
7. Fix surgically (extend check to include working modes)
8. Test exhaustively (all modes, all tests)
```

### Lesson 4: Comment Your Workarounds

The code had this comment:

```c
// CRITICAL: placeMasterMetadataPartII has malloc corruption bug for 256-color mode
```

This was **crucial**. It told us:
- Why the restriction existed
- What problem it was solving
- That it was a workaround, not a final solution

Without this comment, we might have removed the check entirely and reintroduced the 256-color crash.

---

## Technical Deep Dive: Mask Pattern Math

For the curious, here's how mask patterns actually work in JABCode.

### The Mask Functions

Each mask pattern is a mathematical function `M(x, y)` that depends on position:

```c
Pattern 0: M(x,y) = (x + y) % 2
Pattern 1: M(x,y) = y % 2
Pattern 2: M(x,y) = x % 3
Pattern 3: M(x,y) = (x + y) % 3
Pattern 4: M(x,y) = ((y/2) + (x/3)) % 2
Pattern 5: M(x,y) = ((x*y) % 2) + ((x*y) % 3)
Pattern 6: M(x,y) = (((x*y) % 2) + ((x*y) % 3)) % 2
Pattern 7: M(x,y) = (((x+y) % 2) + ((x*y) % 3)) % 2
```

### Applying the Mask

For each data module at position (x, y):

```c
// Encoding (encoder applies mask)
encoded_value = original_value XOR M(x,y)

// Decoding (decoder removes mask)
original_value = encoded_value XOR M(x,y)
```

Since `A XOR B XOR B = A`, this recovers the original value.

### Why Different Patterns?

Different patterns break up different problematic data patterns. The encoder tries all 8 and picks the one that:
- Avoids long runs of same color
- Distributes colors evenly
- Minimizes penalty score

### What Went Wrong

With the bug:
- **Encoder**: `encoded = data XOR M₀(x,y)` (using pattern 0)
- **Decoder**: `decoded = encoded XOR M₇(x,y)` (using pattern 7)
- **Result**: `decoded = data XOR M₀(x,y) XOR M₇(x,y)` = garbage

The decoder was applying the wrong inverse operation, scrambling every bit.

---

## Impact Analysis

### Before the Fix

| Mode | Mask Update | Decoder Behavior | Result |
|------|-------------|------------------|--------|
| 4-color | ✅ Updated | Correct mask | ✅ Works |
| 8-color | ✅ Updated | Correct mask | ✅ Works |
| 16-color | ❌ Not updated | Wrong mask (always 7) | ❌ LDPC fails |
| 32-color | ❌ Not updated | Wrong mask (always 7) | ❌ LDPC fails |
| 64-color | ❌ Not updated | Wrong mask (always 7) | ❌ LDPC fails |
| 128-color | ❌ Not updated | Wrong mask (always 7) | ❌ LDPC fails |

Wait, this suggests 16-color and 32-color should also fail! Why didn't they?

**Plot twist:** They DID fail initially. We fixed 16-color and 32-color earlier by addressing a different issue, which inadvertently worked around this one. The full story is even more complex than presented here. 😅

### After the Fix

| Mode | Mask Update | Decoder Behavior | Result |
|------|-------------|------------------|--------|
| All 4-128 | ✅ Updated | Correct mask | ✅ Works perfectly |
| 256-color | 🛡️ Protected | Not reached | ⚠️ Malloc crash prevented |

---

## The 256-Color Question

"Why not fix the malloc crash and enable 256-color?"

**Good question!** Here's the situation:

1. The malloc crash is **deeper** in the encoder initialization
2. It's not just in `placeMasterMetadataPartII()` 
3. Initial investigation suggests it's in palette allocation or matrix setup
4. We fixed the palette allocation (1 palette → 4 palettes), but crash persists
5. Likely needs memory sanitizer analysis to find the real culprit

**Current status:** 
- 256-color is **documented** as broken
- Tests are **excluded** to prevent crashes
- It's on the **roadmap** for proper investigation
- All other modes (4-128) work perfectly

**Priority:** Medium. Why? 256-color is rare in practice. Most real-world use cases work fine with 64 or 128 colors maximum.

---

## Code References

### The Fix Location

**File:** `src/jabcode/encoder.c`  
**Lines:** 2628-2638

```c
if(mask_reference != DEFAULT_MASKING_REFERENCE)
{
    // CRITICAL: placeMasterMetadataPartII has malloc corruption bug for 256-color mode
    // Safe for ≤128 colors. 256-color needs deeper investigation and fix.
    // TODO: Fix placeMasterMetadataPartII malloc corruption for 256-color mode
    if (enc->color_number <= 128) {
        //re-encode PartII of master symbol metadata with actual mask_reference
        updateMasterMetadataPartII(enc, mask_reference);
        //update the masking reference in master symbol metadata
        placeMasterMetadataPartII(enc);
    }
}
```

### Related Functions

**Mask selection:** `src/jabcode/mask.c:362-405` (`maskCode()`)  
**Metadata update:** `src/jabcode/encoder.c:1024-1058` (`updateMasterMetadataPartII()`)  
**Metadata placement:** `src/jabcode/encoder.c:1064-1123` (`placeMasterMetadataPartII()`)  
**Decoder read:** `src/jabcode/decoder.c:1378` (reads mask_type from Part II metadata)

---

## Testing Strategy

### How We Verified the Fix

1. **Unit-level**: Added logging to verify mask_type matches between encoder and decoder
2. **Integration-level**: Full round-trip tests for all color modes
3. **Regression**: Ensured 4-8 color modes still work (didn't break what was working)
4. **Stress testing**: Multiple iterations, various message lengths, different ECC levels

### Test Results

```
ColorMode3Test (16-color):  12/12 tests passing ✅
ColorMode4Test (32-color):  12/12 tests passing ✅
ColorMode5Test (64-color):  11/11 tests passing ✅
ColorMode6Test (128-color): 13/13 tests passing ✅

Total: 48 tests covering the previously broken modes
Success rate: 100%
LDPC failures: 0
```

---

## Conclusion

This bug was a masterclass in unintended consequences:

1. A **real problem** (256-color malloc crash) required a safety check
2. The **safety check** was too broad, breaking working modes
3. The **breakage** was subtle (metadata mismatch) but total (complete decoding failure)
4. The **fix** required understanding both the original problem and the unintended side effect
5. The **solution** was surgical: protect what needs protecting, allow what can work

**Time to find:** ~8 hours of debugging  
**Time to fix:** 5 minutes  
**Time to test:** 2 hours  
**Impact:** Unlocked 64-color and 128-color modes, enabling high-density JABCode usage

**Final thought:** Sometimes the most devastating bugs are the ones introduced by fixes for other bugs. Always test your workarounds thoroughly! 🧪

---

## Further Reading

- **[05-encoder-memory-architecture.md](05-encoder-memory-architecture.md)** - The 256-color malloc mystery
- **[07-test-coverage-journey.md](07-test-coverage-journey.md)** - How we achieved 75% coverage
- **[08-color-mode-reference.md](08-color-mode-reference.md)** - Complete mask pattern specifications

---

*"Debugging is twice as hard as writing the code in the first place. Therefore, if you write the code as cleverly as possible, you are, by definition, not smart enough to debug it."* - Brian Kernighan

We weren't too clever here, just persistent. 🔍✨
