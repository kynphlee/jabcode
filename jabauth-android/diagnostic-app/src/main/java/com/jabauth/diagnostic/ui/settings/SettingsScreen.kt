package com.jabauth.diagnostic.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.clickable
import com.jabauth.ui.components.JABAuthCard
import com.jabauth.ui.theme.JABAuthBgBase
import com.jabauth.ui.theme.ModAbe
import com.jabauth.ui.theme.VerdictFailed
import com.jabauth.ui.theme.VerdictUntrusted
import com.jabauth.ui.theme.JABAuthBgElevated
import com.jabauth.ui.theme.JABAuthBorder
import com.jabauth.ui.theme.JABAuthPrimary
import com.jabauth.ui.theme.JABAuthTextPrimary
import com.jabauth.ui.theme.JABAuthTextSecondary
import com.jabauth.ui.theme.Spacing
import kotlinx.coroutines.delay

/**
 * Settings screen - App configuration
 *
 * Provides configuration options for:
 * - Decoder settings (timeout, intervals)
 * - Camera settings (auto-focus)
 * - Debug options (logging)
 * - JABCode preferences (color mode)
 *
 * Styled with the JABAuth design system (DESIGN_SYSTEM.md v1.0.0): stepped navy
 * surfaces via [JABAuthCard], neon-accent controls, and a staggered card
 * entrance per the motion spec.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel()
) {
    val settings by viewModel.settings.collectAsState()
    val anchorCount by viewModel.anchorCount.collectAsState()
    val importMessage by viewModel.importMessage.collectAsState()
    val context = LocalContext.current
    val certPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.importTrustAnchor(it) }
    }
    LaunchedEffect(importMessage) {
        importMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearImportMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                actions = {
                    TextButton(onClick = { viewModel.resetToDefaults() }) {
                        Text("Reset", color = JABAuthPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = JABAuthBgBase,
                    titleContentColor = JABAuthTextPrimary,
                    actionIconContentColor = JABAuthPrimary
                )
            )
        },
        containerColor = JABAuthBgBase,
        modifier = modifier
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            // Each section reveals with a staggered upward-slide + fade. The index
            // counter increments per rendered section so they cascade top -> down.
            var sectionIndex = 0

            // Verification (Flow C · design) — the on-device trust configuration.
            StaggeredReveal(index = sectionIndex++) {
                SettingsSection(title = "Verification") {
                    VerificationNavRow(
                        label = "Trust Store",
                        value = "$anchorCount ${if (anchorCount == 1) "anchor" else "anchors"} · import",
                        onClick = { certPicker.launch("*/*") }
                    )
                    SwitchSetting(
                        label = "Revocation Check",
                        description = "Check OCSP/CRL when the network is reachable",
                        checked = settings.revocationCheck,
                        onCheckedChange = { viewModel.updateRevocationCheck(it) }
                    )
                    VerificationSegmented(
                        label = "Offline behavior",
                        warnSelected = !settings.offlineHardFail,
                        onWarnSelected = { warn -> viewModel.updateOfflineHardFail(!warn) }
                    )
                    VerificationProfileRow(
                        selected = settings.defaultCoaProfile,
                        onSelect = { viewModel.updateDefaultCoaProfile(it) }
                    )
                    VerificationAttributes(attrs = settings.verifierAttributes)
                }
            }

            // Decoder Settings
            StaggeredReveal(index = sectionIndex++) {
                SettingsSection(title = "Decoder Settings") {
                    SliderSetting(
                        label = "Decode Timeout",
                        value = settings.decodeTimeout.toLong(),
                        valueRange = 100f..1000f,
                        steps = 8,
                        onValueChange = { viewModel.updateDecodeTimeout(it.toInt()) },
                        valueDisplay = { "${it.toInt()}ms" }
                    )

                    SliderSetting(
                        label = "Analyze Interval",
                        value = settings.analyzeInterval.toLong(),
                        valueRange = 100f..2000f,
                        steps = 18,
                        onValueChange = { viewModel.updateAnalyzeInterval(it.toInt()) },
                        valueDisplay = { "${it.toInt()}ms" }
                    )
                }
            }

            // Camera Settings
            StaggeredReveal(index = sectionIndex++) {
                SettingsSection(title = "Camera Settings") {
                    SwitchSetting(
                        label = "Auto Focus",
                        description = "Enable continuous auto-focus during capture",
                        checked = settings.autoFocus,
                        onCheckedChange = { viewModel.updateAutoFocus(it) }
                    )
                }
            }

            // Debug Options
            StaggeredReveal(index = sectionIndex++) {
                SettingsSection(title = "Debug Options") {
                    SwitchSetting(
                        label = "Debug Logging",
                        description = "Enable verbose logging for troubleshooting",
                        checked = settings.debugLogging,
                        onCheckedChange = { viewModel.updateDebugLogging(it) }
                    )
                }
            }

            // Feedback
            StaggeredReveal(index = sectionIndex++) {
                SettingsSection(title = "Feedback") {
                    SwitchSetting(
                        label = "Haptic Feedback",
                        description = "Vibrate briefly on each successful decode",
                        checked = settings.hapticFeedback,
                        onCheckedChange = { viewModel.updateHapticFeedback(it) }
                    )
                }
            }

            // Motion
            StaggeredReveal(index = sectionIndex++) {
                SettingsSection(title = "Motion") {
                    SwitchSetting(
                        label = "Motion Telemetry",
                        description = "Log accelerometer + gyroscope magnitudes per decode (SENSOR_SNAPSHOT)",
                        checked = settings.motionTelemetryEnabled,
                        onCheckedChange = { viewModel.updateMotionTelemetry(it) }
                    )
                    SwitchSetting(
                        label = "Motion Throttling",
                        description = "Skip decode attempts during motion above stability threshold",
                        checked = settings.motionThrottlingEnabled,
                        onCheckedChange = { viewModel.updateMotionThrottling(it) }
                    )
                }
            }

            // JABCode Preferences
            StaggeredReveal(index = sectionIndex++) {
                SettingsSection(title = "JABCode Preferences") {
                    DropdownSetting(
                        label = "Preferred Color Mode",
                        value = settings.preferredColorMode,
                        options = listOf(
                            null to "Auto-detect",
                            2 to "2 colors",
                            4 to "4 colors",
                            8 to "8 colors",
                            16 to "16 colors",
                            32 to "32 colors",
                            64 to "64 colors",
                            128 to "128 colors",
                            256 to "256 colors"
                        ),
                        onValueChange = { viewModel.updateColorMode(it) },
                        displayValue = { mode ->
                            when (mode) {
                                null -> "Auto-detect"
                                else -> "$mode colors"
                            }
                        }
                    )
                }
            }

            // About Section (last — no further increment needed)
            StaggeredReveal(index = sectionIndex) {
                SettingsSection(title = "About") {
                    InfoRow(label = "Version", value = "1.0.0")
                    InfoRow(label = "Build", value = "DEBUG")
                    InfoRow(label = "Framework", value = "JABCode SDK")
                }
            }
        }
    }
}

