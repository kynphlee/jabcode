package com.jabauth.diagnostic.ui.dashboard.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Live metrics display with KPI best practices
 * 
 * Features:
 * - Clear visual hierarchy (large values, small labels)
 * - Contextual trend indicators
 * - Better readability with 2-column grid
 * - Semantic colors for trends
 */
@Composable
fun MetricsBar(
    avgEncodeMs: Double = 0.0,
    avgDecodeMs: Double = 0.0,
    successRate: Double = 0.0,
    activeTests: Int = 0,
    deviceName: String = "Unknown",
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Top Row: Encode & Decode Times
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ImprovedMetricCard(
                label = "Encode Time",
                value = "%.1f".format(avgEncodeMs),
                unit = "ms",
                trend = -5.2,  // Mock: 5.2% improvement
                modifier = Modifier.weight(1f)
            )
            
            ImprovedMetricCard(
                label = "Decode Time",
                value = "%.1f".format(avgDecodeMs),
                unit = "ms",
                trend = -3.8,  // Mock: 3.8% improvement
                modifier = Modifier.weight(1f)
            )
        }
        
        // Middle Row: Success Rate & Active Tests
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ImprovedMetricCard(
                label = "Success Rate",
                value = "%.0f".format(successRate * 100),
                unit = "%",
                trend = 2.1,  // Mock: 2.1% increase
                modifier = Modifier.weight(1f)
            )
            
            ImprovedMetricCard(
                label = "Active Tests",
                value = "$activeTests",
                unit = "running",
                trend = null,  // No trend for count
                modifier = Modifier.weight(1f)
            )
        }
        
        // Bottom Row: Device (full width for longer text)
        ImprovedMetricCard(
            label = "Test Device",
            value = deviceName,
            unit = "",
            trend = null,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Improved metric card with KPI best practices:
 * - Large, bold value (primary focus)
 * - Small label above
 * - Trend indicator with color coding
 * - Better spacing and alignment
 */
@Composable
private fun ImprovedMetricCard(
    label: String,
    value: String,
    unit: String,
    trend: Double? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Label (small, subtle)
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 0.5.sp
            )
            
            // Value (large, bold - primary focus)
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 32.sp
                )
                
                if (unit.isNotEmpty()) {
                    Text(
                        text = unit,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
            }
            
            // Trend indicator (if available)
            trend?.let {
                val isPositive = it > 0
                val trendColor = when {
                    // For time metrics, lower is better (green for negative trend)
                    label.contains("Time", ignoreCase = true) -> 
                        if (it < 0) Color(0xFF4CAF50) else Color(0xFFF44336)
                    // For success rate, higher is better (green for positive trend)
                    else -> 
                        if (it > 0) Color(0xFF4CAF50) else Color(0xFFF44336)
                }
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "${if (isPositive) "↑" else "↓"} ${if (it > 0) "+" else ""}%.1f%% vs avg".format(it),
                        style = MaterialTheme.typography.labelMedium,
                        color = trendColor,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
