package com.jabauth.diagnostic.ui.dashboard.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Color mode comparison grid
 * 
 * Displays 6 cards for JABCode color modes (4, 8, 16, 32, 64, 128 colors)
 * with average latency statistics.
 */
@Composable
fun ColorModeGrid(
    selectedMode: Int = 8,
    onModeSelected: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colorModes = listOf(4, 8, 16, 32, 64, 128)
    
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Color Mode Comparison",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)  // Fixed height to avoid infinite constraints
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            userScrollEnabled = false  // Disable internal scrolling
        ) {
            items(colorModes) { mode ->
                ColorModeCard(
                    colorMode = mode,
                    isSelected = mode == selectedMode,
                    avgLatencyMs = calculateMockLatency(mode),
                    onClick = { onModeSelected(mode) }
                )
            }
        }
    }
}

/**
 * Individual color mode card
 */
@Composable
fun ColorModeCard(
    colorMode: Int,
    isSelected: Boolean = false,
    avgLatencyMs: Double = 0.0,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .aspectRatio(1f)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = if (isSelected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        },
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 4.dp else 1.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "$colorMode",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.primary
                }
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = "colors",
                style = MaterialTheme.typography.labelSmall,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "%.1f ms".format(avgLatencyMs),
                style = MaterialTheme.typography.bodySmall,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

/**
 * Mock latency calculation (will be replaced with real data)
 */
private fun calculateMockLatency(colorMode: Int): Double {
    return when (colorMode) {
        4 -> 45.2
        8 -> 67.8
        16 -> 89.5
        32 -> 112.3
        64 -> 145.7
        128 -> 189.4
        else -> 0.0
    }
}
