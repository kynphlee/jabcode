# 4. Colour modes and conformance

<!-- objective: An operator can classify every supported colour count (2, 4, 8, 16, 32, 64, 128, 256) as ISO-standard or implementation extension and state the interchange consequence of each choice. -->

**In this chapter you will** learn which of the eight colour counts this library accepts are actually part of the ISO standard, which are extensions — and exactly what each choice commits you to when someone else's scanner meets your code.

**You should already** know that colour count sets bits per module and that a field called `Nc` inside the symbol records the colour mode ([01-what-a-jab-code-is.md](01-what-a-jab-code-is.md)); knowing how capacity scales ([02-capacity-size-robustness.md](02-capacity-size-robustness.md)) helps you appreciate why the temptation of more colours exists.

## Nc: one small number, eight big choices

The colour mode of a symbol is a 3-bit metadata field, `Nc`, with the rule colour count = 2^(Nc+1). The public header states the accepted range plainly: "Valid values: 2, 4, 8, 16, 32, 64, 128, 256 (= 2^(Nc+1) for Nc=0..7). 0 = auto (default)" — that last sentence describing the decoder-side preference knob, whose default is to try modes automatically.
<!-- anchor: src/jabcode/include/jabcode.h:105 -->

The standard maps the same eight mode values in its Annex G, Table G.2:

| `Nc` (mode) | Colour count per Table G.2 |
|---|---|
| 0 | reserved |
| 1 | 4 colours |
| 2 (default) | 8 colours |
| 3 | 16 colours |
| 4 | 32 colours |
| 5 | 64 colours |
| 6 | 128 colours |
| 7 | 256 colours |

<!-- anchor: ISO 23634 Annex G Table G.2 -->

Look closely at that first row. Mode 0 is *reserved* — the standard does not assign it 2 colours or anything else. Hold that thought; it matters below.

## What the standard actually defines

Only two of the eight modes are fully defined, interoperable JAB Code: **4-colour (mode 1)** and **8-colour (mode 2, the default)**. Everything else is covered by one sentence in the body of the standard: "Colour modes 0, 3, 4, 5, 6 and 7 are reserved for future extensions. These colour modes can also be used for user-defined colour modes."
<!-- anchor: ISO 23634 4.4.1.2 -->

For the high-colour modes, Annex G (which is informative, not normative) offers palette guidance, framed for exactly one audience: "If more than eight colours are used for closed, user defined applications, the following guideline should be considered." *Closed* is the operative word — both the encoder and every reader are under your control. For the 4-colour mode the annex also names the palette: "When using the 4-colour mode, black, cyan, magenta, and yellow should be used." (Those four will ring a bell for anyone who has changed printer cartridges — [05-printing-and-scanning.md](05-printing-and-scanning.md) makes the connection.)
<!-- anchor: ISO 23634 Annex G.3 a), G.1 a) -->

## Mode 0: the 2-colour extension

This implementation accepts `--color-number 2`, and the writer's source is candid about where that came from: "WS-0: Accept color_number=2 (Nc=0, Mode 0 monochrome). WS-3: Accept color_number=256 (Nc=7, max-density mode)." The fork repurposes the reserved mode 0 as a black-and-white monochrome mode.
<!-- anchor: src/jabcodeWriter/jabwriter.c:147-148 -->

Be very clear about its standing: a 2-colour mode does not exist anywhere in ISO/IEC 23634 — not in the body, not in the annexes; Table G.2 lists mode 0 only as "reserved". A 2-colour symbol produced here is a *reference-implementation extension*, decodable by this codebase and nothing else. That can be a perfectly good engineering choice (a laser-etched metal tag has no colours to offer), but it is a private dialect, not the standard's language.
<!-- anchor: ISO 23634 Annex G Table G.2; docs/manuals/corpus-model.md §4 (Mode 0 extension row) -->

## The classification, complete

Here is the whole family in one table — the answer this chapter promised:

| Colour count | `Nc` | Standing | Interchange consequence |
|---|---|---|---|
| 2 | 0 | Implementation extension (mode 0 is "reserved" in the standard; no 2-colour mode exists in ISO 23634) | Decodes only with this implementation. Closed-loop even among JAB Code readers. |
| 4 | 1 | ISO-defined | Interoperable with any conforming reader. |
| 8 | 2 | ISO-defined, and both the standard's and this library's default | Interoperable with any conforming reader. |
| 16 | 3 | Reserved mode, usable as user-defined | Closed, user-defined applications only. |
| 32 | 4 | Reserved mode, usable as user-defined | Closed, user-defined applications only. |
| 64 | 5 | Reserved mode, usable as user-defined | Closed, user-defined applications only. |
| 128 | 6 | Reserved mode, usable as user-defined | Closed, user-defined applications only. |
| 256 | 7 | Reserved mode, usable as user-defined | Closed, user-defined applications only. |

