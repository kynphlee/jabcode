# Diagnostic Controls

The framework exposes two opt-in diagnostic flags routed from the
Kotlin SDK surface to native C-side globals in `libjabcode-mobile.so`.
Consumer apps integrating these for in-field diagnostics should follow
the propagation patterns documented below.

## The two flags

| Flag | Public Kotlin API | Native global | Default |
|---|---|---|---|
| Verbose markers | `com.jabauth.jabcode.setDiagVerbose(Boolean)` | `g_diag_verbose` | OFF |
| Permissive color classification | `com.jabauth.jabcode.setPermissiveColorClassification(Boolean)` | `g_permissive_color_classification` | OFF |

Both APIs are public facades in the `com.jabauth.jabcode` package over
internal `JABCodeMobile` JNI bindings; consumer apps depend on the
public facade, not on `JABCodeMobile` directly (which is
module-internal to the SDK).

## Propagation chain pattern

The empirical 2026-05-30/31 investigation surfaced a recurring pattern:
diagnostic flags shipped without observability at each propagation link
produce subtle bugs that are hard to diagnose. The recommended pattern
is to log at each link.

```kotlin
// In your Application or MainActivity onCreate:
lifecycleScope.launch {
    settingsRepository.settingsFlow.collect { settings ->
        Log.i("DiagProp", "[C] settings emitted: verbose=${settings.verbose}")
        setDiagVerbose(settings.verbose)
        // (optionally) setPermissiveColorClassification(settings.permissive)
    }
}
```

The framework's `DiagnosticControl.setDiagVerbose()` logs internally at
the `[E1]`/`[E2]` link (entry + post-JNI). Consumer apps should add
`[C]` (Settings emission) and `[F]` (post-call) probes if they need
end-to-end propagation visibility. Cost per probe: ~50 ns and one
logcat line per Settings flow emission — negligible vs the hot-path
camera-thread budget.

## Thread-locality is intentionally NOT used

Both native globals are process-global, not `__thread`. The 2026-05-30
investigation surfaced (and corrected) a bug where thread-local
isolation prevented the UI-thread `setDiagVerbose` from reaching the
analyzer thread that runs the decoder. Consumer apps should NOT wrap
these flags in their own thread-local state.

## When to enable

### `setDiagVerbose(true)`

Use during a tightly-scoped diagnostic capture window. The flag enables
per-iteration markers (`PartI_DIAG`, `DIAG_PALETTE_LEARNED`,
`DIAG_PARTII_RESULT`, `Nc_FALLBACK` retries, `DIAG_MODE0_DETECT`,
`DETECT_SUCCESS`, `GRID_REF`) that cost ~50–300 µs per call on Android's
binder-mediated logcat. A failed decode with full `Nc_FALLBACK`
iteration emits ~24 such lines, adding ~1.5–7 ms per frame.

Terminal markers (`FAIL_ATTR`, `DECODE_OK`, `DIAG_SYMBOL_DECODE` final
result) always fire regardless of this flag.

### `setPermissiveColorClassification(true)`

Documented opt-in for the specific empirical case where camera-side
fixes (manual WB override per `camera-integration.md`) are insufficient
and the residual color cast is still producing systematic `rgb=5` (M)
classifications at metadata module positions. See
`docs/cassandra-register/H_nc2_decode_failure.md` for the empirical
record.

The flag substitutes `rgb=5` (M) → `rgb=6` (Y) at the
`decodeMasterMetadataPartI` module_color stage. It is load-bearing
for the nc=2 33.75% baseline on the reference device but is NOT a
universal fix: it produces `(Y, Y)` invalid pair_bits when the camera
is correctly producing rgb=5 reads, and it can mask camera-side bugs
that would otherwise be diagnosed.

**Production guidance**: do not enable this flag in shipped consumer
apps without first running a representative diagnostic capture to
confirm the camera-side signature matches the H_nc2 case.

## Raw-byte instrumentation pattern

The `PartI_DIAG` markers (when `setDiagVerbose(true)` is active)
include raw RGB byte values alongside the classified output:

```
[PartI_DIAG] module[N] xy=(X,Y) raw_bytes=(B0,B1,B2) rgb=R valid=V
```

Byte order is `[R, G, B]` (confirmed via the inline comment at
`decoder.c:786`). Use the raw bytes to identify the specific cast
direction on consumer-target devices — this is the diagnostic input
needed to design the per-device manual WB tuning per
`camera-integration.md`.
