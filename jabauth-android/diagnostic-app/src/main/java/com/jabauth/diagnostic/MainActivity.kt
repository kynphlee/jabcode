package com.jabauth.diagnostic

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.lifecycle.lifecycleScope
import com.jabauth.diagnostic.data.SettingsRepository
import com.jabauth.jabcode.setDiagVerbose
import com.jabauth.jabcode.setPermissiveColorClassification
import com.jabauth.jabcode.setPreferredColorCount
import kotlinx.coroutines.launch

/**
 * Main activity for diagnostic app
 *
 * Hosts the navigation graph and bottom navigation bar.
 *
 * Routes the existing Settings "Enable verbose logging for troubleshooting"
 * toggle (Settings.debugLogging) through to the native decoder's
 * g_diag_verbose thread-local, so a single Settings switch controls BOTH
 * Kotlin-side DiagnosticLogger output AND the C-side [PartI_DIAG] markers
 * added in feat(decoder) for the H_partI_unifies investigation. Synced on
 * every emission of settingsFlow so toggling in Settings propagates
 * immediately without a process restart.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i("DiagPropProbe", "[A] MainActivity.onCreate START")
        lifecycleScope.launch {
            Log.i("DiagPropProbe", "[B] lifecycleScope.launch coroutine started")
            SettingsRepository(applicationContext).settingsFlow.collect { settings ->
                Log.i("DiagPropProbe", "[C] settingsFlow emitted: debugLogging=${settings.debugLogging}")
                setDiagVerbose(settings.debugLogging)
                // Path β: bind permissive-color-classification to the same
                // diagnostic toggle for now. Production SDK consumers will
                // get their own opt-in API once empirical validation is done.
                setPermissiveColorClassification(settings.debugLogging)
                // Path β preferred-color-count: route the Settings UI
                // "Preferred Color Mode" dropdown through to the C decoder.
                // Null (Auto-detect) → 0 (auto fallback ladder). Closes the
                // wiring gap surfaced by Bayesian Council bc-2026-05-31-04.
                setPreferredColorCount(settings.preferredColorMode)
                Log.i("DiagPropProbe", "[F] setDiagVerbose + setPermissive + setPreferredColorCount returned (caller side)")
            }
        }
        setContent {
            MaterialTheme {
                DiagnosticApp()
            }
        }
    }
}
