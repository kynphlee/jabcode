package com.jabauth.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontListFontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.ResourceFont
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for the JABAuth Theme.
 *
 * Source of truth: JABAuth Emulator Design System ("theme 1b").
 *
 * Covers the design-system colour tokens (retargeted values + the new
 * module-identity and verdict tokens), the two bundled [FontFamily] instances
 * (JetBrains Mono for data, IBM Plex Sans for prose — asserting no silent
 * fallback), and the typography-tier → family mapping.
 *
 * Robolectric-backed so the Compose font resolver and a themed render can run
 * on the JVM (CI-able, no device). Coverage Target: 70%+
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ThemeTest {

    @get:Rule
    val composeRule = createComposeRule()

    // -----------------------------------------------------------------------
    // Typography type-scale (unchanged by the re-skin — regression guard)
    // -----------------------------------------------------------------------

    @Test
    fun `JABAuthTypography has correct display large style`() {
        assertThat(JABAuthTypography.displayLarge.fontSize.value).isEqualTo(36f)
        assertThat(JABAuthTypography.displayLarge.lineHeight.value).isEqualTo(40f)
    }

    @Test
    fun `JABAuthTypography has correct headline large style`() {
        assertThat(JABAuthTypography.headlineLarge.fontSize.value).isEqualTo(18f)
        assertThat(JABAuthTypography.headlineLarge.lineHeight.value).isEqualTo(24f)
    }

    @Test
    fun `JABAuthTypography has correct body large style`() {
        assertThat(JABAuthTypography.bodyLarge.fontSize.value).isEqualTo(15f)
        assertThat(JABAuthTypography.bodyLarge.lineHeight.value).isEqualTo(24f)
    }

    @Test
    fun `JABAuthTypography has correct label small style`() {
        assertThat(JABAuthTypography.labelSmall.fontSize.value).isEqualTo(9f)
        assertThat(JABAuthTypography.labelSmall.lineHeight.value).isEqualTo(12f)
    }

    // -----------------------------------------------------------------------
    // Emulator DS colour tokens — retargeted core values
    // -----------------------------------------------------------------------

    @Test
    fun `background hierarchy resolves to emulator DS values`() {
        assertThat(JABAuthBgBase).isEqualTo(Color(0xFF070910))
        assertThat(JABAuthBgPanel).isEqualTo(Color(0xFF0D0F15))
        assertThat(JABAuthBgCard).isEqualTo(Color(0xFF12151E))
        assertThat(JABAuthBgHover).isEqualTo(Color(0xFF1A1E2A))
        assertThat(JABAuthBgActive).isEqualTo(Color(0xFF1E2438))
        // Elevated is retained as an API-compatible alias of the panel token.
        assertThat(JABAuthBgElevated).isEqualTo(JABAuthBgPanel)
    }

    @Test
    fun `border and text hierarchy resolve to emulator DS values`() {
        assertThat(JABAuthBorder).isEqualTo(Color(0xFF1E2235))
        assertThat(JABAuthTextPrimary).isEqualTo(Color(0xFFDDE3F0))
        assertThat(JABAuthTextSecondary).isEqualTo(Color(0xFF6B7A99))
        assertThat(JABAuthTextDim).isEqualTo(Color(0xFF3D4760))
        assertThat(JABAuthTextCode).isEqualTo(Color(0xFFA8B4D0))
    }

    @Test
    fun `primary accent resolves to emulator DS cyan`() {
        assertThat(JABAuthPrimary).isEqualTo(Color(0xFF00D4FF))
    }

    // -----------------------------------------------------------------------
    // Emulator DS colour tokens — NEW module-identity + verdict tokens
    // -----------------------------------------------------------------------

    @Test
    fun `module-identity tokens resolve to their signature colours`() {
        assertThat(ModPki).isEqualTo(Color(0xFF7C6DF0))
        assertThat(ModJwt).isEqualTo(Color(0xFF00BCD4))
        assertThat(ModAbe).isEqualTo(Color(0xFFFF6B35))
        assertThat(ModJabcode).isEqualTo(Color(0xFF00E676))
    }

    @Test
    fun `verdict tokens resolve to their semantic colours`() {
        assertThat(VerdictVerified).isEqualTo(Color(0xFF00E676))
        assertThat(VerdictUntrusted).isEqualTo(Color(0xFFFFAA00))
        assertThat(VerdictFailed).isEqualTo(Color(0xFFFF006E))
        assertThat(VerdictNeutral).isEqualTo(Color(0xFF00D4FF))
    }

    @Test
    fun `magenta is scarce - only the hard-fail verdict uses it (Principle D)`() {
        // Principle D: magenta (#FF006E) is load-bearing and reserved for the
        // failed verdict / error alarm. It must NOT leak into the positive
        // verdicts or the module-identity palette.
        val magenta = Color(0xFFFF006E)
        assertThat(VerdictFailed).isEqualTo(magenta)
        assertThat(VerdictVerified).isNotEqualTo(magenta)
        assertThat(VerdictUntrusted).isNotEqualTo(magenta)
        assertThat(VerdictNeutral).isNotEqualTo(magenta)
        assertThat(ModPki).isNotEqualTo(magenta)
        assertThat(ModJwt).isNotEqualTo(magenta)
        assertThat(ModAbe).isNotEqualTo(magenta)
        assertThat(ModJabcode).isNotEqualTo(magenta)
    }

    // -----------------------------------------------------------------------
    // Bundled font families — assert the correct faces load (no silent fallback)
    // -----------------------------------------------------------------------
    // A FontFamily built from res/font TTFs is a FontListFontFamily holding one
    // ResourceFont per weight. If a Font(...) referenced a missing resource the
    // module would not compile, so a resource-backed family whose every entry is
    // a ResourceFont with the expected weight + a real (non-zero, distinct)
    // resId is the JVM-checkable proxy for "loads its bundled faces, no default
    // fallback".

    private fun FontFamily.resourceFonts(): List<ResourceFont> {
        val family = this as? FontListFontFamily
            ?: error("expected a resource-backed FontListFontFamily, got $this")
        return family.map { it as ResourceFont }
    }

    @Test
    fun `DataFontFamily bundles JetBrains Mono weights 400 500 600 700`() {
        val fonts = DataFontFamily.resourceFonts()
        assertThat(fonts.map { it.weight }).containsExactly(
            FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold
        )
        // Every weight is backed by a real, distinct resource → no fallback.
        assertThat(fonts.map { it.resId }.toSet()).hasSize(4)
        fonts.forEach { assertThat(it.resId).isNotEqualTo(0) }
    }

    @Test
    fun `BodyFontFamily bundles IBM Plex Sans weights 400 500 600`() {
        val fonts = BodyFontFamily.resourceFonts()
        assertThat(fonts.map { it.weight }).containsExactly(
            FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold
        )
        assertThat(fonts.map { it.resId }.toSet()).hasSize(3)
        fonts.forEach { assertThat(it.resId).isNotEqualTo(0) }
    }

    @Test
    fun `data and body families are distinct - the re-skin uses two families`() {
        assertThat(DataFontFamily).isNotEqualTo(BodyFontFamily)
        // The two families share no backing resource (JetBrains Mono vs IBM Plex Sans).
        val dataIds = DataFontFamily.resourceFonts().map { it.resId }.toSet()
        val bodyIds = BodyFontFamily.resourceFonts().map { it.resId }.toSet()
        assertThat(dataIds.intersect(bodyIds)).isEmpty()
    }

    // -----------------------------------------------------------------------
    // Typography tier → family mapping
    // -----------------------------------------------------------------------

    @Test
    fun `display and label tiers use the data (JetBrains Mono) family`() {
        assertThat(JABAuthTypography.displayLarge.fontFamily).isEqualTo(DataFontFamily)
        assertThat(JABAuthTypography.displayMedium.fontFamily).isEqualTo(DataFontFamily)
        assertThat(JABAuthTypography.displaySmall.fontFamily).isEqualTo(DataFontFamily)
        assertThat(JABAuthTypography.headlineLarge.fontFamily).isEqualTo(DataFontFamily)
        assertThat(JABAuthTypography.labelLarge.fontFamily).isEqualTo(DataFontFamily)
        assertThat(JABAuthTypography.labelMedium.fontFamily).isEqualTo(DataFontFamily)
        assertThat(JABAuthTypography.labelSmall.fontFamily).isEqualTo(DataFontFamily)
    }

    @Test
    fun `body and UI-heading tiers use the prose (IBM Plex Sans) family`() {
        assertThat(JABAuthTypography.bodyLarge.fontFamily).isEqualTo(BodyFontFamily)
        assertThat(JABAuthTypography.bodyMedium.fontFamily).isEqualTo(BodyFontFamily)
        assertThat(JABAuthTypography.bodySmall.fontFamily).isEqualTo(BodyFontFamily)
        assertThat(JABAuthTypography.headlineMedium.fontFamily).isEqualTo(BodyFontFamily)
        assertThat(JABAuthTypography.headlineSmall.fontFamily).isEqualTo(BodyFontFamily)
    }

    // -----------------------------------------------------------------------
    // Material colour-scheme wiring still resolves under the new tokens
    // -----------------------------------------------------------------------

    @Test
    fun `Primary colors are distinct`() {
        assertThat(JABAuthPrimary).isNotEqualTo(JABAuthOnPrimary)
        assertThat(JABAuthPrimary).isNotEqualTo(JABAuthPrimaryContainer)
    }

    @Test
    fun `Success colors are distinct`() {
        assertThat(JABAuthSuccess).isNotEqualTo(JABAuthOnSuccess)
        assertThat(JABAuthSuccess).isNotEqualTo(JABAuthSuccessContainer)
    }

    @Test
    fun `Warning colors are distinct`() {
        assertThat(JABAuthWarning).isNotEqualTo(JABAuthOnWarning)
        assertThat(JABAuthWarning).isNotEqualTo(JABAuthWarningContainer)
    }

    @Test
    fun `Error colors are distinct`() {
        assertThat(JABAuthError).isNotEqualTo(JABAuthOnError)
        assertThat(JABAuthError).isNotEqualTo(JABAuthErrorContainer)
    }

    // -----------------------------------------------------------------------
    // Smoke render — the theme composes and resolves both bundled families
    // -----------------------------------------------------------------------

    @Test
    fun `JABAuthTheme renders themed text using both bundled families without throwing`() {
        composeRule.setContent {
            JABAuthTheme {
                ThemedFontProbe()
            }
        }
        // Compose the tree and settle layout — a font-resolution failure or a
        // broken colour-scheme token would surface here as an exception.
        composeRule.waitForIdle()
    }
}

/** A tiny composable that forces both families to be resolved during layout. */
@Composable
private fun ThemedFontProbe() {
    Text("data 0123", style = MaterialTheme.typography.labelLarge)   // JetBrains Mono
    Text("prose body", style = MaterialTheme.typography.bodyLarge)   // IBM Plex Sans
}
