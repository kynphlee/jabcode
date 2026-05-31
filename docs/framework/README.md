# JABCode Android Framework Integration Guide

Guidance for third-party Android applications integrating the JABCode SDK
(modules under `jabauth-android/framework/`). The diagnostic-app
(`jabauth-android/diagnostic-app/`) is an internal debugging tool that
exercises these patterns; consumer apps should follow the patterns
documented here, not copy diagnostic-app code wholesale.

## Sub-documents

| File | Topic |
|---|---|
| [camera-integration.md](camera-integration.md) | Camera2 patterns: LEVEL_3 preference, AWB/AE convergence-lock, manual WB override |
| [diagnostic-controls.md](diagnostic-controls.md) | Decoder verbose-marker propagation, permissive-color-classification opt-in API |
| [build-config-patterns.md](build-config-patterns.md) | Per-variant `BuildConfig` defaults for diagnostic vs production posture |
| [../framework/jabcode-sdk/docs/CAMERA_CONFIGURATION_GUIDE.md](../framework/jabcode-sdk/docs/CAMERA_CONFIGURATION_GUIDE.md) | Original camera configuration reference (PR #36) — superset of `camera-integration.md` below |

## Empirical caveat

All patterns documented in this directory were derived from empirical
investigation on a single device (Galaxy S25 / SM-S938U-16) against a
single fixture (8-color JABCode on screen) during the 2026-05-30/31
investigation cycle. Consumer apps SHOULD validate behavior on their
target devices and SHOULD treat the patterns as defaults to be
overridden when their measurements show analogous failure signatures.

The Cassandra register entries under `docs/cassandra-register/` (e.g.
`H_nc2_decode_failure.md`) document the empirical record that drove
each pattern; read those before deviating from any pattern documented
here.

## Stability posture

These sub-documents track patterns that have shipped to the
diagnostic-app and are reasonably stable. Patterns under active
investigation (not yet shipped) live in the relevant Cassandra register
entry rather than here.

A pattern is promoted from "register entry" to a sub-doc here when ALL
the following hold:

1. It has shipped in a merged PR
2. The PR's empirical validation cycle is complete (not falsified)
3. Its production posture (default ON / OFF / opt-in) is documented
4. Cross-Nc applicability is at least partially characterized
