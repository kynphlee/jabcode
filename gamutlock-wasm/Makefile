# gamutlock-wasm — bridge #3: the fork's codec compiled to WebAssembly.
#
# Same portable C source as the JNI and Swift bridges (src/jabcode); this
# Makefile only adds a browser shell. Requires an activated Emscripten SDK
# (emcc on PATH — https://emscripten.org, `emsdk install latest`).
#
# WHAT IS EXCLUDED, AND WHY (bundle honesty):
#   image.c             PNG/TIFF *file* I/O only. The web reader decodes raw
#                       RGBA from canvas ImageData — the same file-free path
#                       the mobile bridge uses — so libpng/libtiff/zlib never
#                       enter the module. Nothing else in the codec calls
#                       readImage/saveImage outside #if TEST_MODE blocks.
#   detector_synthetic.c  decodeJABCodeSynthetic only: the encoder-metadata
#                       bypass used by synthetic round-trip tests. Never part
#                       of the camera decode path.
# Everything else — including the fork's decode-rate work (adaptive_palette,
# color_calibration, kdtree_color, lab_color, strict Part-II gate in decoder,
# decode_profile) — is compiled in.

EMCC  ?= emcc
CODEC := ../src/jabcode
DIST  := dist

CODEC_SRCS := adaptive_palette.c binarizer.c color_calibration.c decode_profile.c \
              decoder.c detector.c encoder.c interleave.c kdtree_color.c lab_color.c \
              ldpc.c mask.c pseudo_random.c sample.c transform.c

SRCS := wasm_bridge.c $(addprefix $(CODEC)/,$(CODEC_SRCS))

CFLAGS := -O3 -flto -std=c11 -D_POSIX_C_SOURCE=199309L -I$(CODEC) -I$(CODEC)/include

# _gw_* is the whole bridge ABI; _malloc/_free let the wrapper manage scratch.
EXPORTS := _gw_frame_buffer,_gw_decode_frame,_gw_result_data,_gw_result_length,_gw_result_color_count,_gw_result_status,_gw_result_corner_x,_gw_result_corner_y,_gw_last_error,_gw_version,_gw_set_preferred_color_count,_gw_set_diag_verbose,_malloc,_free

# FILESYSTEM=0: the raw-bitmap path never touches a file; dropping the FS
# shrinks the JS glue. STACK_SIZE=1MiB: the detector keeps per-symbol state on
# the stack; the Emscripten 64KiB default is uncomfortably close.
LDFLAGS := -sMODULARIZE=1 -sEXPORT_ES6=1 -sEXPORT_NAME=createGamutlockCodec \
           -sALLOW_MEMORY_GROWTH=1 -sSTACK_SIZE=1048576 \
           -sENVIRONMENT=web,worker,node -sFILESYSTEM=0 \
           -sEXPORTED_FUNCTIONS=$(EXPORTS) \
           -sEXPORTED_RUNTIME_METHODS=HEAPU8,UTF8ToString

.PHONY: all simd size clean fixture smoke

all: $(DIST)/gamutlock-codec.mjs

$(DIST)/gamutlock-codec.mjs: wasm_bridge.c $(addprefix $(CODEC)/,$(CODEC_SRCS)) Makefile
	mkdir -p $(DIST)
	$(EMCC) $(SRCS) $(CFLAGS) $(LDFLAGS) -o $@

# Second-pass variant per the handoff: fixed-width 128-bit WASM SIMD via LLVM
# autovectorization. Same source, same ABI; served from dist-simd/ so the A/B
# bench can measure both builds side by side.
simd: wasm_bridge.c $(addprefix $(CODEC)/,$(CODEC_SRCS)) Makefile
	mkdir -p dist-simd
	$(EMCC) $(SRCS) $(CFLAGS) -msimd128 $(LDFLAGS) -o dist-simd/gamutlock-codec.mjs

size: $(DIST)/gamutlock-codec.mjs
	@ls -l $(DIST)/gamutlock-codec.wasm $(DIST)/gamutlock-codec.mjs | awk '{print $$5, $$9}'
	@gzip -9 -c $(DIST)/gamutlock-codec.wasm | wc -c | awk '{print $$1, "$(DIST)/gamutlock-codec.wasm (gzip -9)"}'

# Node-side guard: encode a fixture natively (host codec), decode it through
# the WASM wrapper, byte-compare. Needs the host static lib:
#   make -C ../src/jabcode build/libjabcode.a
BRIDGE_DIR := ../swift-java-wrapper

fixture: tools/make_fixture.c
	mkdir -p build test/fixtures
	cc -O2 -std=c11 -I$(CODEC) -I$(CODEC)/include -I$(BRIDGE_DIR)/include \
	    tools/make_fixture.c $(BRIDGE_DIR)/src/c/mobile_bridge.c \
	    $(CODEC)/build/libjabcode.a -lm -o build/make_fixture
	./build/make_fixture test/fixtures/fixture

smoke: all fixture
	node test/node-smoke.mjs

# Pixel-identity receipt tool for the A/B (see tools/rgba_sha.c). Needs the
# host static lib and host libpng/libtiff (same deps as the R0 rig probe).
pxsha: tools/rgba_sha.c
	mkdir -p build
	cc -O2 -std=c11 -I$(CODEC) -I$(CODEC)/include tools/rgba_sha.c \
	    $(CODEC)/build/libjabcode.a -lpng16 -ltiff -lz -lm -o build/rgba_sha

# Latency-fair native probe (no diag-verbose, no fd capture) — see the header
# comment in tools/quiet_probe.c for why the rig probe's times can't be
# compared against the quiet WASM runs.
quietprobe: tools/quiet_probe.c
	mkdir -p build
	cc -O2 -std=c11 -I$(CODEC) -I$(CODEC)/include tools/quiet_probe.c \
	    $(CODEC)/build/libjabcode.a -lpng16 -ltiff -lz -lm -o build/quiet_probe

clean:
	rm -rf $(DIST) dist-simd build test/fixtures
