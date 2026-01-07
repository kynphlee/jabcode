# JNI Wrapper Implementation Audit

## Overview

This document audits the existing JNI wrapper implementation to understand patterns that must be replicated in the Panama FFM version.

**Location:** `/javacpp-wrapper/`

## Architecture

```
Java Layer (com.jabcode.*)
    ↓ [JNI calls]
C++ JNI Wrapper Layer (JABCodeNative_jni.cpp)
    ↓ [C wrapper functions]
C Wrapper Layer (jabcode_c_wrapper.c)
    ↓ [Direct calls]
JABCode C Library (libjabcode)
```

## Key Components

### 1. Java Classes

| Class | Purpose | Lines |
|-------|---------|-------|
| `OptimizedJABCode.java` | High-level API | ~1,600 |
| `JABCodeNative.java` | JavaCPP bindings (generated) | ~400 |
| `JABCodeNativePtr.java` | Low-level native calls | ~200 |
| `SimpleJABCode.java` | Simple wrapper API | ~100 |
| `JABCodePresets.java` | Constants and presets | ~100 |

### 2. Native Layer Files

| File | Purpose | Lines |
|------|---------|-------|
| `JABCodeNative_jni.cpp` | JNI bridge functions | ~444 |
| `jabcode_c_wrapper.c/.cpp` | C API wrapper | ~200 |
| `jabcode_constants.c` | Constant exports | ~100 |

## JNI Call Flow Example: Encoding

### Java Side

```java
// OptimizedJABCode.java
public static BufferedImage encode(byte[] data, int colorCount) {
    // 1. Create encoder pointer
    long encPtr = JABCodeNativePtr.createEncodePtr(colorCount, 1);
    
    // 2. Create data structure
    long dataPtr = allocateJabData(data);
    
    // 3. Generate code
    int result = JABCodeNativePtr.generateJABCodePtr(encPtr, dataPtr);
    
    // 4. Extract bitmap
    int[] argbData = JABCodeNativePtr.bitmapToARGB(encPtr);
    
    // 5. Convert to BufferedImage
    BufferedImage image = createImageFromARGB(argbData);
    
    // 6. Cleanup
    JABCodeNativePtr.destroyEncodePtr(encPtr);
    
    return image;
}
```

### JNI Bridge (C++)

```cpp
// JABCodeNative_jni.cpp
JNIEXPORT jlong JNICALL 
Java_com_jabcode_internal_JABCodeNativePtr_createEncodePtr(
    JNIEnv *env, jclass cls, jint colorNumber, jint symbolNumber) {
    
    // Cast to pointer, return as Java long
    return (jlong)createEncode_c(colorNumber, symbolNumber);
}

JNIEXPORT jint JNICALL 
Java_com_jabcode_internal_JABCodeNativePtr_generateJABCodePtr(
    JNIEnv *env, jclass cls, jlong encPtr, jlong dataPtr) {
    
    // Cast back from Java long to C pointers
    return generateJABCode_c((jab_encode*)encPtr, (jab_data*)dataPtr);
}

JNIEXPORT void JNICALL 
Java_com_jabcode_internal_JABCodeNativePtr_destroyEncodePtr(
    JNIEnv *env, jclass cls, jlong encPtr) {
    
    destroyEncode_c((jab_encode*)encPtr);
}
```

### C Wrapper Layer

```c
// jabcode_c_wrapper.c
jab_encode* createEncode_c(jab_int32 color_number, jab_int32 symbol_number) {
    return createEncode(color_number, symbol_number);
}

jab_int32 generateJABCode_c(jab_encode* enc, jab_data* data) {
    return generateJABCode(enc, data);
}

void destroyEncode_c(jab_encode* enc) {
    destroyEncode(enc);
}
```

## Memory Management Patterns

### 1. Pointer Passing (Opaque Pointers)

**Pattern:** Store C pointers as Java `long` values

