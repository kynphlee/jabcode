# JAB Code golden-vector conformance suite

`vectors.jsonl` is the cross-binding oracle for JAB Code encode parameters. Each
line is one **golden vector**: a fixed set of encode parameters plus a fixed
deterministic payload, paired with the **ground-truth geometry** and round-trip
result that the reference **C codec** (`libjabcode`) actually produced.

The C codec is the reference implementation, so its output *is* the golden truth.
Any other binding (Swift, Java/Panama, Android) that encodes the same parameters +
payload must reproduce the same symbol count, the same per-symbol module/pixel
geometry, and the same round-trip outcome. Divergence is a binding bug.

## Files

| File | Purpose |
|------|---------|
| `gen_vectors.c` | Generator. Links `libjabcode`, encodes the vector matrix, writes `vectors.jsonl`. |
| `vectors.jsonl` | The golden vectors, one JSON object per line. **Generated artifact** — regenerate, don't hand-edit. |
| `validate.py` | Asserts every line is valid JSON, has the required schema, and the create-site vector is modules 21 / px 252. |
| `Makefile` | `vectors` (build + regenerate), `validate` (regenerate + check), `clean`. |

## Regenerate

```sh
# from the repo root, or anywhere:
make -C conformance vectors      # builds libjabcode (static) + the generator, rewrites vectors.jsonl
make -C conformance validate     # regenerate, then JSON-parse + schema-check every line
make -C conformance clean
```

The generator is run **from the repo root** by the Makefile so its output path
`conformance/vectors.jsonl` resolves here. It writes via `fopen()` (not stdout)
on purpose: the encoder prints `JABCode Error: ...` diagnostics to stdout on
overflow, and the negative-overflow vector deliberately triggers that path — so
stdout cannot be used for the JSONL stream.

Link flags mirror `src/jabcode/Makefile` (`bench`/`sweep` targets):
`-I src/jabcode -I src/jabcode/include`, `src/jabcode/build/libjabcode.a`,
`-lpng16 -ltiff -lz -lm`.

## JSON schema (one object per line)

```jsonc
{
  "id": "create_site_8c_v1_ms12_ecc0",   // stable identifier for this vector
  "params": {
    "colorNumber":    8,                  // Nc palette size: 2,4,8,...,256
    "eccLevel":       0,                  // master ecc level; spec range 1..10 (Table 20), 0=unset->default 3
    "symbolNumber":   1,                  // symbol count (1 = single, >1 = cascade)
    "symbolVersions": [[1,1]],            // per-symbol [version_x, version_y]; [0,0] = auto-sized
    "moduleSize":     12                  // pixels per module (default 12)
  },
  "payload_b64": "SkFCQ29kZS1jb25mb3JtYW5jZS12MQ==",  // base64 of the exact input bytes
  "expect": {
    "symbol_count": 1,                    // symbols the encoder emitted (0 = encode failed)
    "symbols": [                          // per-symbol ground-truth geometry
      { "modules_x":21, "modules_y":21, "px_x":252, "px_y":252 }
    ],
    "roundtrip": true                     // encode -> decodeJABCode -> bytes equal input
  }
}
```

### Geometry truth

```
side_modules = 4 * version + 17     // VERSION2SIZE in src/jabcode/include/jabcode.h
px           = side_modules * moduleSize
```

For the canonical create-site case (8-colour, moduleSize 12, version (1,1), ecc 0):
`4*1 + 17 = 21` modules, `21 * 12 = 252` px. Confirmed by `validate.py`.

### How a vector is produced

`createEncode(colorNumber, symbolNumber)`, then on the returned `jab_encode`:
`module_size`, per-symbol `symbol_versions[i].{x,y}`, `symbol_ecc_levels[i]`, and —
for cascades (`symbolNumber>1`) — `symbol_positions[i] = i` (sequential dock indices
are edge-adjacent in `jab_symbol_pos`; index 0 is the master). Then
`generateJABCode(enc, data)`. The geometry is read back from
`enc->symbols[i].side_size`. The round-trip self-check pins the decoder's colour
count (`jabSetPreferredColorCount`) to the known encode `colorNumber` — collapsing
the auto-detect fallback ladder to the correct mode — then `decodeJABCode` and
compares the returned bytes to the input.

## Vector matrix (28 vectors)

**Single-symbol** — colorNumber {2, 8, 128} x version {(1,1),(6,6)} x ecc {1, 3},
symbolNumber 1, moduleSize 12. ECC levels run 1..10 per ISO/IEC 23634:2022
Table 20; this matrix exercises the minimum (1) and default (3). Plus the explicit
create-site vector `create_site_8c_v1_ms12_ecc0`, which uses ecc 0 to exercise the
"unset -> default 3" normalization (still modules 21 / px 252, round-trips).

**ECC-level sweep** — `ecc_sweep_8c_L1` .. `ecc_sweep_8c_L10`: 8-colour,
auto-sized single symbol, one per ECC level 1..10. The generator gates on all ten
round-tripping and on the in-code `ecclevel2wcwr` rows equaling Table 20.

**Cascade** — 8-colour multi-symbol:
- `cascade_8c_n2_v66_v44`: symbolNumber 2, versions [[6,6],[4,4]].
- `cascade_8c_n4_v66`: symbolNumber 4, uniform (6,6).
- `cascade_8c_n8_v66`: symbolNumber 8, uniform (6,6).

**Negative / overflow** — a 600-byte payload:
- `overflow_single_8c_v1`: one 8-colour v(1,1) symbol — too small, encode fails.
- `overflow_resolved_8c_n2_v66`: same payload, 2-symbol cascade — fits, round-trips.

## Reading the `roundtrip` and `symbol_count` fields honestly

These fields record **what the reference codec actually does**, not an idealized
result. Several entries are deliberate negative/edge cases — they are golden truth
about the codec's real boundaries, and any conforming binding must reproduce them:

- `symbol_count == 0` means `generateJABCode` produced no bitmap (encode failure).
  - `overflow_single_8c_v1`: payload exceeds a single v(1,1) symbol's capacity.
  - `cascade_8c_n2_v66_v44`: docked symbols must share the side dimension where
    they dock; a (6,6) host docked to a (4,4) slave is rejected by the encoder
    ("different side version"). This vector documents that constraint.
- `roundtrip == false` on a successfully-encoded symbol means the reference
  decoder could not recover the payload from the pristine bitmap. The
  `single_2c_v6_*` (a 41-module 2-colour symbol holding only 22 bytes is extremely
  sparse -> alignment-pattern detection fails) and `single_128c_v1_*` (128-colour
  palette discrimination at the smallest version) cases are genuine codec limits,
  recorded faithfully.

The **geometry** (`symbols[]`) is captured whenever the symbol was emitted,
independent of round-trip success, and is the primary conformance signal.
