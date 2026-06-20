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

.PHONY: all clean bench bench-concurrent bench-cascade sweep transcode profile test-pn test-symid test-eci test-table15 test-roundtrip test-concurrent

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
	rm -f $(STATIC_LIB) $(SHARED_LIB) $(OBJECTS) $(CORE_DIR)/bench_codec $(CORE_DIR)/bench_concurrent $(CORE_DIR)/bench_cascade $(CORE_DIR)/bench_profile $(CORE_DIR)/test_pn_index $(CORE_DIR)/test_symbology_id $(CORE_DIR)/test_eci $(CORE_DIR)/test_table15 $(CORE_DIR)/test_text_roundtrip $(CORE_DIR)/test_concurrent_roundtrip

# Suite A: native codec microbenchmark -- encode+decode timing across Nc 0..7.
# Statically links libjabcode for a self-contained binary (adb-pushable).
# Run: $(CORE_DIR)/bench_codec [warmup] [iters]   (JSON on stdout, table on stderr)
bench: $(STATIC_LIB)
	$(CC) -O2 -std=c11 -I. -I./include test/bench_codec.c $(STATIC_LIB) -lpng16 -ltiff -lz -lm -o $(CORE_DIR)/bench_codec
	@echo "built $(CORE_DIR)/bench_codec"

# Concurrent-THROUGHPUT benchmark -- the value PR #110 (reentrant codec) unlocked.
# Runs the SAME 8-colour/256-byte round-trip two ways at each thread count:
# CONCURRENT (reentrant codec, no lock -> throughput scales toward T x) vs
# SERIALIZED (every round-trip behind one global mutex -> the pre-#110 "lock
# everything" reality, throughput stays flat). The gap is what #110 bought.
# Statically links libjabcode (self-contained binary); -lpthread for the threads.
# Run: $(CORE_DIR)/bench_concurrent [duration_ms] [max_threads]   (JSONL stdout, table stderr)
bench-concurrent: $(STATIC_LIB)
	$(CC) -O2 -std=c11 -D_POSIX_C_SOURCE=199309L -I. -I./include test/bench_concurrent.c $(STATIC_LIB) -lpng16 -ltiff -lz -lm -lpthread -o $(CORE_DIR)/bench_concurrent
	@echo "built $(CORE_DIR)/bench_concurrent"

# Multi-symbol (CASCADE) benchmark -- the axis PR #113 (high-colour cascade) made
# sound, and the one every single-symbol harness above cannot see. Sweeps the
# cascade size N (1..61 docked symbols) x colour Nc and reports capacity (~N x
# scaling), encode/decode latency, and density (the per-symbol tiling overhead),
# plus a success matrix over Nc x version x N that doubles as a regression guard for
# #113 and maps the documented v==0-mod-5 high-Nc edge. Statically links libjabcode.
# Run: $(CORE_DIR)/bench_cascade [curves|matrix|both] [warmup] [iters]
#   build/bench_cascade curves > benchmarks/data/cascade.jsonl
#   build/bench_cascade matrix > benchmarks/data/cascade_matrix.jsonl
bench-cascade: $(STATIC_LIB)
	$(CC) -O2 -std=c11 -D_POSIX_C_SOURCE=199309L -I. -I./include test/bench_cascade.c $(STATIC_LIB) -lpng16 -ltiff -lz -lm -o $(CORE_DIR)/bench_cascade
	@echo "built $(CORE_DIR)/bench_cascade"

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

# Comprehensive capacity/latency/ECC sweep for the full-picture benchmark suite.
sweep: $(STATIC_LIB)
	$(CC) -O2 -std=c11 -I. -I./include test/bench_sweep.c $(STATIC_LIB) -lpng16 -ltiff -lz -lm -o $(CORE_DIR)/bench_sweep
	@echo "built $(CORE_DIR)/bench_sweep"

# Encode/decode helper for the transcode-survival benchmark (benchmarks/transcode_survival.py).
transcode: $(STATIC_LIB)
	$(CC) -O2 -std=c11 -I. -I./include test/transcode_tool.c $(STATIC_LIB) -lpng16 -ltiff -lz -lm -o $(CORE_DIR)/transcode_tool
	@echo "built $(CORE_DIR)/transcode_tool"

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

# Codec reentrancy (thread-safety) guard under ThreadSanitizer.
# Spawns N>=8 pthreads x M>=50 encode->decode round-trips over Nc=1..7 and
# asserts each thread decodes its OWN payload byte-identically.
#
# Built with -fsanitize=thread -g, compiling the codec SOURCES (not the
# pre-built static lib) into the binary so TSan instruments the library
# internals -- the shared codec globals live there, so linking the
# non-instrumented $(STATIC_LIB) would hide exactly the races we guard against.
# -O1 keeps TSan's stacks readable; -lpthread for the threads.
#   AFTER the _Thread_local fix:  TSan clean, RESULT: PASS.
#   BEFORE (stash the src change): TSan reports races on the codec globals
#                                  and/or round-trip assertions fail.
test-concurrent: $(CORE_DIR)
	$(CC) -O1 -g -std=c11 -fsanitize=thread -D_POSIX_C_SOURCE=199309L -I. -I./include \
	    test/test_concurrent_roundtrip.c $(SOURCES) \
	    -lpng16 -ltiff -lz -lm -lpthread -o $(CORE_DIR)/test_concurrent_roundtrip
	$(CORE_DIR)/test_concurrent_roundtrip
