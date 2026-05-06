package com.jabauth.diagnostic.ui.dashboard.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Performance Chart Component
 * 
 * Features:
 * - Canvas-based bar chart for 6 color modes
 * - Staggered animation on mount
 * - Grid background for easier reading
 * - Color-coded bars with labels
 * - Professional dashboard aesthetic
 */
@Composable
fun PerformanceChart(
    latencyData: Map<Int, Double> = mapOf(
        4 to 45.2,
        8 to 67.8,
        16 to 89.5,
        32 to 112.3,
        64 to 145.7,
        128 to 189.4
    ),
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header
            Text(
                text = "PERFORMANCE COMPARISON",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            
            Text(
                text = "Average latency by color mode",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Chart
            AnimatedBarChart(
                latencyData = latencyData,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
            )
        }
    }
}

/**
 * Animated bar chart with staggered entrance animations
 */
@Composable
private fun AnimatedBarChart(
    latencyData: Map<Int, Double>,
    modifier: Modifier = Modifier
) {
    val colorModes = latencyData.keys.sorted()
    val maxLatency = latencyData.values.maxOrNull() ?: 200.0
    
    // Staggered animations for each bar
    val animatedValues = colorModes.mapIndexed { index, mode ->
        val animationSpec = remember {
            tween<Float>(
                durationMillis = 800,
                delayMillis = index * 100,
                easing = FastOutSlowInEasing
            )
        }
        
        var targetValue by remember { mutableStateOf(0f) }
        val animatedValue by animateFloatAsState(
            targetValue = targetValue,
            animationSpec = animationSpec,
            label = "bar_animation_$mode"
        )
        
        LaunchedEffect(Unit) {
            targetValue = 1f
        }
        
        mode to animatedValue
    }.toMap()
    
    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    
    Box(modifier = modifier) {
        // Canvas for bars and grid
        Canvas(modifier = Modifier.fillMaxSize()) {
            val chartWidth = size.width
            val chartHeight = size.height
            
            // Better spacing calculation: divide width by number of bars, with padding
            val totalPadding = chartWidth * 0.1f  // 10% padding on sides
            val availableWidth = chartWidth - totalPadding
            val barWidth = availableWidth / colorModes.size * 0.7f  // 70% of space = bar, 30% = gap
            val gapWidth = availableWidth / colorModes.size * 0.3f
            val startPadding = totalPadding / 2
            
            val maxBarHeight = chartHeight * 0.75f
            
            // Draw grid lines
            drawGridLines(
                chartHeight = chartHeight,
                chartWidth = chartWidth,
                gridColor = onSurfaceVariant.copy(alpha = 0.1f)
            )
            
            // Draw bars
            colorModes.forEachIndexed { index, mode ->
                val latency = latencyData[mode] ?: 0.0
                val animationProgress = animatedValues[mode] ?: 0f
                val barHeight = (latency / maxLatency * maxBarHeight * animationProgress).toFloat()
                
                // Center bars in their allocated space
                val xPosition = startPadding + (index * (barWidth + gapWidth)) + (gapWidth / 2)
                val yPosition = chartHeight - barHeight - 55.dp.toPx()
                
                // Draw bar
                drawBar(
                    x = xPosition,
                    y = yPosition,
                    width = barWidth,
                    height = barHeight,
                    color = getBarColor(mode, primaryColor)
                )
            }
        }
        
        // Overlay labels - aligned with bars
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            colorModes.forEach { mode ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "$mode",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 11.sp
                    )
                    Text(
                        text = "colors",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 8.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        
        // Overlay values - aligned with bars
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            colorModes.forEach { mode ->
                val latency = latencyData[mode] ?: 0.0
                val animationProgress = animatedValues[mode] ?: 0f
                
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.TopCenter
                ) {
                    if (animationProgress > 0.6f) {
                        Text(
                            text = "%.1f".format(latency),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 9.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * Draw grid lines for better readability
 */
private fun DrawScope.drawGridLines(
    chartHeight: Float,
    chartWidth: Float,
    gridColor: Color
) {
    val lineCount = 5
    val spacing = (chartHeight * 0.75f) / lineCount
    
    repeat(lineCount) { i ->
        val y = chartHeight - 40.dp.toPx() - (i * spacing)
        drawLine(
            color = gridColor,
            start = Offset(0f, y),
            end = Offset(chartWidth, y),
            strokeWidth = 1.dp.toPx()
        )
    }
}

/**
 * Draw individual bar with highlight edge
 */
private fun DrawScope.drawBar(
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    color: Color
) {
    // Bar fill
    drawRect(
        color = color,
        topLeft = Offset(x, y),
        size = Size(width, height)
    )
    
    // Highlight on left edge
    drawRect(
        color = color.copy(alpha = 0.7f),
        topLeft = Offset(x, y),
        size = Size(2.dp.toPx(), height)
    )
}

/**
 * Get color for bar based on color mode
 */
private fun getBarColor(colorMode: Int, primaryColor: Color): Color {
    return when (colorMode) {
        4 -> primaryColor.copy(alpha = 0.5f)
        8 -> primaryColor.copy(alpha = 0.6f)
        16 -> primaryColor.copy(alpha = 0.75f)
        32 -> primaryColor.copy(alpha = 0.85f)
        64 -> primaryColor.copy(alpha = 0.95f)
        128 -> primaryColor
        else -> primaryColor
    }
}
