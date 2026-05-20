#!/bin/bash
# WS-4 Step 4.8 — threshold sweep driver
#
# Builds libjabcode 4× with different compile-flag combinations, runs
# test_roundtrip_with_noise against each, and emits a labeled comparison
# report. WS-4.7's empirical matrix is the input; this script produces
# the data WS-4.8 needs to decide whether to flip default flags ON in
# the production Makefile for WS-4.9 final regression.
#
# Compile-flag combinations:
#   baseline    no flags                                  (default Makefile state)
#   lab         -DUSE_LAB_DISTANCE                        (4.2 perceptual ΔE2000)
#   fpcal       -DUSE_FP_CALIBRATION                      (4.4 FP-core normalization)
#   both        -DUSE_LAB_DISTANCE -DUSE_FP_CALIBRATION   (combined)
#
# Run from src/jabcode/:
#   bash scripts/ws4_8_threshold_sweep.sh
#
# Output: /tmp/ws4_8_results.txt + stdout report
# See: docs/jabcode-all-nc-plan/00-CHECKLIST.md item 4.8

set -e

JABCODE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$JABCODE_DIR"

RESULTS_FILE="/tmp/ws4_8_results.txt"
> "$RESULTS_FILE"

CONFIGS=(
  "baseline|"
  "lab|-DUSE_LAB_DISTANCE"
  "fpcal|-DUSE_FP_CALIBRATION"
  "both|-DUSE_LAB_DISTANCE -DUSE_FP_CALIBRATION"
)

echo "================================================" | tee -a "$RESULTS_FILE"
echo "WS-4 Step 4.8 — threshold sweep" | tee -a "$RESULTS_FILE"
echo "Started: $(date -Iseconds)" | tee -a "$RESULTS_FILE"
echo "================================================" | tee -a "$RESULTS_FILE"

for cfg in "${CONFIGS[@]}"; do
  name=${cfg%%|*}
  flags=${cfg#*|}

  echo "" | tee -a "$RESULTS_FILE"
  echo "─── Config: $name ─── ($flags)" | tee -a "$RESULTS_FILE"

  # Rebuild library with the requested flags
  make clean >/dev/null 2>&1
  if [ -n "$flags" ]; then
    make CFLAGS="-O2 -std=c11 -fPIC $flags" >/dev/null 2>&1
  else
    make >/dev/null 2>&1
  fi

  # Rebuild the test binary against the new library (otherwise it links
  # against the previous build's libjabcode.so via cached object files)
  gcc -O2 -std=c11 -I. -I./include \
      test/test_roundtrip_with_noise.c \
      -L./build -ljabcode -ltiff -lpng16 -lz -lm \
      -o test/test_roundtrip_with_noise \
      -Wl,-rpath,"$JABCODE_DIR/build" 2>&1 | tee -a "$RESULTS_FILE"

  # Run the test — matrix goes to stderr, library diagnostics to stdout (suppressed)
  LD_LIBRARY_PATH="$JABCODE_DIR/build" ./test/test_roundtrip_with_noise \
      1>/dev/null 2>>"$RESULTS_FILE" || true
done

# Restore default build at the end so the working state matches Makefile default
echo "" | tee -a "$RESULTS_FILE"
echo "─── Restore default build ───" | tee -a "$RESULTS_FILE"
make clean >/dev/null 2>&1
make >/dev/null 2>&1
echo "Default build restored (no -D flags)" | tee -a "$RESULTS_FILE"

echo "" | tee -a "$RESULTS_FILE"
echo "================================================" | tee -a "$RESULTS_FILE"
echo "WS-4.8 sweep complete. Full report: $RESULTS_FILE" | tee -a "$RESULTS_FILE"
echo "================================================" | tee -a "$RESULTS_FILE"
