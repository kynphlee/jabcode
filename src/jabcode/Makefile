PREFIX =
CC 	= $(PREFIX)gcc
AR 	= $(PREFIX)ar
RANLIB	= $(PREFIX)ranlib
# _POSIX_C_SOURCE exposes clock_gettime / CLOCK_MONOTONIC (used by the opt-in
# decode stage profiler in decode_profile.h) under -std=c11. Defined globally so
# the feature macro precedes every system-header include in every TU.
CFLAGS	= -O2 -std=c11 -fPIC -D_POSIX_C_SOURCE=199309L

CORE_DIR := build
STATIC_LIB := $(CORE_DIR)/libjabcode.a
SHARED_LIB := $(CORE_DIR)/libjabcode.so

SOURCES := $(wildcard *.c)
OBJECTS := $(SOURCES:.c=.o)

.PHONY: all clean bench profile test-pn test-symid test-eci test-table15 test-roundtrip

all: $(STATIC_LIB) $(SHARED_LIB)

$(CORE_DIR):
	mkdir -p $(CORE_DIR)

$(STATIC_LIB): $(CORE_DIR) $(OBJECTS)
	$(AR) cru $@ $(OBJECTS)
	$(RANLIB) $@

$(SHARED_LIB): $(CORE_DIR) $(OBJECTS)
	$(CC) -shared -o $@ $(OBJECTS) -lpng16 -lz

%.o: %.c
	$(CC) -c -I. -I./include $(CFLAGS) $< -o $@

clean:
	rm -f $(STATIC_LIB) $(SHARED_LIB) $(OBJECTS) $(CORE_DIR)/bench_codec $(CORE_DIR)/bench_profile $(CORE_DIR)/test_pn_index $(CORE_DIR)/test_symbology_id $(CORE_DIR)/test_eci $(CORE_DIR)/test_table15 $(CORE_DIR)/test_text_roundtrip

# Suite A: native codec microbenchmark -- encode+decode timing across Nc 0..7.
# Statically links libjabcode for a self-contained binary (adb-pushable).
# Run: $(CORE_DIR)/bench_codec [warmup] [iters]   (JSON on stdout, table on stderr)
bench: $(STATIC_LIB)
	$(CC) -O2 -std=c11 -I. -I./include test/bench_codec.c $(STATIC_LIB) -lpng16 -ltiff -lz -lm -o $(CORE_DIR)/bench_codec
	@echo "built $(CORE_DIR)/bench_codec"

# Per-stage decode profiling harness -- attributes decode latency to the major
# pipeline stages (DETECT, PALETTE, COLOR_CLASSIFY, DEINTERLEAVE, LDPC,
# DATA_DECODE) across colour modes Nc=4..256, using the opt-in profiler. It also
# breaks DETECT into its sub-stages (DETECT_BINARIZE, DETECT_FINDER,
# DETECT_TRANSFORM, DETECT_SAMPLE), which sum to the DETECT roll-up. Like bench,
# statically links libjabcode for a self-contained binary; emits per-stage and
# per-sub-stage microseconds as JSON on stdout and two human tables on stderr.
#   scripts/plot_stage_profile.py    -> stacked pipeline-stage chart
#   scripts/plot_detect_substage.py  -> stacked DETECT-sub-stage chart
# Run: $(CORE_DIR)/bench_profile [warmup] [iters]   (defaults: 5 30)
profile: $(STATIC_LIB)
	$(CC) -O2 -std=c11 -D_POSIX_C_SOURCE=199309L -I. -I./include test/bench_profile.c $(STATIC_LIB) -lpng16 -ltiff -lz -lm -o $(CORE_DIR)/bench_profile
	@echo "built $(CORE_DIR)/bench_profile"

# Regression guard for the pn_index() FP-UB fix. Self-contained -- pn_index is a
# static inline in pseudo_random.h, so no libjabcode link is needed.
test-pn: $(CORE_DIR)
	$(CC) -O2 -std=c11 -I. test/test_pn_index.c -o $(CORE_DIR)/test_pn_index
	$(CORE_DIR)/test_pn_index

# Regression guard for the Annex H symbology identifier (Table H.1). Self-contained
# -- the formatter is a header-only function in symbology_id.h, no libjabcode link.
test-symid: $(CORE_DIR)
	$(CC) -O2 -std=c11 -I. test/test_symbology_id.c -o $(CORE_DIR)/test_symbology_id
	$(CORE_DIR)/test_symbology_id

# Bit-level regression guard for ECI decoding (ISO/IEC 23634 5.3.9 / 7.3). The
# encoder emits no ECI, so we feed decodeData hand-crafted bit streams and assert
# the "\nnnnnn" transmission output + the Annex H "]j1" modifier. Links libjabcode
# for decodeData / jabGetSymbologyIdentifier.
test-eci: $(STATIC_LIB)
	$(CC) -O2 -std=c11 -I. -I./include test/test_eci.c $(STATIC_LIB) -lpng16 -ltiff -lz -lm -o $(CORE_DIR)/test_eci
	$(CORE_DIR)/test_eci

# Bit-level guard for ISO 23634 Table 15 / FNC1 / 7.3 backslash-doubling.
# Hand-crafted bit streams (encoder emits none of these); links libjabcode.
test-table15: $(STATIC_LIB)
	$(CC) -O2 -std=c11 -I. -I./include test/test_table15.c $(STATIC_LIB) -lpng16 -ltiff -lz -lm -o $(CORE_DIR)/test_table15
	$(CORE_DIR)/test_table15

# Text-mode regression guard: encode->decode multi-mode strings byte-identical.
test-roundtrip: $(STATIC_LIB)
	$(CC) -O2 -std=c11 -I. -I./include test/test_text_roundtrip.c $(STATIC_LIB) -lpng16 -ltiff -lz -lm -o $(CORE_DIR)/test_text_roundtrip
	$(CORE_DIR)/test_text_roundtrip
