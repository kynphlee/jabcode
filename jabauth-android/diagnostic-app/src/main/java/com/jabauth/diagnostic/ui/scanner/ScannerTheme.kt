package com.jabauth.diagnostic.ui.scanner

import com.jabauth.jabcode.DecodeResult
import com.jabauth.ui.theme.JABAuthBgBase
import com.jabauth.ui.theme.JABAuthBgCard
import com.jabauth.ui.theme.JABAuthError
import com.jabauth.ui.theme.JABAuthPrimary
import com.jabauth.ui.theme.JABAuthSuccess
import com.jabauth.ui.theme.JABAuthTextPrimary
import com.jabauth.ui.theme.JABAuthTextSecondary

/**
 * Shared palette, constants and helpers for the scanner screen's composables.
 *
 * The scanner uses a QR-Scanner-style floating-control layout (adopted from the
 * reference videos). Its accent + surface tokens now resolve to the JABAuth
 * design-system palette so the overlay reads on-brand against the rest of the
 * app; the role each colour plays (accent, good/hard workload, surfaces, text)
 * is unchanged — only the literal values moved onto the shared tokens.
 *
 * These are package-internal so every extracted scanner composable shares one
 * definition instead of redeclaring per-file private copies.
 */

/** Scanner accent — reticle, slider, active states (design-system primary). */
internal val Orange = JABAuthPrimary
/** Full-screen camera backdrop. */
internal val ScanBg = JABAuthBgBase
/** Diagnostic-panel surface. */
internal val PanelBg = JABAuthBgCard
/** High-emphasis overlay text. */
internal val TextPrimary = JABAuthTextPrimary
/** Medium-emphasis overlay text. */
internal val TextSecondary = JABAuthTextSecondary
/** Workload/health "good" indication. */
internal val WorkloadGood = JABAuthSuccess
/** Workload/health "hard" indication. */
internal val WorkloadHard = JABAuthError

/** Upper bound for the zoom slider (× magnification). */
internal const val MAX_ZOOM = 8f

/** One-line decoded-payload preview, truncated for chips/badges. */
internal fun decodeSummary(result: DecodeResult): String {
    val s = result.asString()
    return if (s.length > 40) "${s.take(40)}…" else s
}

/** Coaching hint shown after repeated decode failures. */
internal fun failureHint(stats: ScannerViewModel.ScanStats): String {
    return if (stats.successRate < 0.1f)
        "Couldn't decode — zoom in or move closer"
    else
        "Detected but borderline — hold steadier"
}
