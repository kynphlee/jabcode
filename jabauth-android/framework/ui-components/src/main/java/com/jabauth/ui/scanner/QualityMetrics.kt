package com.jabauth.ui.scanner

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jabauth.ui.theme.JABAuthError
import com.jabauth.ui.theme.JABAuthSuccess
import com.jabauth.ui.theme.JABAuthWarning

/**
 * Quality Metric Data
 * 
 * Represents a single quality measurement for scan conditions.
 * 
 * @param label Display label (e.g., "Brightness", "Focus", "Contrast")
 * @param value Quality value from 0.0 (worst) to 1.0 (best)
 * @param threshold Classification threshold (for semantic meaning)
 */
data class QualityMetric(
    val label: String,
    val value: Float,
    val threshold: QualityThreshold = QualityThreshold.Medium
)

/**
 * Quality Threshold Levels
 * 
 * Defines semantic meaning for quality values:
 * - Low: 0.0 - 0.4 (Red - Poor quality, likely scan failures)
 * - Medium: 0.4 - 0.7 (Yellow - Acceptable, may have issues)
 * - High: 0.7 - 1.0 (Green - Excellent quality)
 */
enum class QualityThreshold {
    Low,    // 0.0 - 0.4: Red
    Medium, // 0.4 - 0.7: Yellow
    High    // 0.7 - 1.0: Green
}

/**
 * Quality Indicators Container
 * 
 * Displays multiple quality metrics in a horizontal row.
 * Designed to overlay camera preview at bottom of screen.
 * 
 * @param metrics List of quality metrics to display
 * @param modifier Modifier for container
 */
@Composable
fun QualityIndicators(
    metrics: List<QualityMetric>,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                shape = RoundedCornerShape(8.dp)
            )
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        metrics.forEach { metric ->
            QualityMetricIndicator(metric)
        }
    }
}

/**
 * Single Quality Metric Indicator
 * 
 * Compact vertical layout with:
 * - Label (uppercase, monospace)
 * - Progress bar (60dp x 4dp, color-coded)
 * 
 * Progress bar animates smoothly when value changes.
 * 
 * @param metric Quality metric to display
 */
@Composable
fun QualityMetricIndicator(metric: QualityMetric) {
    val animatedValue by animateFloatAsState(
        targetValue = metric.value.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 300),
        label = "quality_value"
    )
    
    val barColor = when {
        animatedValue < 0.4f -> JABAuthError
        animatedValue < 0.7f -> JABAuthWarning
        else -> JABAuthSuccess
    }
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Label
        Text(
            text = metric.label.uppercase(),
            style = TextStyle(
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.05.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
        
        // Progress bar (60dp x 4dp)
        Box(
            modifier = Modifier
                .width(60.dp)
                .height(4.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(2.dp)
                )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animatedValue)
                    .background(
                        color = barColor,
                        shape = RoundedCornerShape(2.dp)
                    )
            )
        }
    }
}
