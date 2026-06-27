package com.jabauth.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * JABAuth spacing scale (4dp base unit).
 *
 * Source of truth: DESIGN_SYSTEM.md v1.0.0 (2026-05-02). Use these tokens for
 * all padding, gaps and margins instead of literal dp values so spacing stays
 * consistent across the design system.
 *
 * Usage: `Modifier.padding(Spacing.md)`.
 */
object Spacing {
    /** 2dp — rare tight spacing. */
    val xxxs: Dp = 2.dp
    /** 4dp — icon-text gaps, minimal. */
    val xxs: Dp = 4.dp
    /** 8dp — default element gaps, chips. */
    val xs: Dp = 8.dp
    /** 12dp — comfortable card spacing. */
    val sm: Dp = 12.dp
    /** 16dp — card padding, sections. */
    val md: Dp = 16.dp
    /** 24dp — section spacing. */
    val lg: Dp = 24.dp
    /** 32dp — large spacing, page margins. */
    val xl: Dp = 32.dp
    /** 48dp — extra large spacing. */
    val xxl: Dp = 48.dp
    /** 64dp — huge spacing (rare). */
    val xxxl: Dp = 64.dp
}
