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
