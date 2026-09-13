# WASM-in-browser vs native decode — A/B table

- corpus: 62 images; pixel-identity verified on 57, mismatched (excluded from rates): 5
- success rule: SHA-256(decoded bytes) == clean-source hash, both paths
- native rates = R0 rig probe; native latency = quiet probe (no diag-verbose/fd-capture overhead — the rig's instrumented times are not comparable to the quiet WASM runs); rate agreement between the two native probes is asserted per image
- wasm/simd = browser canvas ImageData -> gamutlock-wasm (quiet wrapper)

| colours | condition | n | native | wasm | wasm-simd | native med ms | wasm med ms | simd med ms |
|---|---|---|---|---|---|---|---|---|
| 4 | blur@1.0 | 1 | 100% | 100% | 100% | 6.6 | 4.2 | 11.4 |
| 4 | blur@2.0 | 1 | 0% | 0% | 0% | 4.7 | 4.4 | 4.1 |
| 4 | blur@3.0 | 1 | 0% | 0% | 0% | 4.4 | 3.7 | 3.5 |
| 4 | chroma@0.3 | 1 | 100% | 100% | 100% | 3.1 | 2.9 | 2.7 |
| 4 | chroma@0.5 | 1 | 100% | 100% | 100% | 3.8 | 2.8 | 2.9 |
| 4 | chroma@0.7 | 1 | 100% | 100% | 100% | 3.1 | 3.3 | 2.8 |
| 4 | clean | 1 | 100% | 100% | 100% | 2.3 | 2.7 | 2.9 |
| 4 | downscale@3 | 1 | 100% | 100% | 100% | 2.0 | 3.1 | 2.7 |
| 4 | downscale@4 | 1 | 100% | 100% | 100% | 1.5 | 2.7 | 2.5 |
| 4 | downscale@6 | 1 | 100% | 100% | 100% | 1.4 | 2.7 | 2.5 |
| 4 | jpeg@30 | 1 | 100% | 100% | 100% | 1.5 | 3.5 | 3.1 |
| 4 | jpeg@50 | 1 | 100% | 100% | 100% | 1.4 | 4.0 | 3.6 |
| 4 | jpeg@70 | 1 | 100% | 100% | 100% | 1.4 | 4.4 | 2.5 |
| 4 | lighting@0.3 | 1 | 100% | 100% | 100% | 2.0 | 4.4 | 3.6 |
| 4 | lighting@0.5 | 1 | 100% | 100% | 100% | 2.0 | 3.7 | 3.9 |
| 4 | lighting@0.7 | 1 | 100% | 100% | 100% | 2.0 | 4.8 | 7.0 |
| 4 | perspective@20 | 1 | 100% | 100% | 100% | 6.3 | 13.1 | 13.2 |
| 4 | perspective@30 | 1 | 100% | 100% | 100% | 6.3 | 11.1 | 9.8 |
| 4 | perspective@35 | 1 | 100% | 100% | 100% | 6.3 | 11.2 | 12.2 |
| 8 | blur@1.0 | 1 | 100% | 100% | 100% | 2.7 | 2.7 | 2.4 |
| 8 | blur@2.0 | 1 | 100% | 100% | 100% | 1.6 | 2.9 | 2.5 |
| 8 | blur@3.0 | 1 | 100% | 100% | 100% | 1.6 | 3.2 | 2.6 |
| 8 | chroma@0.3 | 1 | 100% | 100% | 100% | 1.5 | 4.3 | 3.8 |
| 8 | chroma@0.5 | 1 | 100% | 100% | 100% | 1.5 | 4.0 | 3.5 |
| 8 | chroma@0.7 | 1 | 0% | 0% | 0% | 2.2 | 5.0 | 6.2 |
| 8 | clean | 1 | 100% | 100% | 100% | 1.5 | 3.7 | 3.1 |
| 8 | downscale@3 | 1 | 100% | 100% | 100% | 1.5 | 6.7 | 2.6 |
| 8 | downscale@4 | 1 | 100% | 100% | 100% | 1.5 | 7.7 | 3.5 |
| 8 | downscale@6 | 1 | 100% | 100% | 100% | 1.5 | 9.2 | 5.3 |
| 8 | jpeg@30 | 1 | 100% | 100% | 100% | 1.6 | 6.7 | 6.1 |
| 8 | jpeg@50 | 1 | 100% | 100% | 100% | 1.6 | 4.0 | 10.2 |
| 8 | jpeg@70 | 1 | 100% | 100% | 100% | 1.6 | 4.5 | 11.8 |
| 8 | lighting@0.3 | 1 | 100% | 100% | 100% | 2.1 | 20.4 | 9.1 |
| 8 | lighting@0.5 | 1 | 100% | 100% | 100% | 2.1 | 9.3 | 4.5 |
| 8 | lighting@0.7 | 1 | 100% | 100% | 100% | 2.0 | 4.3 | 4.6 |
| 8 | perspective@20 | 1 | 100% | 100% | 100% | 6.6 | 11.9 | 10.1 |
| 8 | perspective@30 | 1 | 100% | 100% | 100% | 6.2 | 11.8 | 9.8 |
| 8 | perspective@35 | 1 | 100% | 100% | 100% | 6.1 | 23.5 | 10.9 |
| 16 | blur@1.0 | 1 | 100% | 100% | 100% | 35.5 | 50.7 | 45.3 |
| 16 | blur@2.0 | 1 | 100% | 100% | 100% | 2.3 | 4.7 | 3.9 |
| 16 | blur@3.0 | 1 | 100% | 100% | 100% | 2.3 | 8.1 | 4.5 |
| 16 | chroma@0.3 | 1 | 100% | 100% | 100% | 2.2 | 4.4 | 5.0 |
| 16 | chroma@0.5 | 1 | 100% | 100% | 100% | 2.9 | 5.5 | 5.0 |
| 16 | chroma@0.7 | 1 | 0% | 0% | 0% | 3.3 | 8.5 | 5.6 |
| 16 | clean | 1 | 100% | 100% | 100% | 2.2 | 6.1 | 3.4 |
| 16 | downscale@3 | 1 | 100% | 100% | 100% | 2.3 | 8.4 | 3.9 |
| 16 | downscale@4 | 1 | 100% | 100% | 100% | 2.2 | 5.7 | 3.6 |
| 16 | downscale@6 | 1 | 100% | 100% | 100% | 2.3 | 4.2 | 3.5 |
| 16 | jpeg@30 | 1 | 100% | 100% | 100% | 2.3 | 4.4 | 4.4 |
| 16 | jpeg@50 | 1 | 100% | 100% | 100% | 2.3 | 4.4 | 7.7 |
| 16 | jpeg@70 | 1 | 100% | 100% | 100% | 2.3 | 4.3 | 6.9 |
| 16 | lighting@0.3 | 1 | 100% | 100% | 100% | 2.8 | 5.2 | 7.3 |
| 16 | lighting@0.5 | 1 | 100% | 100% | 100% | 3.0 | 5.5 | 14.9 |
| 16 | lighting@0.7 | 1 | 100% | 100% | 100% | 4.0 | 6.8 | 8.3 |
| 16 | perspective@20 | 1 | 100% | 100% | 100% | 7.7 | 13.6 | 11.9 |
| 16 | perspective@30 | 1 | 100% | 100% | 100% | 7.3 | 15.3 | 12.8 |
| 16 | perspective@35 | 1 | 0% | 0% | 0% | 7.5 | 16.0 | 14.4 |

## Pixel-identity mismatches (excluded from rate rows)

- analyzer-frame-01_video-00-02.040: native 3eb901e9bb0b vs wasm e29c9adf9a06 / simd e29c9adf9a06
- analyzer-frame-10_video-00-08.757: native 3e83ecdb0825 vs wasm 60e3465a0d3b / simd 60e3465a0d3b
- analyzer-frame-20_video-00-16.087: native ced1b542907d vs wasm c970aea02239 / simd c970aea02239
- analyzer-frame-30_video-00-23.441: native 208342afb877 vs wasm 71a86831234a / simd 71a86831234a
- analyzer-frame-40_video-00-30.727: native fa00b1e48787 vs wasm 7cbb09f36982 / simd 7cbb09f36982
