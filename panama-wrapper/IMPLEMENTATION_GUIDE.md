# Panama Wrapper Implementation Guide

This guide walks you through completing the Panama FFM implementation for JABCode.

## Step 1: Install jextract

### Option A: Use JDK 25 (Recommended)

JDK 25 may include jextract. Check if it's available:

```bash
export JAVA_HOME=/home/kynphlee/tools/compilers/java/jdk-25.0.1
export PATH="$JAVA_HOME/bin:$PATH"
jextract --version
```

### Option B: Download Standalone jextract

If jextract is not included:

```bash
# Download from: https://jdk.java.net/jextract/
cd /home/kynphlee/tools/compilers/java
wget https://download.java.net/java/early_access/jextract/22/5/openjdk-22-jextract+5-33_linux-x64_bin.tar.gz
tar -xzf openjdk-22-jextract+5-33_linux-x64_bin.tar.gz
export PATH="/home/kynphlee/tools/compilers/java/jextract-22/bin:$PATH"
```

## Step 2: Build JABCode Native Library

Ensure libjabcode is built:

```bash
cd /mnt/b34628fa-d41e-4c37-8caf-f06a6ecbb1ae/projects/practice/barcode/jabcode/src/jabcode
make clean
make

# Verify library exists
ls -lh ../../lib/libjabcode.*
```

## Step 3: Generate Panama Bindings

```bash
cd /mnt/b34628fa-d41e-4c37-8caf-f06a6ecbb1ae/projects/practice/barcode/jabcode/panama-wrapper
./jextract.sh
```

This generates Java bindings in `target/generated-sources/jextract/`.

**Expected output:**
```
Generated files:
  com/jabcode/panama/bindings/jabcode_h.java
  com/jabcode/panama/bindings/jab_data.java
  com/jabcode/panama/bindings/jab_encode.java
  com/jabcode/panama/bindings/jab_bitmap.java
  ...
```

## Step 4: Implement JABCodeEncoder

Once bindings are generated, complete the implementation in `JABCodeEncoder.java`:

```java
package com.jabcode.panama;

import java.lang.foreign.*;
import static com.jabcode.panama.bindings.jabcode_h.*;

public class JABCodeEncoder {
    
    public byte[] encodeWithConfig(String data, Config config) {
        if (data == null || data.isEmpty()) {
            throw new IllegalArgumentException("Data cannot be null or empty");
        }
        
        try (Arena arena = Arena.ofConfined()) {
            // 1. Create encoder structure
            MemorySegment enc = createEncode(
                arena,
                config.getColorNumber(),
                config.getSymbolNumber()
            );
            
            if (enc.address() == 0) {
                return null;
            }
            
            // 2. Prepare data structure
            byte[] dataBytes = data.getBytes(StandardCharsets.UTF_8);
            MemorySegment jabData = jab_data.allocate(arena);
            jab_data.length(jabData, dataBytes.length);
            
            // Allocate flexible array member for data
            MemorySegment dataArray = arena.allocateFrom(
                ValueLayout.JAVA_BYTE,
                dataBytes
            );
            // Note: jab_data has flexible array member, handle accordingly
            
            // 3. Set encode parameters
            jab_encode.module_size(enc, config.getModuleSize());
            jab_encode.master_symbol_width(enc, config.getMasterSymbolWidth());
            jab_encode.master_symbol_height(enc, config.getMasterSymbolHeight());
            
            // Set ECC levels for symbols
            MemorySegment symbols = jab_encode.symbols(enc);
            if (symbols.address() != 0) {
                // Set ECC level for each symbol
                // (Implementation depends on exact structure)
            }
            
            // 4. Generate JABCode
            int result = generateJABCode(arena, enc, jabData);
            
            if (result == 0) { // JAB_FAILURE
                destroyEncode(arena, enc);
                return null;
            }
            
            // 5. Extract bitmap
            MemorySegment bitmap = jab_encode.bitmap(enc);
            if (bitmap.address() == 0) {
                destroyEncode(arena, enc);
                return null;
            }
            
            int width = jab_bitmap.width(bitmap);
            int height = jab_bitmap.height(bitmap);
            int channels = jab_bitmap.channel_count(bitmap);
            int pixelCount = width * height * channels;
            
            // 6. Copy pixel data
            MemorySegment pixelData = jab_bitmap.pixel(bitmap);
            byte[] pixels = pixelData.reinterpret(pixelCount)
                                    .toArray(ValueLayout.JAVA_BYTE);
            
            // 7. Cleanup
            destroyEncode(arena, enc);
            
            return pixels;
            
        } catch (Exception e) {
            throw new RuntimeException("Encoding failed", e);
        }
    }
}
```

