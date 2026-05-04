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
    
    @Test
    fun `JABAuthTypography has correct display large style`() {
        assertThat(JABAuthTypography.displayLarge.fontSize.value).isEqualTo(57f)
        assertThat(JABAuthTypography.displayLarge.lineHeight.value).isEqualTo(64f)
    }
    
    @Test
    fun `JABAuthTypography has correct headline large style`() {
        assertThat(JABAuthTypography.headlineLarge.fontSize.value).isEqualTo(32f)
        assertThat(JABAuthTypography.headlineLarge.lineHeight.value).isEqualTo(40f)
    }
    
    @Test
    fun `JABAuthTypography has correct body large style`() {
        assertThat(JABAuthTypography.bodyLarge.fontSize.value).isEqualTo(16f)
        assertThat(JABAuthTypography.bodyLarge.lineHeight.value).isEqualTo(24f)
    }
    
    @Test
    fun `JABAuthTypography has correct label small style`() {
        assertThat(JABAuthTypography.labelSmall.fontSize.value).isEqualTo(11f)
        assertThat(JABAuthTypography.labelSmall.lineHeight.value).isEqualTo(16f)
    }
    
    @Test
    fun `Light primary colors are distinct`() {
        assertThat(JABAuthPrimary).isNotEqualTo(JABAuthOnPrimary)
        assertThat(JABAuthPrimary).isNotEqualTo(JABAuthPrimaryContainer)
    }
    
    @Test
    fun `Dark primary colors are distinct`() {
        assertThat(JABAuthPrimaryDark).isNotEqualTo(JABAuthOnPrimaryDark)
        assertThat(JABAuthPrimaryDark).isNotEqualTo(JABAuthPrimaryContainerDark)
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
