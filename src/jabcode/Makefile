PREFIX =
CC 	= $(PREFIX)gcc
AR 	= $(PREFIX)ar
RANLIB	= $(PREFIX)ranlib
CFLAGS	= -O2 -std=c11 -fPIC

CORE_DIR := build
STATIC_LIB := $(CORE_DIR)/libjabcode.a
SHARED_LIB := $(CORE_DIR)/libjabcode.so

SOURCES := $(wildcard *.c)
OBJECTS := $(SOURCES:.c=.o)

.PHONY: all clean bench test-pn test-symid

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
	rm -f $(STATIC_LIB) $(SHARED_LIB) $(OBJECTS) $(CORE_DIR)/bench_codec $(CORE_DIR)/test_pn_index $(CORE_DIR)/test_symbology_id

# Suite A: native codec microbenchmark -- encode+decode timing across Nc 0..7.
# Statically links libjabcode for a self-contained binary (adb-pushable).
# Run: $(CORE_DIR)/bench_codec [warmup] [iters]   (JSON on stdout, table on stderr)
bench: $(STATIC_LIB)
	$(CC) -O2 -std=c11 -I. -I./include test/bench_codec.c $(STATIC_LIB) -lpng16 -ltiff -lz -lm -o $(CORE_DIR)/bench_codec
	@echo "built $(CORE_DIR)/bench_codec"

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
