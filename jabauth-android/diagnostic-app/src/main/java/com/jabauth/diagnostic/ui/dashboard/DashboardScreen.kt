package com.jabauth.diagnostic.ui.dashboard

import android.Manifest
import android.graphics.BitmapFactory
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import com.jabauth.diagnostic.ui.dashboard.components.AlertSection
import com.jabauth.diagnostic.ui.dashboard.components.ColorModeGrid
import com.jabauth.diagnostic.ui.dashboard.components.LiveFeed
import com.jabauth.diagnostic.ui.dashboard.components.MetricsBar
import com.jabauth.diagnostic.ui.dashboard.components.PerformanceChart
import com.jabauth.jabcode.DecodeOptions
import com.jabauth.jabcode.JABCodeDecoderImpl
import java.io.File

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
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = remember { DashboardViewModel() }
) {
    val context = LocalContext.current
    val dashboardState by viewModel.dashboardState.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    var showSyntheticTestDialog by remember { mutableStateOf(false) }
    
    // Storage permission launcher
    val storagePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            showSyntheticTestDialog = true
        }
    }
    
    fun checkAndRequestPermission() {
        when (ContextCompat.checkSelfPermission(context, storagePermission)) {
            PackageManager.PERMISSION_GRANTED -> {
                showSyntheticTestDialog = true
            }
            else -> {
                permissionLauncher.launch(storagePermission)
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("JABAuth Diagnostic") },
                actions = {
                    IconButton(
                        onClick = { viewModel.refreshMetrics() },
                        enabled = !isRefreshing
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh diagnostics"
                        )
                    }
                    IconButton(
                        onClick = {
                            val report = viewModel.generateReport()
                            // TODO: Share report via Android share sheet
                            println(report)
                        }
                    ) {
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
                    avgEncodeMs = dashboardState.avgEncodeMs,
                    avgDecodeMs = dashboardState.avgDecodeMs,
                    successRate = dashboardState.successRate / 100.0,
                    activeTests = dashboardState.activeTests,
                    deviceName = dashboardState.deviceName
                )
            }
            
            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            // Synthetic Test Button
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Synthetic Image Tests",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Test decoder with perfect synthetic images",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                            )
                        }
                        Button(
                            onClick = { checkAndRequestPermission() }
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Run Tests")
                        }
                    }
                }
            }
            
            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            // Color Mode Comparison
            item {
                ColorModeGrid(
                    selectedMode = dashboardState.selectedColorMode,
                    onModeSelected = { viewModel.selectColorMode(it) }
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
    
    // Synthetic Test Dialog
    if (showSyntheticTestDialog) {
        val decoder = remember { JABCodeDecoderImpl() }
        
        SyntheticTestDialog(
            onDismiss = { showSyntheticTestDialog = false },
            onDecodeImage = { imageFile ->
                try {
                    android.util.Log.d("SyntheticTest", "Decoding file: ${imageFile.absolutePath}")
                    android.util.Log.d("SyntheticTest", "File exists: ${imageFile.exists()}, size: ${imageFile.length()}")
                    
                    val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
                    android.util.Log.d("SyntheticTest", "Bitmap loaded: ${bitmap != null}, size: ${bitmap?.width}x${bitmap?.height}")
                    
                    if (bitmap == null) {
                        android.util.Log.e("SyntheticTest", "Failed to load bitmap")
                        return@SyntheticTestDialog null
                    }
                    
                    val result = decoder.decode(
                        image = bitmap,
                        options = DecodeOptions(timeout = 5000)
                    )
                    
                    android.util.Log.d("SyntheticTest", "Decode result: ${result != null}, data: ${result?.data?.size ?: 0} bytes")
                    
                    // Return decoded string or null
                    val decoded = result?.data?.toString(Charsets.UTF_8)
                    android.util.Log.d("SyntheticTest", "Decoded string: $decoded")
                    decoded
                } catch (e: Exception) {
                    android.util.Log.e("SyntheticTest", "Exception during decode", e)
                    null
                }
            }
        )
    }
}