```cpp
// Encode to Java long
JNIEXPORT jlong JNICALL createEncodePtr(...) {
    jab_encode* enc = createEncode(...);
    return (jlong)enc;  // Cast pointer to long
}

// Decode from Java long
JNIEXPORT jint JNICALL generateJABCodePtr(..., jlong encPtr, ...) {
    jab_encode* enc = (jab_encode*)encPtr;  // Cast back
    return generateJABCode(enc, ...);
}
```

**Panama Equivalent:**
```java
// MemorySegment is the direct equivalent
MemorySegment enc = createEncode(arena, colorNumber, symbolNumber);
int result = generateJABCode(arena, enc, dataSegment);
```

### 2. String Conversion

**JNI Pattern:**

```cpp
JNIEXPORT jlong JNICALL readImagePtr(JNIEnv *env, jclass cls, jstring filename) {
    // Java String → C string
    const char* filenameChars = env->GetStringUTFChars(filename, NULL);
    
    // Call C function
    jab_bitmap* result = readImage_c((jab_char*)filenameChars);
    
    // Release Java string
    env->ReleaseStringUTFChars(filename, filenameChars);
    
    return (jlong)result;
}
```

**Panama Equivalent:**
```java
try (Arena arena = Arena.ofConfined()) {
    MemorySegment filenamePtr = arena.allocateFrom(filename);
    MemorySegment result = readImage(arena, filenamePtr);
    // Auto-cleanup when arena closes
}
```

### 3. Array Conversion (Bitmap to ARGB)

**JNI Pattern:**

```cpp
JNIEXPORT jintArray JNICALL bitmapToARGB(JNIEnv *env, jclass cls, jlong bitmapPtr) {
    jab_bitmap* bitmap = (jab_bitmap*)bitmapPtr;
    
    // Calculate size
    jint width = bitmap->width;
    jint height = bitmap->height;
    jint totalSize = 2 + (width * height);
    
    // Allocate Java array
    jintArray result = env->NewIntArray(totalSize);
    
    // Allocate temporary buffer
    jint* buffer = new jint[totalSize];
    buffer[0] = width;
    buffer[1] = height;
    
    // Convert pixels
    jab_byte* pixel = bitmap->pixel;
    for (jab_int32 i = 0; i < width * height; i++) {
        jab_byte r = pixel[i * 4 + 0];
        jab_byte g = pixel[i * 4 + 1];
        jab_byte b = pixel[i * 4 + 2];
        buffer[2 + i] = 0xFF000000 | (r << 16) | (g << 8) | b;
    }
    
    // Copy to Java array
    env->SetIntArrayRegion(result, 0, totalSize, buffer);
    delete[] buffer;
    
    return result;
}
```

**Panama Equivalent:**
```java
MemorySegment bitmap = ...;
int width = jab_bitmap.width(bitmap);
int height = jab_bitmap.height(bitmap);
MemorySegment pixelData = jab_bitmap.pixel(bitmap);

// Direct access, no copy needed
int[] pixels = new int[width * height];
for (int i = 0; i < pixels.length; i++) {
    byte r = pixelData.get(ValueLayout.JAVA_BYTE, i * 4 + 0);
    byte g = pixelData.get(ValueLayout.JAVA_BYTE, i * 4 + 1);
    byte b = pixelData.get(ValueLayout.JAVA_BYTE, i * 4 + 2);
    pixels[i] = 0xFF000000 | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
}
```

### 4. Status Output Parameters

**JNI Pattern:**

```cpp
JNIEXPORT jlong JNICALL decodeJABCodePtr(
    JNIEnv *env, jclass cls, jlong bitmapPtr, jint mode, jintArray statusArray) {
    
    jab_int32 status = 0;
    jab_data* result = decodeJABCode_c((jab_bitmap*)bitmapPtr, mode, &status);
    
    // Write status back to Java array
    if (statusArray != NULL) {
        jint* statusPtr = env->GetIntArrayElements(statusArray, NULL);
        statusPtr[0] = status;
        env->ReleaseIntArrayElements(statusArray, statusPtr, 0);
    }
    
    return (jlong)result;
}
```

