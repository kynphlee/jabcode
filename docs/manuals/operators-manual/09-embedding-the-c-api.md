# 9. Embedding the C API

<!-- objective: A developer-literate operator can write and run a minimal C program that encodes a string and decodes it back (round trip), using the five-call flow with correct memory ownership -->

**In this chapter you will** write and run a minimal C program that encodes a string into a JAB Code image and decodes it back — five API calls, with the memory ownership rules that keep it leak-free.

**You should already** have built the library ([chapter 6](06-building-the-library.md)) and used the CLI tools ([chapter 7](07-encoding-with-jabcodewriter.md), [chapter 8](08-decoding-with-jabcodereader.md)) — every struct field you set here corresponds to a writer flag you have already used. Working C literacy is assumed.

## The five-call flow

Everything you need is declared in `jabcode.h` (`src/jabcode/include/jabcode.h`). The round trip is:

1. `createEncode` — allocate and default-initialize the encode object.
2. `generateJABCode` — encode your payload into a bitmap.
3. `saveImage` — write the bitmap to a PNG.
4. `readImage` + `decodeJABCode` — load an image and decode it.
5. `destroyEncode` / `free` — release what each side allocated.

The exact signatures, quoted from the header:

```c
extern jab_encode* createEncode(jab_int32 color_number, jab_int32 symbol_number);
extern void destroyEncode(jab_encode* enc);
extern jab_int32 generateJABCode(jab_encode* enc, jab_data* data);
extern jab_data* decodeJABCode(jab_bitmap* bitmap, jab_int32 mode, jab_int32* status);
extern jab_boolean saveImage(jab_bitmap* bitmap, jab_char* filename);
extern jab_bitmap* readImage(jab_char* filename);
```

<!-- anchor: src/jabcode/include/jabcode.h:217-220, 287, 289 -->

## Building the payload: `jab_data`

The payload container is a length-prefixed flexible-array struct:

```c
typedef struct {
	jab_int32	length;
	jab_char	data[];
}jab_data;
```

<!-- anchor: src/jabcode/include/jabcode.h:136-139 -->

You allocate it yourself — `malloc(sizeof(jab_data) + payload_length)`, set `length`, and copy the bytes in. This is exactly what the writer CLI does with your `--input` string, so it is the reference pattern. <!-- anchor: src/jabcodeWriter/jabwriter.c:80-87 -->

## `createEncode` and the fields you may set

`createEncode(color_number, symbol_number)` returns a defaulted `jab_encode*`, or `NULL` on out-of-memory. It is forgiving: an invalid colour count silently falls back to `DEFAULT_COLOR_NUMBER` (8) and an out-of-range symbol number to `DEFAULT_SYMBOL_NUMBER` (1) — so a typo'd `createEncode(3, 1)` gives you an 8-colour encoder, not an error. Module size starts at `DEFAULT_MODULE_SIZE` (12). <!-- anchor: src/jabcode/encoder.c:182-204; src/jabcode/include/jabcode.h:31-33 -->

The struct you get back:

```c
typedef struct {
	jab_int32		color_number;
	jab_int32		symbol_number;
	jab_int32		module_size;
	jab_int32		master_symbol_width;
	jab_int32		master_symbol_height;
	jab_byte*		palette;				///< Palette holding used module colors in format RGB
	jab_vector2d*	symbol_versions;
	jab_byte* 		symbol_ecc_levels;
	jab_int32*		symbol_positions;
	jab_symbol*		symbols;				///< Pointer to internal representation of JAB Code symbols
	jab_bitmap*		bitmap;
	jab_int32		mask_type;				///< Mask pattern type used (0-7)
}jab_encode;
```

<!-- anchor: src/jabcode/include/jabcode.h:172-185 -->

