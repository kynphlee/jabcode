package com.jabauth.diagnostic.ui.errorlog

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jabauth.diagnostic.verify.StageErrorTag
import com.jabauth.ui.components.Badge
import com.jabauth.ui.components.FeedItemType
import com.jabauth.ui.theme.JABAuthBgBase
import com.jabauth.ui.theme.JABAuthBgElevated
import com.jabauth.ui.theme.JABAuthTextDim
import com.jabauth.ui.theme.JABAuthTextPrimary
import com.jabauth.ui.theme.JABAuthTextSecondary
import com.jabauth.ui.theme.ModAbe
import com.jabauth.ui.theme.ModJabcode
import com.jabauth.ui.theme.ModJwt
import com.jabauth.ui.theme.ModPki
import com.jabauth.ui.theme.Spacing
import kotlinx.coroutines.delay

/**
 * Error Log screen — verification-aware, timestamped error history.
 *
 * A horizontal **STAGE** filter chip row (`all · decode · pki · jwt · abe`, each chip in its module colour)
 * filters the list by verification pipeline stage. Each entry is a card carrying two chips — the stage TAG
 * (module colour) and the severity (ERROR magenta / WARNING amber) — plus timestamp, title and detail, with
 * a full-height left border in the severity colour (DESIGN_SYSTEM.md v1.0.0 feed-item idiom). Entries enter
 * with the staggered-reveal motion.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ErrorLogScreen(
    modifier: Modifier = Modifier,
    viewModel: ErrorLogViewModel = viewModel()
) {
    val errors by viewModel.errors.collectAsState()
    val filter by viewModel.filter.collectAsState()

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
                    IconButton(
                        onClick = { viewModel.clearErrors() },
                        enabled = errors.isNotEmpty()
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "Clear All"
                        )
                    }
                }
            )
        },
        containerColor = JABAuthBgBase,
        modifier = modifier
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            StageFilterRow(
                selected = filter,
                onSelect = viewModel::setFilter,
                modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm)
            )

            val filteredErrors = errors.filter { filter.accepts(it) }
            if (filteredErrors.isEmpty()) {
                EmptyState(
                    message = if (errors.isEmpty()) "No errors logged" else "No errors match filter",
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                ErrorLogContent(
                    errors = filteredErrors,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

/**
 * The STAGE filter chip row: `all · decode · pki · jwt · abe`. Each stage chip is tinted in its module
 * colour; the selected chip fills solid (with an on-accent label), the rest read as an outlined tint.
 * Horizontally scrollable so the row never truncates on a narrow device.
 */
@Composable
private fun StageFilterRow(
    selected: ErrorFilter,
    onSelect: (ErrorFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "STAGE",
            style = MaterialTheme.typography.labelSmall,
            color = JABAuthTextDim
        )
        STAGE_FILTERS.forEach { stageFilter ->
            FilterChip(
                label = stageFilter.chipLabel,
                accent = stageFilter.chipColor,
                selected = stageFilter == selected,
                onClick = { onSelect(stageFilter) }
            )
        }
    }
}

/**
 * One stage filter chip. Selected → solid accent fill + dark label; unselected → accent @ 0.15 fill,
 * accent border, accent label. Mirrors the [Badge] geometry (4dp radius, 12x4 padding).
 */
@Composable
private fun FilterChip(
    label: String,
    accent: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(4.dp)
    val background = if (selected) accent else accent.copy(alpha = 0.15f)
    val labelColor = if (selected) JABAuthBgBase else accent
    Text(
        text = label.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = labelColor,
        modifier = modifier
            .clip(shape)
            .background(background)
            .border(width = 1.dp, color = accent, shape = shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 4.dp)
    )
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
        modifier = modifier.padding(horizontal = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = Spacing.md)
    ) {
        itemsIndexed(errors, key = { _, error -> error.id }) { index, error ->
            StaggeredCardReveal(index = index) {
                ErrorCard(error = error)
            }
        }
    }
}

/**
 * A verification-aware error card in the JABAuth feed-item idiom.
 *
 * Header row: the stage TAG chip (module colour, present only for pipeline entries) + the severity chip,
 * then the timestamp. Below: title (message) and the detail line (the stage's real `reason`). A 3dp
 * full-height left accent strip and a 32dp tinted icon box both take the severity colour, so a failure
 * reads as red / a warning as amber at a glance.
 */
@Composable
private fun ErrorCard(
    error: ErrorEntry,
    modifier: Modifier = Modifier
) {
    val accent = error.severity.feedType.color
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
                // Header row: stage TAG chip + severity chip on the left, timestamp on the right.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        error.stageTag?.let { tag ->
                            Badge(text = tag.label, color = tag.color)
                        }
                        Badge(text = error.severity.label, color = accent)
                    }

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

                // Title (message).
                Text(
                    text = error.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = JABAuthTextPrimary
                )

                // Detail line (the stage's real reason, if any).
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

/** The stage filter chips shown in the STAGE row, in pipeline order. */
private val STAGE_FILTERS = listOf(
    ErrorFilter.ALL, ErrorFilter.DECODE, ErrorFilter.PKI, ErrorFilter.JWT, ErrorFilter.ABE
)

/** Chip label for a stage filter (`all` / `decode` / …). */
private val ErrorFilter.chipLabel: String
    get() = when (this) {
        ErrorFilter.ALL -> "all"
        ErrorFilter.DECODE -> "decode"
        ErrorFilter.PKI -> "pki"
        ErrorFilter.JWT -> "jwt"
        ErrorFilter.ABE -> "abe"
        else -> name.lowercase()
    }

/** Chip accent for a stage filter — its module identity colour (ALL uses the neutral primary text hue). */
private val ErrorFilter.chipColor: Color
    get() = when (this) {
        ErrorFilter.DECODE -> ModJabcode
        ErrorFilter.PKI -> ModPki
        ErrorFilter.JWT -> ModJwt
        ErrorFilter.ABE -> ModAbe
        else -> JABAuthTextSecondary
    }

/** Stage tag → its module identity colour (drives the card's TAG chip). */
private val StageErrorTag.color: Color
    get() = when (this) {
        StageErrorTag.DECODE -> ModJabcode
        StageErrorTag.PKI -> ModPki
        StageErrorTag.JWT -> ModJwt
        StageErrorTag.ABE -> ModAbe
    }

/** Stage tag → badge label ([Badge] uppercases for display). */
private val StageErrorTag.label: String
    get() = name.lowercase()

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
