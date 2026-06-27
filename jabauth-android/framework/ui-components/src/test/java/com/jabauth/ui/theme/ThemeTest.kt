package com.jabauth.ui.theme

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Unit tests for JABAuth Theme
 * 
 * Tests theme colors and typography definitions.
 * Full rendering tests require instrumented testing.
 * Coverage Target: 70%+
 */
class ThemeTest {
    
    // Typography assertions track the JABAuth design-system type scale
    // (DESIGN_SYSTEM.md v1.0.0), not the stock Material 3 scale.
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
}
