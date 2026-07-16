# Appendix B. Samples cross-index

**An honest note before the table.** This appendix was planned as a per-image index of the `jabcode-samples/` gallery and its two companion PDFs (`jabcode-samples-gallery.pdf`, `jabcode-supported-variants-brief.pdf`). Those assets are **absent from this working tree**: an existence check found no `jabcode-samples/` directory and neither PDF at the repository root. They are recorded only as untracked files in a *different* clone of this repository — the session-shell mount, whose `git status` listed them; the corpus model documents that the shell mount and this working tree are two different clones, and this manual documents this one. <!-- anchor: docs/manuals/corpus-model.md §1.2 --> Rather than index files you cannot open, this appendix gives you the next best thing: one representative `jabcodeWriter` command per output class, so you can regenerate an equivalent gallery from the tools you built in [chapter 6](06-building-the-library.md).

## Representative commands by output class

Every command uses only the documented flag surface of [chapter 7](07-encoding-with-jabcodewriter.md); parameter rationale lives in [chapter 10](10-choosing-parameters.md). Substitute your own payloads and filenames.

| Output class | Representative command |
|---|---|
| Default symbol — 8 colours, module 12, ECC 3, auto-sized, PNG | `jabcodeWriter --input 'Hello world' --output sample-default.png` <!-- anchor: src/jabcodeWriter/jabwriter.c:55; src/jabcode/include/jabcode.h:31-36 --> |
| ISO-standard 4-colour symbol (print-robust baseline) | `jabcodeWriter --input 'Hello world' --output sample-4c.png --color-number 4` <!-- anchor: src/jabcodeWriter/jabwriter.c:132, 149-155 --> |
| Press-ready CMYK TIFF (4-colour) | `jabcodeWriter --input-file payload.bin --output sample-cmyk.tif --color-number 4 --ecc-level 5 --color-space 1` <!-- anchor: src/jabcodeWriter/jabwriter.c:226, 241-245 --> |
| Hardened symbol — high ECC, larger modules | `jabcodeWriter --input-file label.txt --output sample-hardened.png --color-number 4 --ecc-level 7 --module-size 16` <!-- anchor: src/jabcodeWriter/jabwriter.c:157, 273 --> |
| Fixed-footprint master (pixel-sized) | `jabcodeWriter --input 'Hello world' --output sample-fixed.png --symbol-width 600 --symbol-height 600` <!-- anchor: src/jabcodeWriter/jabwriter.c:173, 189 --> |
| Two-symbol cascade | `jabcodeWriter --input 'Hello world' --output sample-cascade2.png --symbol-number 2 --symbol-position 0 3 --symbol-version 3 2 4 2` <!-- anchor: src/jabcodeWriter/jabwriter.c:58, 397-414 --> |
| Three-symbol cascade (the usage text's own example values) | `jabcodeWriter --input 'Hello world' --output sample-cascade3.png --symbol-number 3 --symbol-position 0 3 2 --symbol-version 3 2 4 2 3 2` <!-- anchor: src/jabcodeWriter/jabwriter.c:58 --> |
| Cascade with per-symbol ECC (master 5, slaves inherit) | `jabcodeWriter --input 'Hello world' --output sample-cascade-ecc.png --symbol-number 3 --symbol-position 0 3 2 --symbol-version 3 2 4 2 3 2 --ecc-level 5 0 0` <!-- anchor: src/jabcodeWriter/jabwriter.c:40-44 --> |
| Mode 0 monochrome — **fork-only extension** | `jabcodeWriter --input 'Hello world' --output sample-mode0.png --color-number 2` <!-- anchor: src/jabcodeWriter/jabwriter.c:147-148 --> |
| 256-colour maximum density — **fork extension** | `jabcodeWriter --input-file big-payload.bin --output sample-256c.png --color-number 256` <!-- anchor: src/jabcodeWriter/jabwriter.c:147-148 --> |

Interchange reminder: the last two classes (and every colour count other than 4 and 8) are not ISO-standard modes — produce them only for closed loops where you control every reader ([chapter 4](04-colour-modes-conformance.md)).

## Regenerating a gallery

To rebuild a local samples set, run the table's commands from the repo root with the tools on your path and `LD_LIBRARY_PATH` set per [chapter 6](06-building-the-library.md), then verify each PNG round-trips with `jabcodeReader` ([chapter 8](08-decoding-with-jabcodereader.md)). The CMYK TIFF is the one deliberate exception — it is a press deliverable the reader does not load, so proof it via an identical-parameter PNG copy, the pattern from [chapter 10](10-choosing-parameters.md)'s worked example.
