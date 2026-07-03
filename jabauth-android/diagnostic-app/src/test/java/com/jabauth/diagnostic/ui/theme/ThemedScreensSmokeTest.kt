package com.jabauth.diagnostic.ui.theme

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.jabauth.diagnostic.ui.dashboard.DashboardScreen
import com.jabauth.diagnostic.ui.dashboard.DashboardViewModel
import com.jabauth.diagnostic.ui.errorlog.ErrorLogViewModel
import com.jabauth.diagnostic.ui.errorlog.ErrorLogScreen
import com.jabauth.diagnostic.ui.settings.SettingsScreen
import com.jabauth.diagnostic.ui.settings.SettingsViewModel
import com.jabauth.ui.theme.JABAuthTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression smoke test for the emulator-DS re-skin (Phase 0).
 *
 * Renders the three primary diagnostic-app screens — Dashboard, Error Log and
 * Settings — inside [JABAuthTheme] under Robolectric and asserts each composes
 * and settles without throwing. This is the CI-able guard that the retargeted
 * colour tokens and the swapped font families (JetBrains Mono / IBM Plex Sans)
 * still resolve for the real screens, catching a missing font resource, a
 * dropped colour token, or a broken Material colour-scheme slot.
 *
 * The screens are top-level composables (not Hilt-injected), so each is driven
 * with a hand-constructed ViewModel rather than the `viewModel()` default:
 *  - Dashboard: `DashboardViewModel { emptyList() }` → renders the empty
 *    device-summary + "Cameras" header content, no camera hardware needed.
 *  - Error Log: parameterless `ErrorLogViewModel()` → renders the empty state.
 *  - Settings: `SettingsViewModel(application)` whose `settings` StateFlow has an
 *    `initialValue = Settings()`, so the first frame paints from defaults and
 *    does not block on DataStore.
 *
 * These are render-only assertions (no DataStore round-trip, no interaction);
 * DataStore behaviour is covered elsewhere by the instrumented suite.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ThemedScreensSmokeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val application: Application
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `Dashboard composes under the emulator-DS theme`() {
        composeRule.setContent {
            JABAuthTheme {
                DashboardScreen(
                    viewModel = DashboardViewModel { emptyList() }
                )
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `Error Log composes under the emulator-DS theme`() {
        composeRule.setContent {
            JABAuthTheme {
                ErrorLogScreen(
                    viewModel = ErrorLogViewModel()
                )
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `Settings composes under the emulator-DS theme`() {
        composeRule.setContent {
            JABAuthTheme {
                SettingsScreen(
                    viewModel = SettingsViewModel(application)
                )
            }
        }
        composeRule.waitForIdle()
    }
}
