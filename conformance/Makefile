# Conformance golden-vector generator.
#
# Builds libjabcode (static) then the generator, and runs it from the REPO ROOT
# so the OUT_PATH "conformance/vectors.jsonl" lands here next to this Makefile.
#
# Usage (from repo root OR from conformance/):
#   make -C conformance vectors     # build lib + generator, regenerate vectors.jsonl
#   make -C conformance validate    # regenerate, then JSON-parse every line
#   make -C conformance clean
#
# The generator links the same flags as src/jabcode/Makefile's bench/sweep targets:
#   -I<codec include>, build/libjabcode.a, -lpng16 -ltiff -lz -lm

# Directory of this Makefile (conformance/) and the repo root (its parent).
SELF_DIR := $(abspath $(dir $(lastword $(MAKEFILE_LIST))))
ROOT     := $(abspath $(SELF_DIR)/..)
CODEC    := $(ROOT)/src/jabcode
LIB      := $(CODEC)/build/libjabcode.a

CC      ?= gcc
CFLAGS  := -O2 -std=c11 -D_POSIX_C_SOURCE=199309L -I$(CODEC) -I$(CODEC)/include
LDLIBS  := -lpng16 -ltiff -lz -lm

BIN     := $(SELF_DIR)/gen_vectors
SRC     := $(SELF_DIR)/gen_vectors.c
OUT     := $(SELF_DIR)/vectors.jsonl

.PHONY: all vectors validate clean lib

all: vectors

# Build the reference static library via the codec's own Makefile.
lib:
	$(MAKE) -C $(CODEC) build/libjabcode.a

$(LIB):
	$(MAKE) -C $(CODEC) build/libjabcode.a

$(BIN): $(SRC) $(LIB)
	$(CC) $(CFLAGS) $(SRC) $(LIB) $(LDLIBS) -o $(BIN)

# Run from the repo root so the relative OUT_PATH resolves to conformance/.
vectors: $(BIN)
	cd $(ROOT) && $(BIN)
	@echo "generated $(OUT)"

# Regenerate then assert every line is valid JSON.
validate: vectors
	@python3 $(SELF_DIR)/validate.py $(OUT)

clean:
	rm -f $(BIN) $(OUT)
