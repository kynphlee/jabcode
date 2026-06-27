package com.jabauth.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.jabauth.ui.theme.JABAuthSuccess

/**
 * Pulsing status indicator dot.
 *
 * Spec (DESIGN_SYSTEM.md v1.0.0): 8dp circle, 12dp glow in the indicator
 * colour, pulse over 2000ms animating alpha 1.0 -> 0.4 -> 1.0 (reverse repeat).
 * Default colour is the success accent.
 *
 * The 12dp glow is rendered as a radial-gradient halo around the 8dp core so it
 * reads as a soft glow on every backend (unlike a native shadow layer, which is
 * a no-op under hardware acceleration). The total drawn size is therefore the
 * 8dp dot plus the surrounding halo.
 *
 * @param modifier outer modifier
 * @param color indicator (and glow) colour
 * @param pulsing whether the alpha pulse animation runs; when false the dot is
 *   drawn statically at full alpha (useful for previews / tests)
 */
@Composable
fun StatusIndicator(
    modifier: Modifier = Modifier,
    color: Color = JABAuthSuccess,
    pulsing: Boolean = true
) {
    val transition = rememberInfiniteTransition(label = "statusPulse")
    val animatedAlpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "statusPulseAlpha"
    )
    val alpha = if (pulsing) animatedAlpha else 1f

    // 8dp core + a 12dp glow halo extending beyond it on each side.
    Canvas(modifier = modifier.size(8.dp + 12.dp + 12.dp)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val coreRadiusPx = 4.dp.toPx()      // 8dp diameter
        val haloRadiusPx = coreRadiusPx + 12.dp.toPx()

        // Soft radial glow.
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    color.copy(alpha = 0.45f * alpha),
                    Color.Transparent
                ),
                center = center,
                radius = haloRadiusPx
            ),
            radius = haloRadiusPx,
            center = center
        )
        // Crisp core dot.
        drawCircle(
            color = color.copy(alpha = alpha),
            radius = coreRadiusPx,
            center = center
        )
    }
}
