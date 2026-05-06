package com.jabauth.diagnostic.ui.dashboard.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Alert Section - Critical warnings and errors
 * 
 * Displays dismissible alert cards for:
 * - Critical errors requiring attention
 * - Warning conditions to monitor
 */
@Composable
fun AlertSection(
    alerts: List<Alert> = mockAlerts(),
    onDismiss: (Alert) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var visibleAlerts by remember { mutableStateOf(alerts) }
    
    if (visibleAlerts.isEmpty()) {
        return
    }
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "ALERTS",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.error
        )
        
        visibleAlerts.forEach { alert ->
            AlertCard(
                alert = alert,
                onDismiss = {
                    visibleAlerts = visibleAlerts.filter { it != alert }
                    onDismiss(alert)
                }
            )
        }
    }
}

/**
 * Individual alert card
 */
@Composable
private fun AlertCard(
    alert: Alert,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (containerColor, iconColor, icon) = when (alert.severity) {
        AlertSeverity.ERROR -> Triple(
            Color(0xFFF44336).copy(alpha = 0.1f),
            Color(0xFFF44336),
            Icons.Default.Info
        )
        AlertSeverity.WARNING -> Triple(
            Color(0xFFFFA726).copy(alpha = 0.1f),
            Color(0xFFFFA726),
            Icons.Default.Warning
        )
    }
    
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Icon
            Icon(
                imageVector = icon,
                contentDescription = alert.severity.name,
                tint = iconColor,
                modifier = Modifier.size(24.dp)
            )
            
            // Content
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = alert.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = iconColor
                )
                
                Text(
                    text = alert.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 12.sp
                )
                
                if (alert.recommendation != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = "→ ${alert.recommendation}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
            
            // Dismiss button
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * Alert data class
 */
data class Alert(
    val severity: AlertSeverity,
    val title: String,
    val message: String,
    val recommendation: String? = null
)

/**
 * Alert severity levels
 */
enum class AlertSeverity {
    WARNING,
    ERROR
}

/**
 * Mock alerts for development
 */
private fun mockAlerts() = listOf(
    Alert(
        severity = AlertSeverity.ERROR,
        title = "Critical: High failure rate",
        message = "8-color mode has 3 consecutive decode failures",
        recommendation = "Check camera focus and lighting conditions"
    ),
    Alert(
        severity = AlertSeverity.WARNING,
        title = "Performance degradation",
        message = "Average latency increased by 15% in last 10 tests",
        recommendation = "Consider restarting the diagnostic session"
    )
)
