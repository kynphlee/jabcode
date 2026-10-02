package com.jabcode.panama;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/**
 * Minimal libc {@code free()} bridge for native buffers that JABCode functions
 * hand to the caller.
 *
 * <p>{@code saveImageToMemory} returns a {@code malloc}'d PNG buffer, and
 * {@code readImageFromMemory} and {@code readImage} each return a {@code calloc}'d
 * bitmap (see jabcode {@code image.c}). {@code decodeJABCode} returns the
 * {@code malloc}'d {@code jab_data} that {@code decodeData} builds
 * ({@code decoder.c}). All of them transfer ownership to the caller. The Java
 * side must release them, otherwise every encode/decode leaks native memory.</p>
 */
final class NativeMemory {

    private static final MethodHandle FREE = Linker.nativeLinker().downcallHandle(
        Linker.nativeLinker().defaultLookup().findOrThrow("free"),
        FunctionDescriptor.ofVoid(ValueLayout.ADDRESS)
    );

    private NativeMemory() {}

    /**
     * Free a native pointer previously returned by one of those JABCode functions.
     * No-op for {@code NULL}/empty segments.
     */
    static void free(MemorySegment ptr) {
        if (ptr == null || ptr.address() == 0) {
            return;
        }
        try {
            FREE.invokeExact(ptr);
        } catch (Throwable t) {
            throw new RuntimeException("native free() failed", t);
        }
    }
}
