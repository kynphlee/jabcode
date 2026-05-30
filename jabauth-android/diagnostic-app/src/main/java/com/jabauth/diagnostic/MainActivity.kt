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

                /* Path β DECOUPLED 2026-05-30: previously tied to the
                 * debugLogging Settings toggle. The 2026-05-30 14:44:13
                 * trace (post-manual-WB-override PR #41) showed Path β
                 * actively harmful when the AWB cast is gone: the rgb=5
                 * (Y) → rgb=6 (M) remap was firing 164 times across 80
                 * PartI attempts and producing 38 pair_bits failures
                 * (FAIL_STAGE=pair_bits bits[0]=8 bits[1]=8) from the
                 * (M, M) invalid metadata pair.
                 *
                 * Hardcoding to FALSE here lets the diagnostic-app run
                 * with verbose markers ON (Settings → Debug Logging) AND
                 * permissive remap OFF, isolating the manual-WB-override
                 * effect from the Path β contamination. Expected outcome
                 * on the next nc2 trace: PartI success rate rises from
                 * 33.75% toward the 50-70% theoretical ceiling as the 38
                 * pair_bits failures convert to successes.
                 *
                 * Permissive remap is preserved in the C decoder (PR #38)
                 * for opt-in SDK consumer use; just not auto-enabled here.
                 * Production SDK customers who want it call
                 * `setPermissiveColorClassification(true)` explicitly
                 * after evaluating their own decoder-vs-camera-stack
                 * tradeoffs. */
                setPermissiveColorClassification(false)

                Log.i("DiagPropProbe", "[F] setDiagVerbose (β decoupled, hardcoded false) returned")
            }
        }
        setContent {
            MaterialTheme {
                DiagnosticApp()
            }
        }
    }
}
