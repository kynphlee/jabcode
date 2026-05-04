package com.jabauth.ui.scanner

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jabauth.ui.theme.JABAuthError
import com.jabauth.ui.theme.JABAuthSuccess
import com.jabauth.ui.theme.JABAuthWarning

/**
 * Scan status types
 */
enum class ScanStatus {
    SCANNING,
    SUCCESS,
    ERROR,
    WARNING
}

/**
 * Overlay displaying scan status
 * 
 * @param status Current scan status
 * @param message Status message
 * @param modifier Modifier
 */
@Composable
fun ScanStatusOverlay(
    status: ScanStatus,
    message: String,
    modifier: Modifier = Modifier
) {
    val backgroundColor = when (status) {
        ScanStatus.SUCCESS -> JABAuthSuccess
        ScanStatus.ERROR -> JABAuthError
        ScanStatus.WARNING -> JABAuthWarning
        ScanStatus.SCANNING -> MaterialTheme.colorScheme.primary
    }
    
    Box(
        modifier = modifier
            .background(
                color = backgroundColor.copy(alpha = 0.9f),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimary
        )
    }
}
