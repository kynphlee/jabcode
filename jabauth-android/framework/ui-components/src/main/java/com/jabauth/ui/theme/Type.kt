package com.jabauth.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * JABAuth design-system typography.
 *
 * Source of truth: DESIGN_SYSTEM.md v1.0.0 (2026-05-02).
 *
 * The design calls for two families:
 *  - Data / display: **IBM Plex Mono** (weights 400/500/600/700)
 *  - Body / UI:       **Archivo**      (weights 400/500/600/700)
 *
 * TODO(fonts): swap [DataFontFamily] / [BodyFontFamily] for the real brand
 * faces via Compose downloadable Google Fonts
 * (androidx.compose.ui:ui-text-google-fonts). That route additionally needs a
 * GoogleFont.Provider backed by the GMS font-certs resource array. It was not
 * wired here because the certs cannot be verified offline and a mis-typed cert
 * fails *silently at runtime* (fonts never download) rather than at compile
 * time — worse than an honest, visible fallback. Until then we use the platform
 * monospace face for data/display (preserving the fixed-width "instrument"
 * feel) and the platform sans for body/UI. Every size, weight, line height and
 * letter-spacing below already matches the spec, so the swap is purely a
 * FontFamily change with no metric churn.
 */
internal val DataFontFamily: FontFamily = FontFamily.Monospace // IBM Plex Mono target
internal val BodyFontFamily: FontFamily = FontFamily.Default   // Archivo target

/** em-based tracking from the spec, expressed in Compose sp letter-spacing. */
private val Tracking002 = (-0.02).em
private val Tracking001 = (-0.01).em
private val Tracking005 = 0.05.em
private val Tracking01 = 0.1.em
private val NoTracking: TextUnit = 0.sp

/**
 * Material 3 [Typography] populated from the JABAuth type scale.
 *
 * Mapping of design roles onto Material slots:
 *  - display{Large,Medium,Small} → IBM Plex Mono display tiers
 *  - headlineLarge               → IBM Plex Mono 18sp
 *  - headline{Medium,Small}      → Archivo UI headings
 *  - body{Large,Medium,Small}    → Archivo body
 *  - label{Large,Medium,Small}   → IBM Plex Mono tracked labels
 *
 * The Material `title*` slots are not part of the JABAuth spec; they are filled
 * with sensible Archivo-based defaults so any stock Material component that
 * reaches for them still renders on-brand.
 */
val JABAuthTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = DataFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 40.sp,
        letterSpacing = Tracking002
    ),
    displayMedium = TextStyle(
        fontFamily = DataFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 32.sp,
        letterSpacing = Tracking002
    ),
    displaySmall = TextStyle(
        fontFamily = DataFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = Tracking001
    ),
    headlineLarge = TextStyle(
        fontFamily = DataFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = Tracking001
    ),
    headlineMedium = TextStyle(
        fontFamily = BodyFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = NoTracking
    ),
    headlineSmall = TextStyle(
        fontFamily = BodyFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = NoTracking
    ),
    // title* are outside the JABAuth spec; provide on-brand Archivo defaults so
    // stock Material components that use these slots still look correct.
    titleLarge = TextStyle(
        fontFamily = BodyFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = NoTracking
    ),
    titleMedium = TextStyle(
        fontFamily = BodyFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = NoTracking
    ),
    titleSmall = TextStyle(
        fontFamily = BodyFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = NoTracking
    ),
    bodyLarge = TextStyle(
        fontFamily = BodyFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 24.sp,
        letterSpacing = NoTracking
    ),
    bodyMedium = TextStyle(
        fontFamily = BodyFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 20.sp,
        letterSpacing = NoTracking
    ),
    bodySmall = TextStyle(
        fontFamily = BodyFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = NoTracking
    ),
    labelLarge = TextStyle(
        fontFamily = DataFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 16.sp,
        letterSpacing = Tracking005
    ),
    labelMedium = TextStyle(
        fontFamily = DataFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = Tracking005
    ),
    labelSmall = TextStyle(
        fontFamily = DataFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 9.sp,
        lineHeight = 12.sp,
        letterSpacing = Tracking01
    )
)