## Step 5: Handle Flexible Array Members

JABCode uses C99 flexible array members (`data[]` in structs). Handle these carefully:

```java
// For jab_data struct with flexible array member:
typedef struct {
    jab_int32 length;
    jab_char  data[];  // Flexible array member
} jab_data;

// Panama approach:
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

## Step 6: Implement JABCodeDecoder

Similar pattern for decoding:

```java
public DecodedResult decodeEx(byte[] imageData) {
    try (Arena arena = Arena.ofConfined()) {
        // 1. Create bitmap from image data
        MemorySegment bitmap = createBitmapFromImageData(arena, imageData);
        
        // 2. Allocate status
        MemorySegment status = arena.allocate(ValueLayout.JAVA_INT);
        
        // 3. Decode
        MemorySegment result = decodeJABCode(arena, bitmap, 0, status);
        
        if (result.address() == 0) {
            return new DecodedResult(null, 0, false);
        }
        
        // 4. Extract data
        int length = jab_data.length(result);
        MemorySegment dataPtr = result.asSlice(
            jab_data.sizeof(),  // Skip struct header
            length
        );
        byte[] decoded = dataPtr.toArray(ValueLayout.JAVA_BYTE);
        
        return new DecodedResult(
            new String(decoded, StandardCharsets.UTF_8),
            1,
            true
        );
    }
}
```

## Step 7: Build and Test

```bash
# Build
mvn clean package

# Run tests (some will be skipped until implementation complete)
mvn test

# Run with native access enabled
mvn test -Djava.library.path=../lib
```

## Step 8: Create Example Application

```java
package com.jabcode.panama.examples;

public class EncoderExample {
    public static void main(String[] args) {
        var encoder = new JABCodeEncoder();
        
        var config = JABCodeEncoder.Config.builder()
            .colorNumber(8)
            .eccLevel(5)
            .moduleSize(12)
            .build();
        
        byte[] encoded = encoder.encodeWithConfig("Hello Panama!", config);
        
        System.out.println("Encoded " + encoded.length + " bytes");
    }
}
```

Run with:
```bash
java --enable-native-access=ALL-UNNAMED \
     -Djava.library.path=../lib \
     -cp target/jabcode-panama-1.0.0-SNAPSHOT.jar \
     com.jabcode.panama.examples.EncoderExample
```

## Common Issues

### Issue: jextract not found

**Solution:** Install jextract or use JDK 25+

### Issue: UnsatisfiedLinkError

**Solution:** Set library path:
```bash
export LD_LIBRARY_PATH=/path/to/jabcode/lib:$LD_LIBRARY_PATH
```

### Issue: IllegalCallerException

**Solution:** Enable native access:
```bash
--enable-native-access=ALL-UNNAMED
```

### Issue: Struct alignment errors

**Solution:** Check generated bindings use correct layouts. May need to adjust jextract flags.

## Debugging Tips

1. **Print generated binding structure:**
   ```bash
   find target/generated-sources/jextract -name "*.java" -exec grep -l "jab_data" {} \;
   cat target/generated-sources/jextract/.../jab_data.java
   ```

2. **Verify memory layouts:**
   ```java
   System.out.println("jab_data size: " + jab_data.sizeof());
   System.out.println("jab_bitmap size: " + jab_bitmap.sizeof());
   ```

3. **Enable JNI logging:**
   ```bash
   java -Xlog:foreign=debug ...
   ```

## Next Steps

1. Complete encoder implementation
2. Complete decoder implementation
3. Add comprehensive tests
4. Create example applications
5. Add benchmarks comparing to JNI version
6. Document performance characteristics

## Resources

- Generated bindings: `target/generated-sources/jextract/`
- JABCode C headers: `../src/jabcode/include/jabcode.h`
- JNI wrapper (for comparison): `../javacpp-wrapper/src/main/c/`
