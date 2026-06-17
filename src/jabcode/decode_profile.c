/**
 * libjabcode - JABCode Encoding/Decoding Library
 *
 * Copyright 2016 by Fraunhofer SIT. All rights reserved.
 * See LICENSE file for full terms of use and distribution.
 *
 * @file decode_profile.c
 * @brief Process-global state + public API for opt-in decode stage profiling.
 *
 * See decode_profile.h for the stage list, the timing macros used at each call
 * site, and the design rationale. The setter/getter idiom mirrors
 * jabSetDiagVerbose / g_diag_verbose in decoder.c.
 */

#include <string.h>
#include "decode_profile.h"

/* OFF by default: when 0, every JAB_PROF_BEGIN/END pair short-circuits on a
 * single global read, so an unprofiled decode does no clock work at all and
 * behaves byte-for-byte as before. Process-global (not __thread) to match the
 * g_diag_verbose idiom — a diagnostic/benchmark tool flips it once, decode runs
 * elsewhere; a torn read only mislabels one decode's timing, never affects the
 * decoded bytes. */
unsigned char g_profile_stages = 0;

/* Accumulators. Zero-initialised; jabResetDecodeProfile clears between sweeps.
 * Fields: stage_us[], detect_us[] (DETECT sub-stage breakdown), decode_count. */
jab_decode_profile g_decode_profile = {{0}, {0}, 0};

/**
 * @brief Enable or disable per-stage decode profiling
 * @param profile JAB_SUCCESS (1) to accumulate per-stage timings, 0 to disable
 */
void jabSetProfileStages(jab_boolean profile)
{
	g_profile_stages = profile;
}

/**
 * @brief Query whether per-stage decode profiling is enabled
 * @return the current profiling flag
 */
jab_boolean jabIsProfileStages(void)
{
	return g_profile_stages;
}

/**
 * @brief Read the accumulated per-stage decode timings
 * @return pointer to the process-global profile accumulator (do not free)
 */
const jab_decode_profile* jabGetDecodeProfile(void)
{
	return &g_decode_profile;
}

/**
 * @brief Reset all accumulated per-stage timings and the decode counter to zero
 */
void jabResetDecodeProfile(void)
{
	memset(&g_decode_profile, 0, sizeof(g_decode_profile));
}