**Panama Equivalent:**
```java
MemorySegment statusSegment = arena.allocate(ValueLayout.JAVA_INT);
MemorySegment result = decodeJABCode(arena, bitmap, mode, statusSegment);
int status = statusSegment.get(ValueLayout.JAVA_INT, 0);
```

## High-Level Java API Patterns

### OptimizedJABCode Class

```java
/**
 * Builder pattern for encoding
 */
public static class Builder {
    private byte[] data;
    private ColorMode colorMode = ColorMode.OCTAL;  // 8 colors
    private int symbolCount = 1;
    private int eccLevel = 3;
    
    public Builder withData(String data) {
        this.data = data.getBytes();
        return this;
    }
    
    public Builder withColorMode(ColorMode colorMode) {
        this.colorMode = colorMode;
        return this;
    }
    
    public BufferedImage build() {
        return encode(data, colorMode.getColorCount());
    }
}
```

**Usage:**
```java
BufferedImage jabcode = new OptimizedJABCode.Builder()
    .withData("Hello World")
    .withColorMode(ColorMode.OCTAL)
    .build();
```

### Color Mode Enum

```java
public enum ColorMode {
    BINARY(2),          // Black & white
    QUATERNARY(4),      // 4 colors
    OCTAL(8),           // 8 colors (default)
    HEXADECIMAL(16),    // 16 colors
    MODE_32(32),
    MODE_64(64),
    MODE_128(128),
    MODE_256(256);
    
    private final int colorCount;
    
    public int getColorCount() {
        return colorCount;
    }
}
```

## Memory Allocation Patterns

### 1. jab_data Allocation (Flexible Array Member)

**JNI Approach:**
```cpp
// Allocate jab_data with flexible array member
jab_data* allocate_jab_data(const char* data, int length) {
    // Calculate total size
    size_t total_size = sizeof(jab_data) + length;
    
    // Allocate
    jab_data* jd = (jab_data*)malloc(total_size);
    jd->length = length;
    
    // Copy data into flexible array
    memcpy(jd->data, data, length);
    
    return jd;
}
```

**Panama Approach:**
```java
MemorySegment allocateJabData(Arena arena, byte[] data) {
    // Calculate total size: struct header + data array
    long structSize = jab_data.sizeof();
    long totalSize = structSize + data.length;
    
    // Allocate
    MemorySegment jabData = arena.allocate(totalSize);
    
    // Set length field
    jab_data.length(jabData, data.length);
    
    // Copy data into flexible array
    MemorySegment dataStart = jabData.asSlice(structSize, data.length);
    MemorySegment.copy(
        MemorySegment.ofArray(data), 0,
        dataStart, 0,
        data.length
    );
    
    return jabData;
}
```

### 2. Bitmap Access

**JNI:**
```cpp
jab_bitmap* bitmap = enc->bitmap;
int width = bitmap->width;
int height = bitmap->height;
jab_byte* pixels = bitmap->pixel;
```

**Panama:**
```java
MemorySegment bitmap = jab_encode.bitmap(enc);
int width = jab_bitmap.width(bitmap);
int height = jab_bitmap.height(bitmap);
MemorySegment pixels = jab_bitmap.pixel(bitmap);
```

## Error Handling

### JNI Pattern

```cpp
JNIEXPORT jlong JNICALL encodePtr(...) {
    jab_encode* enc = createEncode_c(...);
    if (enc == NULL) {
        return 0;  // Return NULL pointer as 0
    }
    
    int result = generateJABCode_c(enc, data);
    if (result == JAB_FAILURE) {
        destroyEncode_c(enc);
        return 0;
    }
    
    return (jlong)enc;
}
```

### Panama Pattern