<!-- anchor: ISO 23634 4.4.1.2; ISO 23634 Annex G Table G.2 -->
<!-- anchor: src/jabcodeWriter/jabwriter.c:132, 149-155 (writer accepts 2,4,8,16,32,64,128,256; "default:8"); src/jabcode/include/jabcode.h:33 -->

The rule of thumb that falls out of it: **if a code will ever be scanned by software you do not ship, use 4 or 8 colours.** Everything else trades interchange for density, and the trade is only sound when you control both ends — the encoder, every reader, and every future reader.

What "closed" costs you in practice: a symbol written in a reserved mode gives another vendor's conforming reader nothing to work with — the standard defines no palette, no thresholds, no behaviour for it. The failure will not be graceful degradation; it is simply not their language. And the 2-colour mode narrows the circle further still: not even other JAB Code implementations that honour Annex G will know what mode 0 means, because the standard says nothing for them to honour.
<!-- anchor: ISO 23634 4.4.1.2; ISO 23634 Annex G.3 a) -->

## Worked example: classifying two proposals

Two requests land on your desk. Walk each through the table.

**Proposal 1: `--color-number 16` for an internal warehouse system.** Sixteen colours means `Nc` = 3 — a reserved mode, usable as user-defined. Is the application closed? Yes: your encoders, your handheld readers, your firmware schedule. Verdict: permissible as a user-defined mode under 4.4.1.2, with Annex G.3's guidance applying; you gain 4 bits per module instead of 3. Write the closure assumption down in the system's documentation, because the day a partner's scanner enters the warehouse, this choice is the reason it reads nothing.
<!-- anchor: ISO 23634 4.4.1.2; ISO 23634 Annex G.3 a); src/jabcode/include/jabcode.h:105 -->

**Proposal 2: `--color-number 2` for supplier-facing shipping labels.** Two colours means the fork's mode 0 — not a reserved-but-mappable mode, but a construct absent from the standard entirely. Supplier-facing means open interchange, the one thing this mode cannot do. Verdict: reject. Counter-offer the 4-colour mode (`Nc` = 1): ISO-defined, interoperable, and its recommended palette of black, cyan, magenta and yellow is friendly to ordinary print processes. If the labels are truly monochrome-only (thermal printers), a JAB Code is the wrong symbology for that channel — better an honest QR code than a private dialect.
<!-- anchor: src/jabcodeWriter/jabwriter.c:147-148; ISO 23634 Annex G Table G.2, G.1 a) -->

## Try it

1. Classify `--color-number 64`: what `Nc` is it, what is its standing, and who can read it?
2. Which two colour counts are safe for open interchange with any conforming reader?
3. True or false: "2-colour is the standard's mode 0."
4. A colleague argues that since Table G.2 maps mode 7 to 256 colours, 256-colour symbols are standard-conforming for interchange. What is the flaw?

<details><summary>Answers</summary>

1. `Nc` = 5 (64 = 2^6). A reserved mode usable as user-defined — readable only within a closed application where you control every reader.
   <!-- anchor: ISO 23634 Annex G Table G.2; ISO 23634 4.4.1.2 -->
2. 4 and 8 colours (modes 1 and 2) — the only ISO-defined modes.
   <!-- anchor: ISO 23634 4.4.1.2 -->
3. False. Mode 0 is reserved in the standard, with no colour count assigned; the 2-colour interpretation exists only in this implementation.
   <!-- anchor: ISO 23634 Annex G Table G.2; src/jabcodeWriter/jabwriter.c:147-148 -->
4. Table G.2 maps the mode *number*, but clause 4.4.1.2 still classes modes 3–7 as reserved/user-defined, and Annex G's own framing is for "closed, user defined applications" — a mapping in an informative annex does not make a mode interoperable.
   <!-- anchor: ISO 23634 4.4.1.2; ISO 23634 Annex G.3 a) -->

</details>

## Where to go next

Next: [05-printing-and-scanning.md](05-printing-and-scanning.md) — how colour choice, error correction and module size play out on real printers, screens and cameras. Deeper: how the decoder identifies and classifies module colours at scan time — including this fork's adaptive palette machinery — is Developer's Manual (JC-T) territory, forthcoming.
