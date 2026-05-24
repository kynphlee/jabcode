# Full-Spectrum JABCode Reference Set

Canonical PNG samples covering every color mode `Nc=0..7` (8 modes total),
generated against the post-WS-4.5.4-and-Bug-E jabcode codebase to provide a
known-good reference set for visual inspection, decoder regression testing,
and Android/Panama wrapper validation.

This set supersedes the earlier `Documents/QR Code research/short list/JAB Codes/Official images`
set (dated 2025-10-05) which (a) lacked Nc=0 entirely and (b) was generated
by an older encoder whose Nc=3..7 bit layout the current decoder no longer
accepts — see `04d-ws4_5_4-determinism-fix.md` for the wire-format-evolution
discussion.

## Files in this set

| File | Nc | Colors | Dimensions | Module size | Payload | Notable workstream |
|---|---:|---:|---|---:|---|---|
| `nc0-2c-20260521.png` | 0 | 2 (mono) | 252×252 | 12 px | `HELLO-Nc-0` | WS-0 Mode 0 monochrome |
| `nc1-4c-20260521.png` | 1 | 4 | 252×252 | 12 px | `HELLO-Nc-1` | baseline |
| `nc2-8c-20260521.png` | 2 | 8 | 252×252 | 12 px | `HELLO-Nc-2` | baseline |
| `nc3-16c-20260521.png` | 3 | 16 | 300×300 | 12 px | `HELLO-Nc-3` | WS-4.5.4 Bug E fix engaged |
| `nc4-32c-20260521.png` | 4 | 32 | 300×300 | 12 px | `HELLO-Nc-4` | Bug E + WS-4.5.1 calibration |
| `nc5-64c-20260521.png` | 5 | 64 | 300×300 | 12 px | `HELLO-Nc-5` | Bug E |
| `nc6-128c-20260521.png` | 6 | 128 | 300×300 | 12 px | `HELLO-Nc-6` | Bug E + interpolatePalette 128-color |
| `nc7-256c-20260521.png` | 7 | 256 | 300×300 | 12 px | `HELLO-Nc-7` | WS-3 + Bug E + interpolatePalette 256-color |

Note the symbol-size breakpoint at Nc=3: modes Nc=0/1/2 fit the payload in
a 21-module side (12 px × 21 = 252 px), modes Nc=3..7 require a 25-module
side (12 px × 25 = 300 px). The encoder selects symbol version
automatically to fit payload + ECC at the configured color mode.

## Canonical md5s

```
7a59f85aa4fac5b67a8cd7ee2e0547bd  nc0-2c-20260521.png
7d424b292768e87b58f50582183ee394  nc1-4c-20260521.png
7705561c15b031094721e39589528c54  nc2-8c-20260521.png
2cdabc784cc84a8e366771da33a1b681  nc3-16c-20260521.png
9f5e9f4e35a9e8cbe35506b3c0f71496  nc4-32c-20260521.png
02c984e859ce86f75b82acb120efe5ed  nc5-64c-20260521.png
200faf2fda6d2c7afbcaec5595c9a083  nc6-128c-20260521.png
95ccaed71d443432e369dc5c5a65d4e8  nc7-256c-20260521.png
```

These hashes will change byte-for-byte if either jabcode commit or the
encoder's bit layout evolves. If a regeneration produces different bytes
but the round-trip still succeeds, that's a backward-compat signal worth
noting in `04d-ws4_5_4-determinism-fix.md`.

## Provenance

- **jabcode tip:** `99d5bd8` — *docs(jabcodeWriter): update --color-number help text to reflect WS-0 / WS-3 unlock* on branch `claude/ws-0-3-help-text-modes`
- **encoder commit lineage:** `99d5bd8 ← 8bbf9b8 (WS-4.5.4 Bug E) ← 17b06a5 ← 099ab85 ← c2c2ee2 (WS-4.5.4 A/B/D) ← 1435de3 (WS-4.5.3)`
- **libjabcode.so md5:** `5a423c65919decc7adff87e2ecd17823` (linux-x86_64, -O2, built in `.claude/worktrees/ws-0-3-help-text-modes/`)
- **jabcodeWriter md5:** `5bcee33d3161a3f89a295b3b20196b2e`
- **jabcodeReader md5:** `e4ca4c920d7a42cdb9b9488d077a41c4`
- **Generated:** 2026-05-21
- **Method:** in-process encode → write PNG → re-decode roundtrip-verify, 8/8 PASS

