package com.jabauth.diagnostic.ui.scanner

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

/**
 * Free the scanner from the app-wide portrait lock while it is on screen, and
 * put the lock back on the way out.
 *
 * ### Why not just drop `screenOrientation="portrait"` from the manifest?
 *
 * Because the rest of the diagnostic app — Dashboard, Errors, Test, Settings —
 * is portrait-only Compose that has never been laid out for a short, wide
 * viewport. Unlocking globally would make every one of those screens a
 * landscape bug. The scanner is the only surface that gains anything from
 * rotating, so it is the only one that unlocks.
 *
 * ### Why the scanner needs it
 *
 * The analysis frame arrives in sensor (landscape) orientation and is rotated
 * into the view's orientation before decoding. With the activity pinned to
 * portrait that rotation is always 90 degrees, so the frame's LONG axis is
 * always the screen's short axis — a wide symbol can never reach the wide side
 * of the frame no matter how the user holds the phone. Letting the scanner
 * rotate puts the frame's 1920-pixel axis across the symbol's wide axis.
 *
 * ### Orientation mode
 *
 * [ActivityInfo.SCREEN_ORIENTATION_SENSOR] deliberately ignores the system
 * rotation lock: a user who scans a wide cascade with rotation locked would
 * otherwise have no way to reach landscape, which is the whole point. It also
 * excludes reverse-portrait on most devices, keeping the flip set small.
 * Reverse-*landscape* is still reachable and does not resize the view, which is
 * why `Camera2Preview` listens for display changes rather than relying on
 * layout callbacks alone.
 */
@Composable
internal fun UnlockOrientationWhileVisible() {
    val context = LocalContext.current
    DisposableEffect(context) {
        val activity = context.findActivity()
        if (activity == null) {
            Log.w("ScannerOrientation", "No host Activity; leaving orientation locked")
            return@DisposableEffect onDispose { }
        }
        val previous = activity.requestedOrientation
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
        Log.i("ScannerOrientation", "Scanner entered: orientation unlocked (was $previous)")
        onDispose {
            activity.requestedOrientation = previous
            Log.i("ScannerOrientation", "Scanner left: orientation restored to $previous")
        }
    }
}

/**
 * Walk the [ContextWrapper] chain to the hosting [Activity]. Compose's
 * `LocalContext` is usually the activity itself, but it can be a wrapper (a
 * themed context, or a `ContextThemeWrapper` inserted by a host view), so the
 * cast has to be a search rather than an assumption.
 */
private fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
