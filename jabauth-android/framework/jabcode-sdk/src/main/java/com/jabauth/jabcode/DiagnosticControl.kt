package com.jabauth.jabcode

import android.util.Log
import com.jabcode.JABCodeMobile

/**
 * Public façade for the native decoder's verbose diagnostic markers.
 *
 * Routes to JABCodeMobile.nativeSetDiagVerbose, which sets the C-side
 * g_diag_verbose thread-local flag. When TRUE, the decoder and detector
 * emit per-iteration markers — [PartI_DIAG] (from PR #32 instrumentation),
 * DIAG_PALETTE_LEARNED, DIAG_PARTII_RESULT, Nc_FALLBACK retries, GRID_REF,
 * etc. — that are useful for forensic investigation but cost ~50–300 µs
 * each on the camera hot path. When FALSE (default), only terminal markers
 * (FAIL_ATTR, DECODE_OK, DIAG_SYMBOL_DECODE final) fire and the decode
 * budget is preserved.
 *
 * Wrapped at the public-package layer because JABCodeMobile is
 * module-internal — consumers of the jabcode-sdk module would otherwise
 * be unable to toggle this from outside the SDK. Diagnostic apps call
 * this immediately before a capture window and disable it afterward;
 * production SDK consumers leave it at the default.
 *
 * Thread-safe: the underlying g_diag_verbose flag is __thread, so
 * concurrent decoders maintain independent verbosity.
 */
private val mobile by lazy {
    Log.i("DiagPropProbe", "[D] JABCodeMobile lazy-init firing (loadLibrary)")
    JABCodeMobile()
}

fun setDiagVerbose(verbose: Boolean) {
    Log.i("DiagPropProbe", "[E1] setDiagVerbose($verbose) entered; about to call nativeSetDiagVerbose")
    mobile.nativeSetDiagVerbose(verbose)
    Log.i("DiagPropProbe", "[E2] nativeSetDiagVerbose($verbose) returned without exception")
}

/**
 * Path β permissive color classification public facade.
 *
 * Toggles the decoder's master-metadata Part I rgb=5 → rgb=6 substitution.
 * Compensates for camera green-channel under-capture (the residual nc2
 * failure mechanism after the AWB/AE convergence-lock in PR #36).
 *
 * @see com.jabcode.JABCodeMobile.nativeSetPermissiveColorClassification
 */
fun setPermissiveColorClassification(permissive: Boolean) {
    Log.i("DiagPropProbe", "[E1-β] setPermissiveColorClassification($permissive) entered")
    mobile.nativeSetPermissiveColorClassification(permissive)
    Log.i("DiagPropProbe", "[E2-β] nativeSetPermissiveColorClassification($permissive) returned")
}

/**
 * Path β preferred color count public facade.
 *
 * Pins the decoder to a specific Nc (color count) by routing through
 * JABCodeMobile.nativeSetPreferredColorCount to the C-side
 * g_preferred_color_count global. When non-null, the decoder collapses
 * the 8-iteration Nc fallback ladder to a single deterministic attempt
 * at the chosen Nc. When null, restores auto-detect mode (the original
 * full fallback walk).
 *
 * Closes the Settings UI dropdown wiring gap exposed by Bayesian Council
 * Session bc-2026-05-31-04 — the "Preferred Color Mode" dropdown was
 * previously cosmetic for decoder steering. This facade makes it
 * functional: one Settings flip is now equivalent to a deterministic
 * Nc-specific scan, invaluable as a discriminator for the
 * H_partI_nc_extraction_bias and H_nc2_decode_failure investigations.
 *
 * Valid count values: 2, 4, 8, 16, 32, 64, 128, 256. Other values (or
 * null) restore auto-detect mode. The C layer treats unknown counts as
 * no-op for safety.
 *
 * @param count desired palette size, or null for auto-detect
 * @see com.jabcode.JABCodeMobile.nativeSetPreferredColorCount
 */
fun setPreferredColorCount(count: Int?) {
    val nativeCount = count ?: 0
    Log.i("DiagPropProbe", "[E1-γ] setPreferredColorCount($count) entered → native=$nativeCount")
    mobile.nativeSetPreferredColorCount(nativeCount)
    Log.i("DiagPropProbe", "[E2-γ] nativeSetPreferredColorCount($nativeCount) returned")
}
