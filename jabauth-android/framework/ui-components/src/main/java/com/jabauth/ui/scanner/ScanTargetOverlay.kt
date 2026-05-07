package com.jabauth.ui.scanner

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Scan Target Overlay
 * 
 * Visual guides for JABCode placement in camera viewfinder.
 * 
 * Features:
 * - Corner guides (40dp x 3dp stroke)
 * - Pulsing animation on corners
 * - Detection state (green + scale effect)
 * - Scanning line with vertical sweep
 * 
 * @param size Overall size of the target overlay
 * @param isScanning Whether actively scanning
 * @param isDetected Whether code has been detected
 * @param primaryColor Color for scanning state
 * @param successColor Color for detected state
 */
@Composable
fun ScanTargetOverlay(
    size: Dp = 300.dp,
    isScanning: Boolean = true,
    isDetected: Boolean = false,
    primaryColor: Color = Color(0xFF00D9FF),
    successColor: Color = Color(0xFF00FF88),
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        // Corner guides with animation
        CornerGuides(
            size = size,
            color = if (isDetected) successColor else primaryColor,
            isDetected = isDetected
        )
        
        // Scanning line animation (only when scanning and not detected)
        if (isScanning && !isDetected) {
            ScanningLine(
                targetHeight = size,
                color = primaryColor
            )
        }
    }
}

/**
 * Corner Guides
 * 
 * Draws 4 L-shaped corners to define the scan target area.
 * Animates scale on detection and pulses during scanning.
 * 
 * @param size Size of the overall target area
 * @param color Color of the corner guides
 * @param isDetected Whether code has been detected
 */
@Composable
fun CornerGuides(
    size: Dp,
    color: Color,
    isDetected: Boolean
) {
    val cornerSize = 40.dp
    val borderWidth = 3.dp
    
    // Scale animation on detection
    val scale by animateFloatAsState(
        targetValue = if (isDetected) 1.1f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "corner_scale"
    )
    
    // Pulsing animation during scanning
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "corner_alpha"
    )
    
    Canvas(
        modifier = Modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    ) {
        val cornerPx = cornerSize.toPx()
        val widthPx = borderWidth.toPx()
        val colorWithAlpha = if (isDetected) color else color.copy(alpha = alpha)
        
        // Top-left corner
        drawPath(
            path = Path().apply {
                moveTo(0f, cornerPx)
                lineTo(0f, 0f)
                lineTo(cornerPx, 0f)
            },
            color = colorWithAlpha,
            style = Stroke(width = widthPx, cap = StrokeCap.Round)
        )
        
        // Top-right corner
        drawPath(
            path = Path().apply {
                moveTo(this@Canvas.size.width - cornerPx, 0f)
                lineTo(this@Canvas.size.width, 0f)
                lineTo(this@Canvas.size.width, cornerPx)
            },
            color = colorWithAlpha,
            style = Stroke(width = widthPx, cap = StrokeCap.Round)
        )
        
        // Bottom-left corner
        drawPath(
            path = Path().apply {
                moveTo(0f, this@Canvas.size.height - cornerPx)
                lineTo(0f, this@Canvas.size.height)
                lineTo(cornerPx, this@Canvas.size.height)
            },
            color = colorWithAlpha,
            style = Stroke(width = widthPx, cap = StrokeCap.Round)
        )
        
        // Bottom-right corner
        drawPath(
            path = Path().apply {
                moveTo(this@Canvas.size.width - cornerPx, this@Canvas.size.height)
                lineTo(this@Canvas.size.width, this@Canvas.size.height)
                lineTo(this@Canvas.size.width, this@Canvas.size.height - cornerPx)
            },
            color = colorWithAlpha,
            style = Stroke(width = widthPx, cap = StrokeCap.Round)
        )
    }
}

/**
 * Scanning Line
 * 
 * Animated vertical line that sweeps through the target area
 * to provide visual feedback during scanning.
 * 
 * @param targetHeight Height of the scan target area
 * @param color Color of the scanning line
 */
@Composable
fun ScanningLine(
    targetHeight: Dp,
    color: Color = Color(0xFF00D9FF)
) {
    val infiniteTransition = rememberInfiniteTransition(label = "scanning")
    val offsetY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = targetHeight.value,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scan_line_position"
    )
    
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(2.dp)
            .offset(y = offsetY.dp)
    ) {
        drawLine(
            brush = Brush.horizontalGradient(
                colors = listOf(
                    Color.Transparent,
                    color,
                    color,
                    Color.Transparent
                ),
                startX = 0f,
                endX = size.width
            ),
            start = Offset(0f, 0f),
            end = Offset(size.width, 0f),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}
