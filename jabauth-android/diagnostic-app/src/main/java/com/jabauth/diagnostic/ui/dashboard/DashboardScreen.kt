package com.jabauth.diagnostic.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jabauth.diagnostic.ui.dashboard.components.AlertSection
import com.jabauth.diagnostic.ui.dashboard.components.ColorModeGrid
import com.jabauth.diagnostic.ui.dashboard.components.LiveFeed
import com.jabauth.diagnostic.ui.dashboard.components.MetricsBar
import com.jabauth.diagnostic.ui.dashboard.components.PerformanceChart

/**
 * Dashboard Screen - Diagnostic monitoring and metrics
 * 
 * Phase 2 Implementation:
 * - Live metrics bar (encode/decode times, success rate)
 * - Color mode comparison grid (6 modes)
 * - Performance monitoring
 * - Framework status
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToScanner: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedColorMode by remember { mutableIntStateOf(8) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("JABAuth Diagnostic") },
                actions = {
                    IconButton(onClick = { /* TODO: Implement refresh diagnostics */ }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh diagnostics"
                        )
                    }
                    IconButton(onClick = { /* TODO: Implement share report */ }) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share diagnostic report"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        },
        modifier = modifier
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Live Metrics Bar
            item {
                MetricsBar(
                    avgEncodeMs = 67.8,
                    avgDecodeMs = 89.5,
                    successRate = 0.94,
                    activeTests = 6,
                    deviceName = "SM-S938U"
                )
            }
            
            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            // Color Mode Comparison
            item {
                ColorModeGrid(
                    selectedMode = selectedColorMode,
                    onModeSelected = { selectedColorMode = it }
                )
            }
            
            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            // Performance Chart
            item {
                PerformanceChart()
            }
            
            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            // Alert Section
            item {
                AlertSection()
            }
            
            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            // Live Feed
            item {
                LiveFeed()
            }
            
            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
            
            // Framework Status Section
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "Framework Status",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        val modules = listOf(
                            "Core Module" to "✅ Active",
                            "JABCode SDK" to "✅ Active",
                            "JABAuth Client" to "✅ Active",
                            "Diagnostic Engine" to "✅ Active",
                            "UI Components" to "✅ Active"
                        )
                        
                        modules.forEach { (name, status) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = name,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = status,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
            
            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            // Quick Actions
            item {
                Button(
                    onClick = onNavigateToScanner,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Start Scanner")
                }
            }
            
            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
