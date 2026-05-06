package com.jabauth.diagnostic.ui.dashboard.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Live Feed - Diagnostic event stream
 * 
 * Displays real-time diagnostic events:
 * - Success events (green)
 * - Warning events (amber)
 * - Error events (red)
 */
@Composable
fun LiveFeed(
    events: List<FeedEvent> = mockFeedEvents(),
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "LIVE FEED",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Text(
                    text = "${events.size} events",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Event list (fixed height with scrolling)
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(events) { event ->
                    FeedItem(event = event)
                }
            }
        }
    }
}

/**
 * Individual feed event item
 */
@Composable
private fun FeedItem(
    event: FeedEvent,
    modifier: Modifier = Modifier
) {
    val (icon, iconColor) = when (event.type) {
        EventType.SUCCESS -> Icons.Default.CheckCircle to Color(0xFF4CAF50)
        EventType.WARNING -> Icons.Default.Warning to Color(0xFFFFA726)
        EventType.ERROR -> Icons.Default.Info to Color(0xFFF44336)
    }
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Icon
        Icon(
            imageVector = icon,
            contentDescription = event.type.name,
            tint = iconColor,
            modifier = Modifier.size(20.dp)
        )
        
        // Content
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = event.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Text(
                text = event.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )
        }
        
        // Timestamp
        Text(
            text = event.timestamp,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp
        )
    }
}

/**
 * Feed event data class
 */
data class FeedEvent(
    val type: EventType,
    val title: String,
    val message: String,
    val timestamp: String
)

/**
 * Event severity types
 */
enum class EventType {
    SUCCESS,
    WARNING,
    ERROR
}

/**
 * Mock feed events for development
 */
private fun mockFeedEvents() = listOf(
    FeedEvent(
        type = EventType.SUCCESS,
        title = "128-color decode completed",
        message = "Successfully decoded in 189.4ms",
        timestamp = "Just now"
    ),
    FeedEvent(
        type = EventType.SUCCESS,
        title = "64-color encode completed",
        message = "Encoded 2KB data in 145.7ms",
        timestamp = "2s ago"
    ),
    FeedEvent(
        type = EventType.WARNING,
        title = "High latency detected",
        message = "32-color mode took 125ms (expected <115ms)",
        timestamp = "5s ago"
    ),
    FeedEvent(
        type = EventType.SUCCESS,
        title = "16-color roundtrip passed",
        message = "Encode + decode completed successfully",
        timestamp = "8s ago"
    ),
    FeedEvent(
        type = EventType.ERROR,
        title = "8-color decode failed",
        message = "Invalid color palette detected",
        timestamp = "12s ago"
    ),
    FeedEvent(
        type = EventType.SUCCESS,
        title = "4-color test completed",
        message = "Fastest mode: 45.2ms average",
        timestamp = "15s ago"
    )
)
