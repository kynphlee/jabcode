package com.jabauth.diagnostic.ui.errorlog

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jabauth.ui.components.Badge
import com.jabauth.ui.components.FeedItemType
import com.jabauth.ui.theme.JABAuthBgBase
import com.jabauth.ui.theme.JABAuthBgElevated
import com.jabauth.ui.theme.JABAuthTextDim
import com.jabauth.ui.theme.JABAuthTextPrimary
import com.jabauth.ui.theme.JABAuthTextSecondary
import com.jabauth.ui.theme.Spacing
import kotlinx.coroutines.delay

/**
 * Error Log screen - Timestamped error history
 *
 * Displays chronological list of errors, warnings, and info messages with
 * filtering and clearing capabilities. Styled per DESIGN_SYSTEM.md v1.0.0:
 * severity-filtered cards use the JABAuth feed-item idiom (elevated surface,
 * 3dp left accent strip in the severity colour, tinted icon box) and enter the
 * list with the staggered-reveal motion.
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = JABAuthBgBase,
                    titleContentColor = JABAuthTextPrimary,
                    actionIconContentColor = JABAuthTextSecondary
                ),
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
        containerColor = JABAuthBgBase,
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
            color = JABAuthTextSecondary
        )
    }
}

@Composable
private fun ErrorLogContent(
    errors: List<ErrorEntry>,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        itemsIndexed(errors, key = { _, error -> error.id }) { index, error ->
            StaggeredCardReveal(index = index) {
                ErrorCard(error = error)
            }
        }
    }
}

/**
 * Severity-filtered error card in the JABAuth feed-item idiom.
 *
 * Spec (DESIGN_SYSTEM.md v1.0.0 — Feed Item): elevated background, 4dp corner
 * radius, a 3dp full-height left accent strip in the severity colour and a
 * 32x32dp tinted icon box. The card body carries the richer error detail
 * (severity badge, timestamp, source, message, optional details) that a plain
 * [com.jabauth.ui.components.FeedItem] cannot express.
 */
@Composable
private fun ErrorCard(
    error: ErrorEntry,
    modifier: Modifier = Modifier
) {
    val type = error.severity.feedType
    val accent = type.color
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(4.dp))
            .background(JABAuthBgElevated)
    ) {
        // 3dp full-height left accent strip in the severity colour.
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(accent)
        )
        Row(modifier = Modifier.padding(Spacing.sm)) {
            // 32x32dp tinted icon box.
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(accent.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = error.severity.icon,
                    contentDescription = null,
                    tint = accent
                )
            }
            Column(
                modifier = Modifier.padding(start = Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                // Header row: severity badge + timestamp.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Badge(text = error.severity.label, color = accent)

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = error.getFormattedTime(),
                            style = MaterialTheme.typography.labelSmall,
                            color = JABAuthTextSecondary
                        )
                        Text(
                            text = error.getFormattedDate(),
                            style = MaterialTheme.typography.labelSmall,
                            color = JABAuthTextDim
                        )
                    }
                }

                // Source.
                Text(
                    text = error.source,
                    style = MaterialTheme.typography.labelMedium,
                    color = JABAuthTextSecondary
                )

                // Message.
                Text(
                    text = error.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = JABAuthTextPrimary
                )

                // Details (if available).
                error.details?.let { details ->
                    Text(
                        text = details,
                        style = MaterialTheme.typography.bodySmall,
                        color = JABAuthTextSecondary
                    )
                }
            }
        }
    }
}

/**
 * Staggered card-entrance wrapper (DESIGN_SYSTEM.md v1.0.0 — Motion System).
 *
 * Each item appears after a 100ms-per-index delay, then fades in while sliding
 * 24dp upward over 600ms on the Decelerate easing curve.
 */
@Composable
private fun StaggeredCardReveal(
    index: Int,
    content: @Composable () -> Unit
) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(index * StaggerDelayPerIndexMs)
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(
            animationSpec = tween(
                durationMillis = AnimationDurationSlow,
                easing = AnimationEasingDecelerate
            )
        ) + slideInVertically(
            initialOffsetY = { 24 },
            animationSpec = tween(
                durationMillis = AnimationDurationSlow,
                easing = AnimationEasingDecelerate
            )
        )
    ) {
        content()
    }
}

/** 600ms entrance duration (DESIGN_SYSTEM.md AnimationDuration.Slow). */
private const val AnimationDurationSlow = 600

/** Decelerate easing — enter screen (DESIGN_SYSTEM.md AnimationEasing.Decelerate). */
private val AnimationEasingDecelerate = CubicBezierEasing(0.0f, 0.0f, 0.2f, 1f)

/** 100ms-per-index reveal stagger. */
private const val StaggerDelayPerIndexMs = 100L

/** Severity → feed-item accent type (drives the strip + icon-box colour). */
private val ErrorSeverity.feedType: FeedItemType
    get() = when (this) {
        ErrorSeverity.ERROR -> FeedItemType.ERROR
        ErrorSeverity.WARNING -> FeedItemType.WARNING
        ErrorSeverity.INFO -> FeedItemType.INFO
    }

/** Severity → leading icon. */
private val ErrorSeverity.icon: ImageVector
    get() = when (this) {
        ErrorSeverity.ERROR -> Icons.Filled.Error
        ErrorSeverity.WARNING -> Icons.Filled.Warning
        ErrorSeverity.INFO -> Icons.Filled.Info
    }

/** Severity → badge label (natural case; [Badge] uppercases for display). */
private val ErrorSeverity.label: String
    get() = when (this) {
        ErrorSeverity.ERROR -> "Error"
        ErrorSeverity.WARNING -> "Warning"
        ErrorSeverity.INFO -> "Info"
    }
