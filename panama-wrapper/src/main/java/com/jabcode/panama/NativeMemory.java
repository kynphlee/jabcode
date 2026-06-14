package com.jabcode.panama;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/**
 * Minimal libc {@code free()} bridge for native buffers returned by the JABCode
 * in-memory functions.
 *
 * <p>{@code saveImageToMemory} returns a {@code malloc}'d PNG buffer and
 * {@code readImageFromMemory} returns a {@code calloc}'d bitmap; both transfer
 * ownership to the caller (see jabcode {@code image.c}). The Java side must
 * release them, otherwise every in-memory encode/decode leaks native memory.</p>
 */
final class NativeMemory {

    private static final MethodHandle FREE = Linker.nativeLinker().downcallHandle(
        Linker.nativeLinker().defaultLookup().findOrThrow("free"),
        FunctionDescriptor.ofVoid(ValueLayout.ADDRESS)
    );

    private NativeMemory() {}

    /**
     * Free a native pointer previously returned by a JABCode in-memory function.
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