/**
 * Staggered card-entrance wrapper (DESIGN_SYSTEM.md v1.0.0 motion spec).
 *
 * Each item fades in and slides 24dp upward over 600ms with the Decelerate
 * easing, delayed 100ms per [index] so a column of sections cascades into view.
 * Mechanical, not bouncy — the spec explicitly forbids springy motion.
 */
@Composable
private fun StaggeredReveal(
    index: Int,
    content: @Composable () -> Unit
) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(index * StaggerDelayPerIndexMs)
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(
            animationSpec = tween(
                durationMillis = EntranceDurationMs,
                easing = DecelerateEasing
            )
        ) + slideInVertically(
            initialOffsetY = { 24 },
            animationSpec = tween(
                durationMillis = EntranceDurationMs,
                easing = DecelerateEasing
            )
        )
    ) {
        content()
    }
}

/** Per-index entrance delay (ms) — DESIGN_SYSTEM.md motion spec. */
private const val StaggerDelayPerIndexMs = 100L
/** Entrance animation duration (ms) — AnimationDuration.Slow. */
private const val EntranceDurationMs = 600
/** Enter-screen easing curve — AnimationEasing.Decelerate (0.0, 0.0, 0.2, 1.0). */
private val DecelerateEasing = CubicBezierEasing(0.0f, 0.0f, 0.2f, 1f)

@Composable
private fun SettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = JABAuthPrimary
        )

        JABAuthCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                content()
            }
        }
    }
}