Between `createEncode` and `generateJABCode`, an SDK user sets the same knobs the writer flags expose — and only those: `module_size`, `master_symbol_width`, `master_symbol_height`, and the per-symbol arrays `symbol_ecc_levels[i]`, `symbol_versions[i]`, `symbol_positions[i]` (all pre-allocated to `symbol_number` entries). The writer CLI's own post-parse code is the canonical example of this assignment pattern. Treat `symbols` and `bitmap` as library-owned outputs. <!-- anchor: src/jabcodeWriter/jabwriter.c:453-473 -->

## `generateJABCode` returns 0 on success — mind the convention flip

The header defines `JAB_SUCCESS` as `1` and `JAB_FAILURE` as `0`, and boolean-returning functions like `saveImage` follow it. `generateJABCode` does **not** — its documented contract is:

> `@return 0:success | 1: out of memory | 2:no input data | 3:incorrect symbol version or position | 4: input data too long`

So the correct success test is `generateJABCode(enc, data) == 0`; testing against `JAB_SUCCESS` would invert your logic. The writer CLI checks `!= 0` for failure, which is the pattern to copy. On success, `enc->bitmap` holds the rendered symbol. <!-- anchor: src/jabcode/include/jabcode.h:47-48; src/jabcode/encoder.c:2305; src/jabcodeWriter/jabwriter.c:476 -->

## Saving, and who frees what

`saveImage(enc->bitmap, "out.png")` writes a PNG and returns `JAB_SUCCESS`/`JAB_FAILURE`; `saveImageCMYK(bitmap, isCMYK, filename)` writes a CMYK TIFF, converting from RGB when `isCMYK` is false. <!-- anchor: src/jabcode/include/jabcode.h:287-288; src/jabcode/image.c:122-128 -->

Ownership on the encode side is all-in on `destroyEncode`: it frees the palette, the per-symbol arrays, every symbol's internals, **and `enc->bitmap`**. Two consequences:

- Save the image **before** calling `destroyEncode` — the bitmap dies with the encoder.
- Never `free(enc->bitmap)` yourself; you would double-free.

Your own `jab_data` payload is yours: plain `free()` when done. <!-- anchor: src/jabcode/encoder.c:261-280 -->

## Reading back and decoding

`readImage(filename)` returns a `jab_bitmap*` (or `NULL` on failure) that you release with plain `free()`. `decodeJABCode(bitmap, mode, &status)` takes mode `NORMAL_DECODE` (0) or `COMPATIBLE_DECODE` (1) and reports a status the header's implementation documents as: "0: not detectable, 1: not decodable, 2: partly decoded with COMPATIBLE\_DECODE mode, 3: fully decoded". It returns the decoded payload as a `jab_data*` — again released with plain `free()` — or `NULL` on failure. The reader CLI follows exactly this sequence, including the two `free()` calls. <!-- anchor: src/jabcode/include/jabcode.h:50-51, 220, 289; src/jabcode/detector.c:4235; src/jabcodeReader/jabreader.c:47-92 -->

## The fork's opt-in toggles, in one paragraph

This fork adds process-global, default-off switches you may meet in diagnostic work: `jabSetDiagVerbose(1)` opens a gated firehose of per-iteration decoder markers (default off to protect decode latency); `jabSetPreferredColorCount(n)` pins the decoder to one colour count — "Valid values: 2, 4, 8, 16, 32, 64, 128, 256 (= 2^(Nc+1) for Nc=0..7). 0 = auto (default)" — instead of walking the Nc fallback ladder; and `jabSetPermissiveColorClassification(1)` enables a camera-tolerance colour substitution at the metadata stage. Leave all three at their defaults unless you are chasing a specific decode failure; their rationale and internals belong to the Developer's Manual (JC-T), forthcoming. <!-- anchor: src/jabcode/include/jabcode.h:247-271, 105 -->

## Worked example: a complete round trip

Save this as `src/roundtrip/roundtrip.c` in the repo (any sibling of the tool directories works — the include and link paths below assume that layout):

