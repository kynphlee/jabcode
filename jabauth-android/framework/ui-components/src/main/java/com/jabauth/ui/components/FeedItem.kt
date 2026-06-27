package com.jabauth.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.IntrinsicSize
import com.jabauth.ui.theme.JABAuthBgElevated
import com.jabauth.ui.theme.JABAuthError
import com.jabauth.ui.theme.JABAuthPrimary
import com.jabauth.ui.theme.JABAuthSuccess
import com.jabauth.ui.theme.JABAuthTextPrimary
import com.jabauth.ui.theme.JABAuthTextSecondary
import com.jabauth.ui.theme.JABAuthWarning
import com.jabauth.ui.theme.Spacing

/**
 * Semantic type of a [FeedItem], driving its accent colour (left border +
 * icon-box tint).
 */
enum class FeedItemType(val color: Color) {
    INFO(JABAuthPrimary),
    SUCCESS(JABAuthSuccess),
    WARNING(JABAuthWarning),
    ERROR(JABAuthError)
}

/**
 * JABAuth activity-feed row.
 *
 * Spec (DESIGN_SYSTEM.md v1.0.0): elevated background, 3dp solid left border in
 * the type colour, 4dp corner radius, 32x32dp icon box (6dp radius, tinted
 * background), 12dp (Spacing.sm) padding, 12dp gap between icon and text.
 *
 * The 3dp left border is a dedicated full-height accent strip as the first row
 * child (the row sizes to its content height via [IntrinsicSize.Min]).
 *
 * @param title primary line (bodyMedium, high-emphasis)
 * @param icon leading icon shown in the tinted icon box
 * @param modifier outer modifier
 * @param type semantic type controlling the accent colour
 * @param subtitle optional secondary line (bodySmall, medium-emphasis)
 */
@Composable
fun FeedItem(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    type: FeedItemType = FeedItemType.INFO,
    subtitle: String? = null
) {
    val accent = type.color
    Row(
        modifier = modifier
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(4.dp))
            .background(JABAuthBgElevated)
    ) {
        // 3dp full-height left accent border.
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(accent)
        )
        Row(
            modifier = Modifier.padding(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(accent.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent
                )
            }
            Column(
                modifier = Modifier.padding(start = Spacing.sm)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = JABAuthTextPrimary
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = JABAuthTextSecondary
                    )
                }
            }
        }
    }
}
