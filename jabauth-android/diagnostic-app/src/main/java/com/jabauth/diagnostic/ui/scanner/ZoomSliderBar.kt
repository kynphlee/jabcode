package com.jabauth.diagnostic.ui.scanner

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// --- Zoom slider bar + scans counter + diagnostics toggle ---

@Composable
internal fun ZoomSliderBar(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    scanCount: Int,
    onTogglePanel: () -> Unit,
    panelExpanded: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.35f))
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Scans: $scanCount", color = TextSecondary, fontSize = 12.sp)
            Spacer(Modifier.weight(1f))
            Text(
                "%.1f×".format(value),
                color = Orange, fontSize = 12.sp, fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.width(12.dp))
            Surface(
                color = if (panelExpanded) Orange else Color.White.copy(alpha = 0.15f),
                shape = CircleShape,
                modifier = Modifier.clickable { onTogglePanel() }
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = "Toggle diagnostics",
                    tint = if (panelExpanded) Color.Black else TextPrimary,
                    modifier = Modifier.padding(6.dp).size(18.dp)
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("−", color = TextPrimary, fontSize = 22.sp)
            Slider(
                value = value,
                onValueChange = onValueChange,
                onValueChangeFinished = onValueChangeFinished,
                valueRange = 1f..MAX_ZOOM,
                colors = SliderDefaults.colors(
                    thumbColor = Orange,
                    activeTrackColor = Orange,
                    inactiveTrackColor = TextSecondary.copy(alpha = 0.4f)
                ),
                modifier = Modifier.weight(1f).padding(horizontal = 10.dp)
            )
            Text("+", color = TextPrimary, fontSize = 22.sp)
        }
    }
}
