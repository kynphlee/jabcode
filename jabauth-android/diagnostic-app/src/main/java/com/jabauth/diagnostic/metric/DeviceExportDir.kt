package com.jabauth.diagnostic.metric

import android.content.Context
import java.io.File

/**
 * Resolves the app-scoped external files directory the Stage-1b exporter writes into, and builds a
 * [DeviceVerifyAttemptExporter] rooted there.
 *
 * This is the only Android-aware part of the export path. The exporter itself takes a plain [File] so
 * it stays JVM-unit-testable; this helper just supplies the right on-device directory.
 *
 * The export lives under `getExternalFilesDir(null)/[DeviceVerifyAttemptExporter.EXPORT_SUBDIR]` —
 * app-scoped external storage, so it survives reinstall-adjacent inspection and is reachable over
 * `adb pull` / the device file manager without extra permissions, yet is auto-removed on uninstall.
 * Falls back to internal `filesDir` if external storage is unavailable.
 */
fun Context.deviceVerifyAttemptExporter(): DeviceVerifyAttemptExporter {
    val base = getExternalFilesDir(null) ?: filesDir
    val dir = File(base, DeviceVerifyAttemptExporter.EXPORT_SUBDIR)
    return DeviceVerifyAttemptExporter(dir)
}
