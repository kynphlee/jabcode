# R0 decode-rate rig — host harness build.
#
# Links the jabcode core static lib (built from src/jabcode). The lib must be
# built first:  make -C ../../../src/jabcode all
# `make` here builds that lib if missing, then the r0_decode probe.

CC      = gcc
# Feature-test macros are set at the top of r0_decode.c (_XOPEN_SOURCE 700),
# so none are passed on the command line here.
CFLAGS  = -O2 -std=c11 -Wall

# Repo root, resolved relative to this Makefile.
RIG_DIR   := $(dir $(abspath $(lastword $(MAKEFILE_LIST))))
REPO_ROOT := $(abspath $(RIG_DIR)/../../..)
JABCODE   := $(REPO_ROOT)/src/jabcode
LIBJAB    := $(JABCODE)/build/libjabcode.a

TARGET = r0_decode

.PHONY: all clean lib

all: $(TARGET)

# Build the core lib on demand (idempotent).
$(LIBJAB):
	$(MAKE) -C $(JABCODE) all

lib: $(LIBJAB)

$(TARGET): r0_decode.c $(LIBJAB)
	$(CC) $(CFLAGS) -I$(JABCODE) -I$(JABCODE)/include r0_decode.c \
		$(LIBJAB) -lpng16 -ltiff -lz -lm -o $(TARGET)

clean:
	rm -f $(TARGET)
