package com.jabauth.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.jabauth.ui.theme.JABAuthPrimary

/**
 * JABAuth badge / chip.
 *
 * Spec (DESIGN_SYSTEM.md v1.0.0): accent colour @ 0.2 alpha background,
 * UPPERCASE labelMedium text in the accent colour, 12dp horizontal x 4dp
 * vertical padding, 4dp corner radius.
 *
 * The supplied [text] is uppercased for display, so callers pass natural-case
 * strings.
 *
 * @param text badge label (rendered uppercase)
 * @param modifier outer modifier
 * @param color accent colour driving both the fill (@0.2 alpha) and the text
 */
@Composable
fun Badge(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = JABAuthPrimary
) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = color,
        modifier = modifier
            .background(
                color = color.copy(alpha = 0.2f),
                shape = RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 12.dp, vertical = 4.dp)
    )
}