```java
public byte[] encode(String data, Config config) {
    try (Arena arena = Arena.ofConfined()) {
        MemorySegment enc = createEncode(arena, config.getColorNumber(), 1);
        if (enc.address() == 0) {
            return null;  // Allocation failed
        }
        
        MemorySegment jabData = allocateJabData(arena, data.getBytes());
        int result = generateJABCode(arena, enc, jabData);
        
        if (result == 0) {  // JAB_FAILURE
            return null;
        }
        
        // Extract bitmap...
        return pixels;
        
    } catch (Exception e) {
        // Handle errors
        return null;
    }
}
```

## Performance Optimizations in JNI

### 1. Object Pooling

```java
// EncoderPool.java - Reuses encoder instances
public class EncoderPool {
    private BlockingQueue<PooledEncoder> pool;
    
    public PooledEncoder acquire() {
        return pool.poll();
    }
    
    public void release(PooledEncoder encoder) {
        pool.offer(encoder);
    }
}
```

**Panama Note:** Can reuse the same pattern with Panama `MemorySegment` instances.

### 2. Direct Buffer Usage

```java
// Use direct ByteBuffers for zero-copy transfers
ByteBuffer directBuffer = ByteBuffer.allocateDirect(size);
```

**Panama:** Memory segments ARE direct buffers by nature.

## Library Loading

### JNI Approach

```java
public class NativeLibraryLoader {
    static {
        // Load native library
        String os = System.getProperty("os.name").toLowerCase();
        String lib = os.contains("win") ? "jabcode.dll" : 
                     os.contains("mac") ? "libjabcode.dylib" : 
                     "libjabcode.so";
        System.loadLibrary(lib);
    }
}
```

### Panama Approach

```java
// Library is loaded automatically via jextract bindings
// Or manually:
Linker linker = Linker.nativeLinker();
SymbolLookup lib = SymbolLookup.libraryLookup("jabcode", arena);
```

## Key Differences: JNI vs Panama

| Aspect | JNI | Panama |
|--------|-----|--------|
| **Wrapper Code** | ~500 lines C++ | 0 lines (pure Java) |
| **Pointers** | Cast to `long` | `MemorySegment` |
| **Strings** | `GetStringUTFChars()` | `arena.allocateFrom()` |
| **Arrays** | `GetIntArrayElements()` | `MemorySegment.toArray()` |
| **Structs** | Manual field access | Generated accessors |
| **Memory** | Manual malloc/free | Arena auto-cleanup |
| **Type Safety** | Runtime only | Compile-time |
| **Build** | Requires C++ compiler | Java only |

## Panama Implementation Checklist

Based on JNI patterns, Panama must implement:

- [ ] **Encoder lifecycle:**
  - [ ] `createEncode()` → return `MemorySegment`
  - [ ] `generateJABCode()` → pass `MemorySegment`
  - [ ] `destroyEncode()` → pass `MemorySegment`

- [ ] **Data structures:**
  - [ ] Allocate `jab_data` with flexible array member
  - [ ] Access `jab_bitmap` fields
  - [ ] Handle `jab_encode` pointer chains

- [ ] **Bitmap conversion:**
  - [ ] Extract RGBA pixels from `jab_bitmap`
  - [ ] Convert to Java `BufferedImage`
  - [ ] Handle different color modes

- [ ] **Decoding:**
  - [ ] `readImage()` from file
  - [ ] `decodeJABCode()` with status output
  - [ ] Extract decoded `jab_data`

- [ ] **Error handling:**
  - [ ] Check for NULL pointers (`address() == 0`)
  - [ ] Validate return codes (JAB_SUCCESS/JAB_FAILURE)
  - [ ] Handle allocation failures

- [ ] **High-level API:**
  - [ ] Builder pattern for encoding
  - [ ] Color mode enum
  - [ ] Default configurations
  - [ ] Image I/O helpers

## References

- **JNI Wrapper:** `javacpp-wrapper/src/main/c/JABCodeNative_jni.cpp`
- **Java API:** `javacpp-wrapper/src/main/java/com/jabcode/OptimizedJABCode.java`
- **Native Pointers:** `javacpp-wrapper/src/main/java/com/jabcode/internal/JABCodeNativePtr.java`
