package com.jabauth.diagnostic.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Settings screen - App configuration
 * 
 * Provides configuration options for:
 * - Decoder settings (timeout, intervals)
 * - Camera settings (auto-focus)
 * - Debug options (logging)
 * - JABCode preferences (color mode)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel()
) {
    val settings by viewModel.settings.collectAsState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                actions = {
                    TextButton(onClick = { viewModel.resetToDefaults() }) {
                        Text("Reset")
                    }
                }
            )
        },
        modifier = modifier
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Decoder Settings
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
            
            // Camera Settings
            SettingsSection(title = "Camera Settings") {
                SwitchSetting(
                    label = "Auto Focus",
                    description = "Enable continuous auto-focus during capture",
                    checked = settings.autoFocus,
                    onCheckedChange = { viewModel.updateAutoFocus(it) }
                )
            }
            
            // Debug Options
            SettingsSection(title = "Debug Options") {
                SwitchSetting(
                    label = "Debug Logging",
                    description = "Enable verbose logging for troubleshooting",
                    checked = settings.debugLogging,
                    onCheckedChange = { viewModel.updateDebugLogging(it) }
                )
            }
            
            // JABCode Preferences
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
            
            // About Section
            SettingsSection(title = "About") {
                InfoRow(label = "Version", value = "1.0.0")
                InfoRow(label = "Build", value = "DEBUG")
                InfoRow(label = "Framework", value = "JABCode SDK")
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
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
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = valueDisplay(value.toFloat()),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
        }
        
        Slider(
            value = value.toFloat(),
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps
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
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
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
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium
        )
        
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
            OutlinedTextField(
                value = displayValue(value),
                onValueChange = {},
                readOnly = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                }
            )
            
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEach { (optionValue, optionLabel) ->
                    DropdownMenuItem(
                        text = { Text(optionLabel) },
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
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}
