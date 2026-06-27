package com.jabauth.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Card
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jabauth.ui.theme.JABAuthBgCard
import com.jabauth.ui.theme.JABAuthBorder
import com.jabauth.ui.theme.Spacing

/**
 * JABAuth primary card surface.
 *
 * Spec (DESIGN_SYSTEM.md v1.0.0): card background, 1dp border, 12dp corner
 * radius, 24dp (Spacing.lg) padding, no shadow.
 *
 * @param modifier outer modifier (size / layout)
 * @param content card content, laid out in a [ColumnScope] with the standard padding applied
 */
@Composable
fun JABAuthCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = JABAuthBgCard),
        border = BorderStroke(1.dp, JABAuthBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.padding(Spacing.lg),
            content = content
        )
    }
}
