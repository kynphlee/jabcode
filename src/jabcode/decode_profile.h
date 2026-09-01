/**
 * libjabcode - JABCode Encoding/Decoding Library
 *
 * Copyright (c) 2026 Kendall Fleming. All rights reserved.
 * Added in this fork; NOT part of the Fraunhofer SIT original.
 * See LICENSE file for full terms of use and distribution.
 *
 * @file decode_profile.h
 * @brief Opt-in per-stage decode profiling (CLOCK_MONOTONIC accumulators).
 *
 * Attributes decode latency to the major pipeline stages so we can see WHICH
 * stage dominates at low vs high Nc (number of module colours). Gated behind a
 * process-global flag (default OFF = zero measurable overhead: a single global
 * read short-circuits before any clock_gettime). Mirrors the g_diag_verbose /
 * jabSetDiagVerbose and g_preferred_color_count process-global setter/getter
 * idiom used elsewhere in the decoder.
 *
 * Stages (additive — each is timed exactly where it executes, no subtraction):
 *   DETECT          finder-pattern search + RGB balance/binarize (detector.c)
 *   PALETTE         colour-palette read for a symbol (decoder.c)
 *   COLOR_CLASSIFY  per-module colour classification, decodeModuleHD loop
 *   DEINTERLEAVE    bit de-interleave of the raw symbol data
 *   LDPC            LDPC hard-decision decode of the symbol payload
 *   DATA_DECODE     final byte/mode data decode (decodeData)
 *
 * DETECT sub-stages (detail breakdown of the DETECT roll-up — additive, summing
 * to ~DETECT; timed by JAB_PROF_DET_END, which folds each interval into both the
 * sub-stage AND DETECT in one read, so DETECT == sum(sub-stages) by construction
 * and there is no double counting against the top-level DETECT accumulator):
 *   DETECT_BINARIZE   balanceRGB + binarizerRGB channel balance/thresholding
 *   DETECT_FINDER     finder-pattern search (findMaster/Slave) + AP-seed scans
 *   DETECT_TRANSFORM  calculateSideSize + getPerspectiveTransform (geometry)
 *   DETECT_SAMPLE     sampleSymbol / sampleSymbolByAlignmentPattern (grid resample)
 */

#ifndef JABCODE_DECODE_PROFILE_H
#define JABCODE_DECODE_PROFILE_H

/* clock_gettime / CLOCK_MONOTONIC need _POSIX_C_SOURCE >= 199309L under -std=c11.
 * The library build sets this on the compiler command line (see src/jabcode/
 * Makefile) so it precedes every system header. Defined here too — guarded — so
 * this header stays self-contained for any TU that includes it first. A feature
 * macro is only effective before the first libc header, so a consumer that has
 * already pulled in <time.h> must rely on the command-line definition. */
#ifndef _POSIX_C_SOURCE
#define _POSIX_C_SOURCE 199309L
#endif
#include <time.h>
#include "jabcode.h"

#ifdef __cplusplus
extern "C" {
#endif

/* Pipeline stage indices into jab_decode_profile.stage_us[]. */
typedef enum
{
	JAB_STAGE_DETECT = 0,
	JAB_STAGE_PALETTE,
	JAB_STAGE_COLOR_CLASSIFY,
	JAB_STAGE_DEINTERLEAVE,
	JAB_STAGE_LDPC,
	JAB_STAGE_DATA_DECODE,
	JAB_STAGE_COUNT
} jab_decode_stage;

/* DETECT sub-stage indices into jab_decode_profile.detect_us[]. These break the
 * DETECT roll-up down into its real sub-steps (see header banner). They sum to
 * ~DETECT because JAB_PROF_DET_END folds every interval into both the sub-stage
 * and JAB_STAGE_DETECT. */
typedef enum
{
	JAB_DET_BINARIZE = 0,
	JAB_DET_FINDER,
	JAB_DET_TRANSFORM,
	JAB_DET_SAMPLE,
	JAB_DET_COUNT
} jab_detect_substage;

/* Accumulated microseconds per stage (and per DETECT sub-stage), plus the number
 * of decode calls the accumulation spans (so a caller can derive per-decode
 * averages). Tagged (struct jab_decode_profile) so jabcode.h can forward-declare
 * the getter's return type without pulling in this header. */
typedef struct jab_decode_profile
{
	jab_int64 stage_us[JAB_STAGE_COUNT];
	jab_int64 detect_us[JAB_DET_COUNT];
	jab_int64 decode_count;
} jab_decode_profile;

/* Process-global profiling state (defined in decode_profile.c). The flag is
 * declared with its raw underlying type so the inline helpers below can read it
 * without depending on the jab_boolean typedef ordering. */
extern unsigned char g_profile_stages;
extern jab_decode_profile g_decode_profile;

/* Monotonic clock read in nanoseconds. Only called on the profiling path. */
static inline jab_int64 jab_prof_now_ns(void)
{
	struct timespec ts;
	clock_gettime(CLOCK_MONOTONIC, &ts);
	return (jab_int64)ts.tv_sec * 1000000000LL + (jab_int64)ts.tv_nsec;
}

/* Stage timing: JAB_PROF_BEGIN opens a scope-local start sample; JAB_PROF_END
 * folds the elapsed microseconds into the named stage accumulator. Both are
 * no-ops (zero clock reads) when profiling is OFF — the global read in
 * JAB_PROF_BEGIN guards the clock_gettime, and JAB_PROF_END re-checks so a torn
 * toggle mid-decode cannot fold a bogus interval. The unique scope name lets a
 * single function host several non-overlapping timed stages. */
#define JAB_PROF_BEGIN(scope) \
	jab_int64 _jab_prof_t0_##scope = g_profile_stages ? jab_prof_now_ns() : 0
#define JAB_PROF_END(scope, stage) \
	do { \
		if (g_profile_stages) { \
			g_decode_profile.stage_us[(stage)] += \
				(jab_prof_now_ns() - _jab_prof_t0_##scope) / 1000; \
		} \
	} while (0)

/* DETECT sub-stage timing: pairs with JAB_PROF_BEGIN. JAB_PROF_DET_END folds the
 * elapsed microseconds into BOTH the named DETECT sub-stage AND the JAB_STAGE_DETECT
 * roll-up from a single clock read — so the sub-stages always sum to DETECT, with
 * no double counting (DETECT sites use this macro instead of JAB_PROF_END, never
 * both). Same OFF short-circuit and torn-toggle re-check as JAB_PROF_END. */
#define JAB_PROF_DET_END(scope, substage) \
	do { \
		if (g_profile_stages) { \
			jab_int64 _jab_prof_dt = \
				(jab_prof_now_ns() - _jab_prof_t0_##scope) / 1000; \
			g_decode_profile.detect_us[(substage)] += _jab_prof_dt; \
			g_decode_profile.stage_us[JAB_STAGE_DETECT] += _jab_prof_dt; \
		} \
	} while (0)

#ifdef __cplusplus
}
#endif

#endif /* JABCODE_DECODE_PROFILE_H */
