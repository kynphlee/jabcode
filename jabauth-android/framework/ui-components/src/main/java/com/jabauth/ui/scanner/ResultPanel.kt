package com.jabauth.ui.scanner

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jabauth.ui.theme.JABAuthError
import com.jabauth.ui.theme.JABAuthSuccess

/**
 * Result Panel - Bottom Sheet
 * 
 * Displays authentication results and validation details.
 * Slides up from bottom with scan results, validations, and actions.
 * 
 * @param result Authentication result data (null = hidden)
 * @param onDismiss Close bottom sheet callback
 * @param onAccept Accept result callback (success only)
 * @param onScanAgain Scan again callback
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultPanel(
    result: AuthenticationResult?,
    onDismiss: () -> Unit,
    onAccept: () -> Unit,
    onScanAgain: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = false
    )
    
    if (result != null) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            dragHandle = null,
            modifier = modifier
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Header
                ResultHeader(
                    status = result.status,
                    title = when (result.status) {
                        ResultStatus.SUCCESS -> "Authentication Valid"
                        ResultStatus.FAILED -> "Authentication Failed"
                    },
                    subtitle = result.subject,
                    onClose = onDismiss
                )
                
                // Validation badges
                ValidationBadges(validations = result.validations)
                
                // Detail sections
                DetailSection(
                    label = "Certificate Info",
                    rows = listOf(
                        "Subject" to result.certificateInfo.subject,
                        "Issuer" to result.certificateInfo.issuer,
                        "Valid Until" to result.certificateInfo.validUntil,
                        "Serial" to result.certificateInfo.serial
                    )
                )
                
                DetailSection(
                    label = "JWT Token",
                    rows = listOf(
                        "Subject" to result.jwtInfo.subject,
                        "Algorithm" to result.jwtInfo.algorithm,
                        "Issued" to result.jwtInfo.issued,
                        "Expires" to result.jwtInfo.expires
                    )
                )
                
                DetailSection(
                    label = "Scan Details",
                    rows = listOf(
                        "Color Mode" to result.scanDetails.colorMode,
                        "ECC Level" to result.scanDetails.eccLevel,
                        "Decode Time" to result.scanDetails.decodeTime,
                        "Quality" to result.scanDetails.quality
                    )
                )
                
                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (result.status == ResultStatus.SUCCESS) {
                        Button(
                            onClick = onAccept,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = JABAuthSuccess
                            )
                        ) {
                            Text("✓ Accept")
                        }
                    }
                    
                    Button(
                        onClick = onScanAgain,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (result.status == ResultStatus.FAILED) 
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Text(if (result.status == ResultStatus.FAILED) "↻ Retry" else "↻ Scan Again")
                    }
                }
            }
        }
    }
}

/**
 * Result Header
 * 
 * Header row with status icon, title, subtitle, and close button.
 * 
 * @param status Result status (success/failed)
 * @param title Main title text
 * @param subtitle Secondary text
 * @param onClose Close button callback
 */
@Composable
fun ResultHeader(
    status: ResultStatus,
    title: String,
    subtitle: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            // Status icon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        color = when (status) {
                            ResultStatus.SUCCESS -> JABAuthSuccess.copy(alpha = 0.2f)
                            ResultStatus.FAILED -> JABAuthError.copy(alpha = 0.2f)
                        },
                        shape = RoundedCornerShape(8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = when (status) {
                        ResultStatus.SUCCESS -> "✓"
                        ResultStatus.FAILED -> "✕"
                    },
                    fontSize = 28.sp,
                    color = when (status) {
                        ResultStatus.SUCCESS -> JABAuthSuccess
                        ResultStatus.FAILED -> JABAuthError
                    }
                )
            }
            
            // Title and subtitle
            Column {
                Text(
                    text = title,
                    style = TextStyle(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        // Close button
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .size(32.dp)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(6.dp)
                )
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                    RoundedCornerShape(6.dp)
                )
        ) {
            Text("✕", fontSize = 18.sp)
        }
    }
}

/**
 * Validation Badges
 * 
 * FlowRow of validation check badges with pass/fail indicators.
 * 
 * @param validations List of validation checks
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ValidationBadges(
    validations: List<ValidationCheck>,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        validations.forEach { validation ->
            Surface(
                color = if (validation.passed) 
                    JABAuthSuccess.copy(alpha = 0.2f)
                else 
                    JABAuthError.copy(alpha = 0.2f),
                shape = RoundedCornerShape(4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (validation.passed) "✓" else "✕",
                        fontSize = 12.sp,
                        color = if (validation.passed) JABAuthSuccess else JABAuthError
                    )
                    Text(
                        text = validation.label.uppercase(),
                        style = TextStyle(
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.05.sp,
                            color = if (validation.passed) JABAuthSuccess else JABAuthError
                        )
                    )
                }
            }
        }
    }
}

/**
 * Detail Section
 * 
 * Card displaying key-value pairs in structured format.
 * Used for certificate, JWT, and scan details.
 * 
 * @param label Section label (uppercase)
 * @param rows List of key-value pairs
 */
@Composable
fun DetailSection(
    label: String,
    rows: List<Pair<String, String>>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Section label
            Text(
                text = label.uppercase(),
                style = TextStyle(
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.05.sp,
                    color = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            // Key-value rows
            rows.forEachIndexed { index, (key, value) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "$key:",
                        style = TextStyle(
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.weight(0.4f)
                    )
                    Text(
                        text = value,
                        style = TextStyle(
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.weight(0.6f),
                        textAlign = TextAlign.End
                    )
                }
                
                // Divider between rows
                if (index < rows.size - 1) {
                    Divider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                        thickness = 1.dp
                    )
                }
            }
        }
    }
}
