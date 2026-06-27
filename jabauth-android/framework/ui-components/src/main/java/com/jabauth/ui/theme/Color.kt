package com.jabauth.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * JABAuth design-system colour palette.
 *
 * Source of truth: DESIGN_SYSTEM.md v1.0.0 (2026-05-02). The design system is a
 * dark, high-contrast "instrument panel" aesthetic — neon accents on stepped
 * navy surfaces. The Material 3 [Theme] maps a subset of these tokens onto the
 * Material colour-scheme slots; the remaining tokens (backgrounds, text
 * hierarchy, borders) are consumed directly by design-system composables.
 */

// ---------------------------------------------------------------------------
// Primary & status colours
// ---------------------------------------------------------------------------

/** Primary (Cyan) — active states, primary actions. */
val JABAuthPrimary = Color(0xFF00D9FF)
/** Primary @ 0.2 alpha — subtle fills / badge backgrounds. */
val JABAuthPrimaryDim = JABAuthPrimary.copy(alpha = 0.2f)

/** Success (Neon Green) — valid states, passed checks. */
val JABAuthSuccess = Color(0xFF39FF14)
/** Success @ 0.2 alpha. */
val JABAuthSuccessDim = JABAuthSuccess.copy(alpha = 0.2f)

/** Warning (Amber) — alerts, performance issues. */
val JABAuthWarning = Color(0xFFFFB800)
/** Warning @ 0.2 alpha. */
val JABAuthWarningDim = JABAuthWarning.copy(alpha = 0.2f)

/** Error (Hot Magenta) — failures, invalid states. */
val JABAuthError = Color(0xFFFF006E)
/** Error @ 0.2 alpha. */
val JABAuthErrorDim = JABAuthError.copy(alpha = 0.2f)

// ---------------------------------------------------------------------------
// Background hierarchy (stepped surfaces)
// ---------------------------------------------------------------------------

/** Base — canvas background. */
val JABAuthBgBase = Color(0xFF0B1120)
/** Elevated — raised surfaces, modals. */
val JABAuthBgElevated = Color(0xFF131B2E)
/** Card — primary card background. */
val JABAuthBgCard = Color(0xFF1A2438)
/** Hover — interactive hover states. */
val JABAuthBgHover = Color(0xFF212D44)

// ---------------------------------------------------------------------------
// Text hierarchy
// ---------------------------------------------------------------------------

/** High-emphasis text. */
val JABAuthTextPrimary = Color(0xFFE8F4F8)
/** Medium-emphasis text. */
val JABAuthTextSecondary = Color(0xFF8B9DB0)
/** Metadata / hints. */
val JABAuthTextDim = Color(0xFF5A6B7D)

// ---------------------------------------------------------------------------
// Borders & dividers
// ---------------------------------------------------------------------------

/** Border @ 0.15 alpha. */
val JABAuthBorder = Color(0xFF8B9DB0).copy(alpha = 0.15f)
/** Grid lines @ 0.05 alpha. */
val JABAuthGrid = Color(0xFF8B9DB0).copy(alpha = 0.05f)

// ---------------------------------------------------------------------------
// Material 3 colour-scheme support tokens
// ---------------------------------------------------------------------------
// "On" colours and container variants the Material colour scheme requires.
// Container tokens reuse the dim accent fills; their matching "on" colours are
// the accent itself so text/icons on a dim fill stay legible and on-brand.

/** Foreground on [JABAuthPrimary] surfaces (dark navy for contrast on cyan). */
val JABAuthOnPrimary = Color(0xFF00131A)
val JABAuthPrimaryContainer = JABAuthPrimaryDim
val JABAuthOnPrimaryContainer = JABAuthPrimary

/** Foreground on [JABAuthSuccess] surfaces. */
val JABAuthOnSuccess = Color(0xFF00200A)
val JABAuthSuccessContainer = JABAuthSuccessDim
val JABAuthOnSuccessContainer = JABAuthSuccess

/** Foreground on [JABAuthWarning] surfaces. */
val JABAuthOnWarning = Color(0xFF1F1500)
val JABAuthWarningContainer = JABAuthWarningDim
val JABAuthOnWarningContainer = JABAuthWarning

/** Foreground on [JABAuthError] surfaces. */
val JABAuthOnError = Color(0xFF2A0014)
val JABAuthErrorContainer = JABAuthErrorDim
val JABAuthOnErrorContainer = JABAuthError

/** Foreground on base/surface backgrounds. */
val JABAuthOnBackground = JABAuthTextPrimary
val JABAuthOnSurface = JABAuthTextPrimary
/** Foreground on [JABAuthBgElevated] surface-variant. */
val JABAuthOnSurfaceVariant = JABAuthTextSecondary