@Composable
private fun SliderSetting(
    label: String,
    value: Long,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
    valueDisplay: (Float) -> String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = JABAuthTextPrimary
            )
            // Metric value rendered in the tracked mono label face for the
            // "instrument readout" feel.
            Text(
                text = valueDisplay(value.toFloat()),
                style = MaterialTheme.typography.labelLarge,
                color = JABAuthPrimary
            )
        }

        Slider(
            value = value.toFloat(),
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = JABAuthPrimary,
                activeTrackColor = JABAuthPrimary,
                activeTickColor = JABAuthBgBase,
                inactiveTrackColor = JABAuthTextSecondary.copy(alpha = 0.3f),
                inactiveTickColor = JABAuthTextSecondary.copy(alpha = 0.5f)
            )
        )
    }
}

@Composable
private fun SwitchSetting(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Spacing.xxs)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.headlineSmall,
                color = JABAuthTextPrimary
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = JABAuthTextSecondary
            )
        }

        Spacer(modifier = Modifier.width(Spacing.md))

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = JABAuthBgBase,
                checkedTrackColor = JABAuthPrimary,
                checkedBorderColor = JABAuthPrimary,
                uncheckedThumbColor = JABAuthTextSecondary,
                uncheckedTrackColor = JABAuthBgBase,
                uncheckedBorderColor = JABAuthTextSecondary
            )
        )
    }
}

@Composable
private fun VerificationNavRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Text(label, style = MaterialTheme.typography.headlineSmall, color = JABAuthTextPrimary)
            Text(value, style = MaterialTheme.typography.bodySmall, color = JABAuthTextSecondary)
        }
        Text("›", style = MaterialTheme.typography.headlineSmall, color = JABAuthTextSecondary)
    }
}

@Composable
private fun VerificationSegmented(label: String, warnSelected: Boolean, onWarnSelected: (Boolean) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = JABAuthTextSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Text(
                "Warn", style = MaterialTheme.typography.headlineSmall,
                color = if (warnSelected) VerdictUntrusted else JABAuthTextSecondary,
                modifier = Modifier.clickable { onWarnSelected(true) }
            )
            Text(
                "Hard-fail", style = MaterialTheme.typography.headlineSmall,
                color = if (!warnSelected) VerdictFailed else JABAuthTextSecondary,
                modifier = Modifier.clickable { onWarnSelected(false) }
            )
        }
    }
}

@Composable
private fun VerificationProfileRow(selected: String, onSelect: (String) -> Unit) {
    val profiles = listOf("FIELD", "FIELD_HOSTILE", "CONTROLLED", "SERVER")
    Row(
        modifier = Modifier.fillMaxWidth().clickable {
            onSelect(profiles[(profiles.indexOf(selected) + 1).mod(profiles.size)])
        },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Default profile", style = MaterialTheme.typography.headlineSmall, color = JABAuthTextPrimary)
        Text("$selected  ▾", style = MaterialTheme.typography.bodyMedium, color = JABAuthPrimary)
    }
}

@Composable
private fun VerificationAttributes(attrs: Set<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        Text("Verifier attributes", style = MaterialTheme.typography.headlineSmall, color = JABAuthTextPrimary)
        Text("identity used for ABE evaluation", style = MaterialTheme.typography.bodySmall, color = JABAuthTextSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            attrs.forEach { a -> Text(a, style = MaterialTheme.typography.bodySmall, color = ModAbe) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownSetting(
    label: String,
    value: Int?,
    options: List<Pair<Int?, String>>,
    onValueChange: (Int?) -> Unit,
    displayValue: (Int?) -> String,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = JABAuthTextPrimary
        )

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
            OutlinedTextField(
                value = displayValue(value),
                onValueChange = {},
                readOnly = true,
                textStyle = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = JABAuthTextPrimary,
                    unfocusedTextColor = JABAuthTextPrimary,
                    focusedBorderColor = JABAuthPrimary,
                    unfocusedBorderColor = JABAuthBorder,
                    focusedTrailingIconColor = JABAuthPrimary,
                    unfocusedTrailingIconColor = JABAuthTextSecondary,
                    focusedContainerColor = JABAuthBgElevated,
                    unfocusedContainerColor = JABAuthBgElevated
                )
            )

            // NOTE: material3 1.1.2's ExposedDropdownMenu has no containerColor
            // param; the menu surface follows the theme `surface` token (navy).
            // We theme the item text directly for on-brand contrast.
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEach { (optionValue, optionLabel) ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = optionLabel,
                                color = if (optionValue == value) JABAuthPrimary else JABAuthTextPrimary
                            )
                        },
                        onClick = {
                            onValueChange(optionValue)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = JABAuthTextSecondary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            color = JABAuthTextPrimary
        )
    }
}
