package com.jabauth.diagnostic.ui.errorlog

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Error Log screen - Timestamped error history
 * 
 * Displays chronological list of errors, warnings, and info messages
 * with filtering and clearing capabilities
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ErrorLogScreen(
    modifier: Modifier = Modifier,
    viewModel: ErrorLogViewModel = viewModel()
) {
    val errors by viewModel.errors.collectAsState()
    val filter by viewModel.filter.collectAsState()
    
    var showFilterMenu by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Error Log") },
                actions = {
                    IconButton(onClick = { showFilterMenu = true }) {
                        Icon(
                            imageVector = Icons.Filled.FilterList,
                            contentDescription = "Filter"
                        )
                    }
                    IconButton(
                        onClick = { viewModel.clearErrors() },
                        enabled = errors.isNotEmpty()
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "Clear All"
                        )
                    }
                    
                    DropdownMenu(
                        expanded = showFilterMenu,
                        onDismissRequest = { showFilterMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("All") },
                            onClick = {
                                viewModel.setFilter(ErrorFilter.ALL)
                                showFilterMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Errors Only") },
                            onClick = {
                                viewModel.setFilter(ErrorFilter.ERRORS_ONLY)
                                showFilterMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Warnings Only") },
                            onClick = {
                                viewModel.setFilter(ErrorFilter.WARNINGS_ONLY)
                                showFilterMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Info Only") },
                            onClick = {
                                viewModel.setFilter(ErrorFilter.INFO_ONLY)
                                showFilterMenu = false
                            }
                        )
                    }
                }
            )
        },
        modifier = modifier
    ) { padding ->
        val filteredErrors = when (filter) {
            ErrorFilter.ALL -> errors
            ErrorFilter.ERRORS_ONLY -> errors.filter { it.severity == ErrorSeverity.ERROR }
            ErrorFilter.WARNINGS_ONLY -> errors.filter { it.severity == ErrorSeverity.WARNING }
            ErrorFilter.INFO_ONLY -> errors.filter { it.severity == ErrorSeverity.INFO }
        }
        
        if (filteredErrors.isEmpty()) {
            EmptyState(
                message = if (errors.isEmpty()) "No errors logged" else "No errors match filter",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )
        } else {
            ErrorLogContent(
                errors = filteredErrors,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )
        }
    }
}

@Composable
private fun EmptyState(
    message: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ErrorLogContent(
    errors: List<ErrorEntry>,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(errors, key = { it.id }) { error ->
            ErrorCard(error = error)
        }
    }
}

@Composable
private fun ErrorCard(
    error: ErrorEntry,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (error.severity) {
                ErrorSeverity.ERROR -> Color(0xFFFFEBEE)
                ErrorSeverity.WARNING -> Color(0xFFFFF8E1)
                ErrorSeverity.INFO -> Color(0xFFE3F2FD)
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header row with severity badge and timestamp
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SeverityBadge(severity = error.severity)
                
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = error.getFormattedTime(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = error.getFormattedDate(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // Source
            Text(
                text = error.source,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            // Message
            Text(
                text = error.message,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            
            // Details (if available)
            error.details?.let { details ->
                Text(
                    text = details,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SeverityBadge(
    severity: ErrorSeverity,
    modifier: Modifier = Modifier
) {
    val (text, color) = when (severity) {
        ErrorSeverity.ERROR -> "ERROR" to Color(0xFFD32F2F)
        ErrorSeverity.WARNING -> "WARNING" to Color(0xFFF57C00)
        ErrorSeverity.INFO -> "INFO" to Color(0xFF1976D2)
    }
    
    Surface(
        modifier = modifier,
        color = color,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}
