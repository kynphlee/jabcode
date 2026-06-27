package com.jabauth.diagnostic.ui.scanner

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

// --- Decode-ROI reticle: draggable + resizable; reports a normalized rect ---
//
// The rect (in container px) is the single source of truth. Dragging the body
// MOVES it; dragging the ⤡ corner RESIZES it; ↺ resets it (and zoom). On every
// change it publishes the rect normalized to 0..1 of the view, plus the view's
// aspect ratio, which the analyzer uses to crop the frame 1:1 to the reticle.

@Composable
internal fun RoiReticle(
    onRoiChange: (Float, Float, Float, Float, Float) -> Unit,
    onResetZoom: () -> Unit,
    onResizingChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val minPx = with(density) { 110.dp.toPx() }
    var box by remember { mutableStateOf(IntSize.Zero) }
    var roi by remember { mutableStateOf<Rect?>(null) }
    // Snapshot of the reticle's geometry when a resize starts — drawn as a faint
    // "ghost" so the user can compare the new vs old size before releasing.
    var ghostRoi by remember { mutableStateOf<Rect?>(null) }

    fun defaultRoi(size: IntSize): Rect {
        val w = size.width * 0.82f          // 82% of width, square (px)
        val left = (size.width - w) / 2f
        val top = (size.height - w) / 2f
        return Rect(left, top, left + w, top + w)
    }

    // Initialize the ROI once the container is measured.
    LaunchedEffect(box) {
        if (box != IntSize.Zero && roi == null) roi = defaultRoi(box)
    }
    // Publish the normalized ROI (+ view aspect) whenever it changes.
    LaunchedEffect(roi, box) {
        val r = roi ?: return@LaunchedEffect
        if (box.width == 0 || box.height == 0) return@LaunchedEffect
        onRoiChange(
            r.left / box.width, r.top / box.height,
            r.right / box.width, r.bottom / box.height,
            box.width.toFloat() / box.height
        )
    }

    Box(modifier = modifier.onSizeChanged { box = it }) {
        // Faint ghost of the pre-resize geometry — only present during a resize,
        // drawn first (behind) so the live brackets read on top of it.
        ghostRoi?.let { g ->
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRect(
                    color = Orange.copy(alpha = 0.10f),
                    topLeft = Offset(g.left, g.top),
                    size = Size(g.width, g.height)
                )
                drawRect(
                    color = Orange.copy(alpha = 0.55f),
                    topLeft = Offset(g.left, g.top),
                    size = Size(g.width, g.height),
                    style = Stroke(
                        width = 3f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 12f))
                    )
                )
            }
        }
        val r = roi
        if (r != null) {
            val handle = 56.dp
            val handlePx = with(density) { handle.toPx() }

            // Centre-anchored resize helper: grow/shrink symmetrically about the
            // box centre so the reticle stays put and only its w/h change.
            fun resizeCentered(dHw: Float, dHh: Float) {
                roi?.let { cur ->
                    val cx = (cur.left + cur.right) / 2f
                    val cy = (cur.top + cur.bottom) / 2f
                    val maxHw = minOf(cx, box.width - cx)
                    val maxHh = minOf(cy, box.height - cy)
                    val hw = (cur.width / 2f + dHw).coerceIn(minPx / 2f, maxHw.coerceAtLeast(minPx / 2f))
                    val hh = (cur.height / 2f + dHh).coerceIn(minPx / 2f, maxHh.coerceAtLeast(minPx / 2f))
                    roi = Rect(cx - hw, cy - hh, cx + hw, cy + hh)
                }
            }

            // Reticle body — corner brackets only (no move). Non-interactive so
            // the camera shows through; the corner hit-areas below resize it.
            Box(
                modifier = Modifier
                    .offset { IntOffset(r.left.roundToInt(), r.top.roundToInt()) }
                    .size(with(density) { r.width.toDp() }, with(density) { r.height.toDp() })
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val len = size.minDimension * 0.16f
                    val sw = 6f
                    val c = Orange
                    drawLine(c, Offset(0f, 0f), Offset(len, 0f), strokeWidth = sw, cap = StrokeCap.Round)
                    drawLine(c, Offset(0f, 0f), Offset(0f, len), strokeWidth = sw, cap = StrokeCap.Round)
                    drawLine(c, Offset(size.width, 0f), Offset(size.width - len, 0f), strokeWidth = sw, cap = StrokeCap.Round)
                    drawLine(c, Offset(size.width, 0f), Offset(size.width, len), strokeWidth = sw, cap = StrokeCap.Round)
                    drawLine(c, Offset(0f, size.height), Offset(len, size.height), strokeWidth = sw, cap = StrokeCap.Round)
                    drawLine(c, Offset(0f, size.height), Offset(0f, size.height - len), strokeWidth = sw, cap = StrokeCap.Round)
                    drawLine(c, Offset(size.width, size.height), Offset(size.width - len, size.height), strokeWidth = sw, cap = StrokeCap.Round)
                    drawLine(c, Offset(size.width, size.height), Offset(size.width, size.height - len), strokeWidth = sw, cap = StrokeCap.Round)
                }
            }

            // Four corner resize handles — LONG-PRESS any corner, then drag to
            // resize centre-anchored (dragging a corner away from centre grows
            // the box). (signX, signY) maps each corner's drag direction.
            listOf(
                Triple(r.left,  r.top,    -1f to -1f),  // top-left
                Triple(r.right, r.top,     1f to -1f),  // top-right
                Triple(r.left,  r.bottom, -1f to  1f),  // bottom-left
                Triple(r.right, r.bottom,  1f to  1f)   // bottom-right
            ).forEach { (hx, hy, signs) ->
                val (sx, sy) = signs
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (hx - handlePx / 2f).roundToInt(),
                                (hy - handlePx / 2f).roundToInt()
                            )
                        }
                        .size(handle)
                        .pointerInput(box) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    ghostRoi = roi          // snapshot pre-resize geometry
                                    onResizingChange(true)  // reveal the workload coach
                                },
                                onDragEnd = { ghostRoi = null; onResizingChange(false) },
                                onDragCancel = { ghostRoi = null; onResizingChange(false) },
                                onDrag = { change, drag ->
                                    change.consume()
                                    resizeCentered(sx * drag.x, sy * drag.y)
                                }
                            )
                        }
                )
            }

            // Reset ↺ — centred just below the reticle, clear of the corners.
            IconButton(
                onClick = {
                    onResetZoom()
                    if (box != IntSize.Zero) roi = defaultRoi(box)
                },
                modifier = Modifier.offset {
                    IntOffset(
                        ((r.left + r.right) / 2f - handlePx / 2f).roundToInt(),
                        (r.bottom + with(density) { 8.dp.toPx() }).roundToInt()
                    )
                }
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Reset", tint = Orange)
            }
        }
    }
}
