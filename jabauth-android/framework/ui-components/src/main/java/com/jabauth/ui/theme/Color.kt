package com.jabauth.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * JABAuth design-system colour palette.
 *
 * Source of truth: **JABAuth Emulator Design System ("theme 1b")**. The design
 * system is a dark, high-contrast "instrument panel" aesthetic — restrained
 * accents on deep, near-black stepped surfaces. The Material 3 [Theme] maps a
 * subset of these tokens onto the Material colour-scheme slots; the remaining
 * tokens (backgrounds, text hierarchy, borders, module identity, verdicts) are
 * consumed directly by design-system composables.
 *
 * The token *names* are stable — they are retargeted from the earlier in-app
 * palette (base #0B1120 / cyan #00D9FF / green #39FF14) to the emulator DS
 * values below, so every existing call site re-skins without a rename.
 */

// ---------------------------------------------------------------------------
// Primary & status colours
// ---------------------------------------------------------------------------

/** Primary (Cyan) — active states, primary actions. */
val JABAuthPrimary = Color(0xFF00D4FF)
/** Primary @ 0.2 alpha — subtle fills / badge backgrounds. */
val JABAuthPrimaryDim = JABAuthPrimary.copy(alpha = 0.2f)

/** Success (Green) — valid states, passed checks. */
val JABAuthSuccess = Color(0xFF00E676)
/** Success @ 0.2 alpha. */
val JABAuthSuccessDim = JABAuthSuccess.copy(alpha = 0.2f)

/** Warning (Amber) — alerts, performance issues. */
val JABAuthWarning = Color(0xFFFFAA00)
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
val JABAuthBgBase = Color(0xFF070910)
/** Panel — panel / rail surfaces. */
val JABAuthBgPanel = Color(0xFF0D0F15)
/** Elevated — raised surfaces, modals (alias of panel for API compatibility). */
val JABAuthBgElevated = JABAuthBgPanel
/** Card — primary card background. */
val JABAuthBgCard = Color(0xFF12151E)
/** Hover — interactive hover states. */
val JABAuthBgHover = Color(0xFF1A1E2A)
/** Active — pressed / active interactive states. */
val JABAuthBgActive = Color(0xFF1E2438)

// ---------------------------------------------------------------------------
// Text hierarchy
// ---------------------------------------------------------------------------

/** High-emphasis text. */
val JABAuthTextPrimary = Color(0xFFDDE3F0)
/** Medium-emphasis text. */
val JABAuthTextSecondary = Color(0xFF6B7A99)
/** Metadata / hints (muted). */
val JABAuthTextDim = Color(0xFF3D4760)
/** Inline code / monospace data text. */
val JABAuthTextCode = Color(0xFFA8B4D0)

// ---------------------------------------------------------------------------
// Borders & dividers
// ---------------------------------------------------------------------------

/** Border — panel / card outlines. */
val JABAuthBorder = Color(0xFF1E2235)
/** Grid lines @ 0.05 alpha (derived from the border hue). */
val JABAuthGrid = Color(0xFF1E2235).copy(alpha = 0.05f)

// ---------------------------------------------------------------------------
// Module-identity tokens
// ---------------------------------------------------------------------------
// One signature colour per framework module so a verification step can be
// attributed at a glance (PKI / JWT / ABE / JABCode).

/** PKI / certificate-chain module identity (violet). */
val ModPki = Color(0xFF7C6DF0)
/** JWT module identity (cyan-teal). */
val ModJwt = Color(0xFF00BCD4)
/** ABE / attribute-based-encryption module identity (orange). */
val ModAbe = Color(0xFFFF6B35)
/** JABCode codec module identity (green). */
val ModJabcode = Color(0xFF00E676)

// ---------------------------------------------------------------------------
// Verdict semantic tokens
// ---------------------------------------------------------------------------
// Principle D — magenta is scarce and load-bearing: it is reserved exclusively
// for the hard-fail alarm ([VerdictFailed]) so a failed verdict is unmistakable
// and never diluted by decorative use elsewhere in the palette.

/** Verified — verification passed authoritatively (green). */
val VerdictVerified = Color(0xFF00E676)
/** Untrusted — captured but not authoritatively trusted / needs review (amber). */
val VerdictUntrusted = Color(0xFFFFAA00)
/** Failed — hard verification failure (magenta — the deliberately-scarce alarm). */
val VerdictFailed = Color(0xFFFF006E)
/** Neutral — informational / in-progress, no verdict yet (cyan). */
val VerdictNeutral = Color(0xFF00D4FF)

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
