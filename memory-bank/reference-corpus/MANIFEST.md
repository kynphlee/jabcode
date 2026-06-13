# JABCode reference corpus v2 — verified 4 to 256

Regenerated 2026-06-13 from swift-java-poc `39de8df` (decoder commit `d486388`),
superseding the corrupt `memory-bank/reference-images/` (whose 16 to 256 images fail to decode on every decoder).
Each image self-describes its mode and is verified to round-trip on the swift-lineage decoder.

| file | Nc | colours | symbol px | sha256 | decode-verify |
|------|----|---------|-----------|--------|---------------|
| jabcode_4.png | 1 | 4 | 300x300 | 3376e324c9e6… | OK |
| jabcode_8.png | 2 | 8 | 252x252 | 440e6b124132… | OK |
| jabcode_16.png | 3 | 16 | 300x300 | 953e4826867f… | OK |
| jabcode_32.png | 4 | 32 | 300x300 | 2b8c1c2bd67f… | OK |
| jabcode_64.png | 5 | 64 | 300x300 | 37c15bb4965a… | OK |
| jabcode_128.png | 6 | 128 | 300x300 | b96155533bbf… | OK |
| jabcode_256.png | 7 | 256 | 300x300 | 2a60f2b9f035… | OK |

## Cross-branch differential (documents the capability boundary)

Decoding this swift-generated corpus with each branch's decoder:

| mode | panama-poc decoder | swift-java-poc decoder |
|------|--------------------|------------------------|
| 4, 8 (Nc 1-2) | decode | decode |
| 16-256 (Nc 3-7) | **fail** | decode |

panama reads only 4-8 of swift's output: swift's >8-colour palette/metadata
format is not backward-readable by panama-poc's older decoder. Production runs
the swift lineage, so this corpus is a faithful fixture for the shipping decoder.
