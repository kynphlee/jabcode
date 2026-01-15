PREFIX =
CC 	= $(PREFIX)gcc
AR 	= $(PREFIX)ar
RANLIB	= $(PREFIX)ranlib
# Production build with debug symbols
CFLAGS	= -g -O2 -std=c11 -fPIC -D_POSIX_C_SOURCE=199309L
# AddressSanitizer for memory debugging
ASAN_CFLAGS = -g -O0 -std=c11 -fPIC -fsanitize=address -fno-omit-frame-pointer

CORE_DIR := build
STATIC_LIB := $(CORE_DIR)/libjabcode.a
SHARED_LIB := $(CORE_DIR)/libjabcode.so
ASAN_SHARED_LIB := $(CORE_DIR)/libjabcode_asan.so

SOURCES := $(wildcard *.c)
# Exclude test programs with main() from library
LIB_SOURCES := $(filter-out test-png-timing.c create-test-image.c, $(SOURCES))
OBJECTS := $(LIB_SOURCES:.c=.o)
ASAN_OBJECTS := $(LIB_SOURCES:.c=_asan.o)

.PHONY: all clean asan

all: $(STATIC_LIB) $(SHARED_LIB)

asan: $(ASAN_SHARED_LIB)

$(CORE_DIR):
	mkdir -p $(CORE_DIR)

$(STATIC_LIB): $(CORE_DIR) $(OBJECTS)
	$(AR) cru $@ $(OBJECTS)
	$(RANLIB) $@

$(SHARED_LIB): $(CORE_DIR) $(OBJECTS)
	$(CC) -shared -o $@ $(OBJECTS) -lpng16 -lz

$(ASAN_SHARED_LIB): $(CORE_DIR) $(ASAN_OBJECTS)
	$(CC) -shared -fsanitize=address -o $@ $(ASAN_OBJECTS) -lpng16 -lz

%.o: %.c
	$(CC) -c -I. -I./include $(CFLAGS) $< -o $@

%_asan.o: %.c
	$(CC) -c -I. -I./include $(ASAN_CFLAGS) $< -o $@

clean:
	rm -f $(STATIC_LIB) $(SHARED_LIB) $(ASAN_SHARED_LIB) $(OBJECTS) $(ASAN_OBJECTS)
