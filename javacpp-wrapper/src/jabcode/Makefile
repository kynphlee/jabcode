PREFIX 	=
CC 	= $(PREFIX)gcc
AR 	= $(PREFIX)ar
RANLIB	= $(PREFIX)ranlib
CFLAGS	= -O2 -std=c11 -fPIC

TARGET_STATIC = build/libjabcode.a
TARGET_SHARED = build/libjabcode.so

OBJECTS := $(patsubst %.c,%.o,$(wildcard *.c))

all: $(TARGET_STATIC) $(TARGET_SHARED)

$(TARGET_STATIC): $(OBJECTS)
	$(AR) cru $@ $?
	$(RANLIB) $@

$(TARGET_SHARED): $(OBJECTS)
	$(CC) -shared -Wl,-soname,libjabcode.so -o $@ $^ -lm -lpng16 -lz

$(OBJECTS): %.o: %.c
	$(CC) -c -I. -I./include $(CFLAGS) $< -o $@

clean:
	rm -f $(TARGET_STATIC) $(TARGET_SHARED) $(OBJECTS)