```c
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "jabcode.h"

int main(void)
{
    const char* message = "Hello world";
    jab_int32 msg_len = (jab_int32)strlen(message);

    /* 1. Build the payload: caller-allocated jab_data */
    jab_data* data = (jab_data*)malloc(sizeof(jab_data) + msg_len);
    if (data == NULL)
        return 1;
    data->length = msg_len;
    memcpy(data->data, message, msg_len);

    /* 2. Create the encode object: 8 colours, 1 symbol */
    jab_encode* enc = createEncode(8, 1);
    if (enc == NULL) {
        free(data);
        return 1;
    }

    /* 3. Encode. NOTE: 0 means success here (not JAB_SUCCESS). */
    if (generateJABCode(enc, data) != 0) {
        destroyEncode(enc);
        free(data);
        return 1;
    }

    /* 4. Save BEFORE destroyEncode -- it frees enc->bitmap. */
    if (!saveImage(enc->bitmap, "roundtrip.png")) {
        destroyEncode(enc);
        free(data);
        return 1;
    }
    destroyEncode(enc);
    free(data);

    /* 5. Read back and decode */
    jab_bitmap* bitmap = readImage("roundtrip.png");
    if (bitmap == NULL)
        return 1;

    jab_int32 status = 0;
    jab_data* decoded = decodeJABCode(bitmap, NORMAL_DECODE, &status);
    free(bitmap);
    if (decoded == NULL) {
        printf("decode failed, status %d\n", status);
        return 1;
    }

    printf("status %d, %d bytes: %.*s\n",
           status, decoded->length, decoded->length, decoded->data);
    free(decoded);
    return 0;
}
```

Compile and link with the same flags and link line the tool Makefiles use (`-O2 -std=c11`; `-L../jabcode/build -ljabcode -L../jabcode/lib -ltiff -lpng16 -lz -lm`) — recall from [chapter 6](06-building-the-library.md) that `-L../jabcode/lib` is inert in this tree and the system libraries satisfy the link:

```sh
cd src/roundtrip
gcc -I. -I../jabcode -I../jabcode/include -O2 -std=c11 roundtrip.c \
    -L../jabcode/build -ljabcode -L../jabcode/lib -ltiff -lpng16 -lz -lm \
    -o roundtrip

LD_LIBRARY_PATH=../jabcode/build ./roundtrip
```

Expected behavior: the program creates `roundtrip.png`, prints its status line — a fully decoded round trip reports status 3 and 11 bytes — and exits 0. Any early return of 1 pinpoints which of the five calls failed. <!-- anchor: src/jabcodeWriter/Makefile:10, 13; src/jabcode/detector.c:4235 -->

## Try it

1. Why must `saveImage` run before `destroyEncode`?
2. `generateJABCode` returned 4. What went wrong, and which two parameters would you revisit?
3. Which deallocator do you use for the `jab_data*` returned by `decodeJABCode`, and why not a library call?
4. You want the decoder to consider only 4-colour symbols. Which single call, before decoding?

<details><summary>Answers</summary>

1. `destroyEncode` frees `enc->bitmap` along with everything else in the encode object; after it runs there is nothing left to save. <!-- anchor: src/jabcode/encoder.c:261-280 -->
2. Code 4 is "input data too long" — the payload did not fit. Revisit the symbol version (or let a single symbol auto-size by leaving versions unset) and consider more symbols or a higher-capacity configuration. <!-- anchor: src/jabcode/encoder.c:2305, 2340-2348 -->
3. Plain `free()` — the decoded `jab_data` is a single `malloc` block, and the reader CLI itself releases it with `free(decoded_data)`. <!-- anchor: src/jabcodeReader/jabreader.c:91 -->
4. `jabSetPreferredColorCount(4);` — it collapses the Nc fallback ladder to the pinned colour count; pass 0 to restore auto-detect. <!-- anchor: src/jabcode/include/jabcode.h:264-270 -->

</details>

## Where to go next

- Next: [chapter 10](10-choosing-parameters.md) turns everything since chapter 2 into a decision guide — which parameters, for which job.
- Deeper: the extended API (`decodeJABCodeEx`, per-symbol results, the synthetic decode path, profiling hooks) is covered in the Developer's Manual (JC-T), forthcoming.