## How to regenerate

From a worktree checked out at jabcode `swift-java-poc` (or any descendant
with the WS-4.5.4 Bug E fix), with `libjabcode.so` built:

```bash
# 1. Build the CLI tools
cd src/jabcode      && make
cd ../jabcodeWriter && make
cd ../jabcodeReader && make

# 2. Generate the spectrum (8 images, one per Nc)
cd <repo-root>
export LD_LIBRARY_PATH="$PWD/src/jabcode/build:$LD_LIBRARY_PATH"
WRITER=./src/jabcodeWriter/bin/jabcodeWriter
OUT_DIR=<this-folder>
DATE=$(date +%Y%m%d)
for COLOR in 2 4 8 16 32 64 128 256; do
  Nc=$(python3 -c "import math; print(int(math.log2($COLOR))-1)")
  "$WRITER" --input "HELLO-Nc-$Nc" \
            --output "$OUT_DIR/nc${Nc}-${COLOR}c-${DATE}.png" \
            --color-number "$COLOR"
done

# 3. Roundtrip-verify each image
READER=./src/jabcodeReader/bin/jabcodeReader
for f in "$OUT_DIR"/nc*.png; do
  out=$(mktemp)
  "$READER" "$f" --output "$out" 2>/dev/null
  echo "$(basename "$f"): $(cat "$out")"
  rm -f "$out"
done
```

Expected output of the verify loop:

```
nc0-2c-20260521.png: HELLO-Nc-0
nc1-4c-20260521.png: HELLO-Nc-1
nc2-8c-20260521.png: HELLO-Nc-2
nc3-16c-20260521.png: HELLO-Nc-3
nc4-32c-20260521.png: HELLO-Nc-4
nc5-64c-20260521.png: HELLO-Nc-5
nc6-128c-20260521.png: HELLO-Nc-6
nc7-256c-20260521.png: HELLO-Nc-7
```

If any line fails to round-trip, that's an empirical regression in the
encoder/decoder pipeline — start with `test_roundtrip_all_nc.c` in the
jabcode repo and the WS-4.5.4 diagnostic notes.

## Intended uses

1. **Decoder regression baseline** — re-running the round-trip script
   above with a new libjabcode tip is a fast sanity gate. Diff against
   the expected output; any line missing or mismatched is a regression.
2. **Visual inspection / printing** — the PNGs can be displayed on a
   monitor or printed to test the Android wrapper's Camera2 + JNI path
   end-to-end (see `swift-java-wrapper/android/testapp` and
   `jabauth-android/diagnostic-app`). Higher-Nc images are visibly
   denser and require steadier capture conditions.
3. **Panama JAR validation** — load each PNG via
   `PanamaJabCodeService.decodeJabCode(BufferedImage)` and assert the
   recovered payload matches the file's `nc{N}-` prefix. Mirrors the
   `PanamaJabCodeServiceIntegrationTest.testColorModeMapping` Java test
   but with disk-backed images instead of in-memory bitmaps.
4. **Wire-format archaeology** — if a future encoder change causes
   round-trip regressions, the canonical md5s above pin "this is what
   the encoder used to produce on 2026-05-21." Diffing pixel-by-pixel
   against the regenerated PNGs surfaces exactly what bit layouts moved.

## Cross-references

- `00-CHECKLIST.md` — workstream status (WS-0 ✅, WS-3 ✅, WS-4.5.x ✅)
- `04d-ws4_5_4-determinism-fix.md` — WS-4.5.4 + Bug E technical narrative
- `04c-ws4_5_3-determinism-investigation.md` — antecedent investigation
- `00b-mode-0-monochrome.md` — WS-0 Mode 0 design notes
- jabcode commit `99d5bd8` (this set's exact encoder)
- jabcode `swift-java-poc` branch — canonical jabcode line for COA-crypto consumption
