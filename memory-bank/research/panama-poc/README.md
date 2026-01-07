# JABCode Project Panama Proof of Concept

**Status:** 🧪 Experimental - Desktop/Server Only

This directory demonstrates using **Project Panama (Foreign Function & Memory API)** as a pure-Java alternative to JNI for integrating the JABCode C library.

## What is Project Panama?

Project Panama (JEP 454) provides a modern Foreign Function & Memory API (FFM) that replaces JNI with:
- **Pure Java** - No C/C++ wrapper code needed
- **Automatic binding generation** - `jextract` reads C headers
- **Type safety** - Compile-time checking
- **Memory safety** - Arena-based automatic cleanup
- **Better performance** - Fewer copies than JNI

## Architecture

```
Java Application
  ↓ [FFM API - MethodHandles]
C Library (JABCode) - libjabcode.so/dylib/dll
```

**No C++ wrapper required!**

## Requirements

- **JDK 22+** (FFM API finalized)
  - JDK 25 will be next LTS with FFM
  - Earlier versions (19-21) had preview FFM APIs
- `jextract` tool (included with some JDKs or download separately)
- JABCode native library compiled for your platform

## ⚠️ Critical Limitation

**Does NOT work on Android** - Android is stuck on Java 8 APIs and will likely never support FFM. For Android, use:
- Current JNI implementation (recommended)
- Swift-Java interop (if using Swift)

## Setup

### 1. Install JDK 22+

**macOS (Homebrew):**
```bash
brew install openjdk@25
export JAVA_HOME="/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home"
```

**Linux (SDKMAN):**
```bash
sdk install java 25.0.1-amzn
sdk use java 25.0.1-amzn
```

**Verify:**
```bash
java --version  # Should show 22 or higher
```

### 2. Install jextract

**Option A: Download from OpenJDK**
```bash
# Check https://jdk.java.net/jextract/ for latest
wget https://download.java.net/java/early_access/jextract/22/5/openjdk-22-jextract+5-33_linux-x64_bin.tar.gz
tar -xzf openjdk-22-jextract+5-33_linux-x64_bin.tar.gz
export PATH="$PWD/jextract-22/bin:$PATH"
```

**Option B: Build from source**
```bash
git clone https://github.com/openjdk/jextract.git
cd jextract
sh ./gradlew clean verify
```

### 3. Ensure JABCode Library is Built

```bash
cd ../src/jabcode
make
# Creates libjabcode.so (Linux), libjabcode.dylib (macOS), or jabcode.dll (Windows)
```

## Generate Java Bindings

### Basic Generation

```bash
jextract \
  --output src/main/java \
  --target-package com.jabcode.panama \
  --library jabcode \
  ../../src/jabcode/jabcode.h
```

This generates:
- `com/jabcode/panama/jabcode_h.java` - Main entry point
- Struct classes: `jab_data`, `jab_encode_params`, etc.
- Function handles: `generateJABCode()`, etc.

### Selective Generation (Recommended)

Generate only what you need:

```bash
jextract \
  --output src/main/java \
  --target-package com.jabcode.panama \
  --library jabcode \
  --include-function generateJABCode \
  --include-function readJABCode \
  --include-function createBitmap \
  --include-function destroyBitmap \
  --include-struct jab_data \
  --include-struct jab_encode_params \
  --include-struct jab_decode_params \
  --include-struct jab_bitmap \
  ../../src/jabcode/jabcode.h
```

### Advanced: Include Dependencies

If JABCode headers include other files:

```bash
jextract \
  --output src/main/java \
  --target-package com.jabcode.panama \
  --library jabcode \
  -I ../../src/jabcode \
  -I /usr/include \
  --include-function generateJABCode \
  --include-struct jab_data \
  ../../src/jabcode/jabcode.h
```

## Usage Example

### Simple Encoding

```java
package com.jabcode.panama;

import java.lang.foreign.*;
import static com.jabcode.panama.jabcode_h.*;

public class JABCodeEncoder {
    
    public byte[] encode(String message) {
        // Arena manages all native memory - auto-freed when closed
        try (Arena arena = Arena.ofConfined()) {
            
            // Allocate and initialize encode parameters
            MemorySegment params = jab_encode_params.allocate(arena);
            jab_encode_params.color_number(params, 8);      // 8-color mode
            jab_encode_params.ecc_level(params, 5);         // ECC level 5
            jab_encode_params.symbol_number(params, 1);     // Single symbol
            
            // Convert Java String to C string
            MemorySegment messagePtr = arena.allocateFrom(message);
            
            // Call C function directly
            MemorySegment resultPtr = generateJABCode(arena, params, messagePtr);
            
            if (resultPtr.address() == 0) {
                throw new RuntimeException("JABCode encoding failed");
            }
            
            // Read result structure
            int length = jab_data.length(resultPtr);
            MemorySegment dataPtr = jab_data.data(resultPtr);
            
            // Copy to Java byte array
            byte[] result = dataPtr.reinterpret(length)
                                   .toArray(ValueLayout.JAVA_BYTE);
            
            // Free C-allocated memory (if needed - check JABCode API)
            // free(resultPtr);  // May need to bind free() function
            
            return result;
            
        } // Arena automatically frees all allocated memory here
    }
}
```

### With Error Handling

```java
package com.jabcode.panama;

import java.lang.foreign.*;
import java.util.Optional;
import static com.jabcode.panama.jabcode_h.*;

public class JABCodeService {
    
    private static final int DEFAULT_COLOR_MODE = 8;
    private static final int DEFAULT_ECC_LEVEL = 5;
    
    public record EncodeParams(
        int colorMode,
        int eccLevel,
        int symbolNumber,
        int moduleSize
    ) {
        public static EncodeParams defaults() {
            return new EncodeParams(8, 5, 1, 12);
        }
    }
    
    public Optional<byte[]> encode(String message, EncodeParams params) {
        if (message == null || message.isEmpty()) {
            return Optional.empty();
        }
        
        try (Arena arena = Arena.ofConfined()) {
            // Setup parameters
            MemorySegment paramsStruct = jab_encode_params.allocate(arena);
            jab_encode_params.color_number(paramsStruct, params.colorMode());
            jab_encode_params.ecc_level(paramsStruct, params.eccLevel());
            jab_encode_params.symbol_number(paramsStruct, params.symbolNumber());
            jab_encode_params.module_size(paramsStruct, params.moduleSize());
            
            // Encode
            MemorySegment messagePtr = arena.allocateFrom(message);
            MemorySegment result = generateJABCode(arena, paramsStruct, messagePtr);
            
            if (result.address() == 0) {
                return Optional.empty();
            }
            
            // Extract data
            int length = jab_data.length(result);
            MemorySegment data = jab_data.data(result);
            
            return Optional.of(
                data.reinterpret(length).toArray(ValueLayout.JAVA_BYTE)
            );
            
        } catch (Exception e) {
            System.err.println("JABCode encoding error: " + e.getMessage());
            return Optional.empty();
        }
    }
    
    public Optional<String> decode(byte[] imageData) {
        try (Arena arena = Arena.ofConfined()) {
            // Setup decode parameters
            MemorySegment params = jab_decode_params.allocate(arena);
            // Set decode params as needed
            
            // Allocate bitmap structure
            MemorySegment bitmap = jab_bitmap.allocate(arena);
            // Populate bitmap from imageData
            
            // Call decode
            MemorySegment result = readJABCode(arena, bitmap, params);
            
            if (result.address() == 0) {
                return Optional.empty();
            }
            
            // Extract decoded string
            int length = jab_data.length(result);
            MemorySegment data = jab_data.data(result);
            
            byte[] decoded = data.reinterpret(length)
                                .toArray(ValueLayout.JAVA_BYTE);
            
            return Optional.of(new String(decoded));
            
        } catch (Exception e) {
            System.err.println("JABCode decoding error: " + e.getMessage());
            return Optional.empty();
        }
    }
}
```

## Comparison with Current JNI

### Current JNI Implementation

**File:** `/javacpp-wrapper/src/main/c/JABCodeNative_jni.cpp` (~500 lines)

```cpp
JNIEXPORT jbyteArray JNICALL 
Java_com_jabcode_JABCodeNative_encode(JNIEnv *env, jobject obj, jstring data) {
    // Manual string conversion
    const char *nativeData = (*env)->GetStringUTFChars(env, data, 0);
    
    // Setup params
    jab_encode_params params;
    params.color_number = 8;
    params.ecc_level = 5;
    
    // Call C
    jab_data* encoded = generateJABCode(&params, (jab_byte*)nativeData);
    
    // Manual array creation
    jbyteArray result = (*env)->NewByteArray(env, encoded->length);
    (*env)->SetByteArrayRegion(env, result, 0, encoded->length, encoded->data);
    
    // Manual cleanup
    (*env)->ReleaseStringUTFChars(env, data, nativeData);
    free(encoded);
    
    return result;
}
```

### Panama Implementation

**File:** `src/main/java/com/jabcode/panama/JABCodeService.java` (~100 lines)

```java
public byte[] encode(String message) {
    try (Arena arena = Arena.ofConfined()) {
        MemorySegment params = jab_encode_params.allocate(arena);
        jab_encode_params.color_number(params, 8);
        jab_encode_params.ecc_level(params, 5);
        
        MemorySegment messagePtr = arena.allocateFrom(message);
        MemorySegment result = generateJABCode(arena, params, messagePtr);
        
        int length = jab_data.length(result);
        return jab_data.data(result)
                       .reinterpret(length)
                       .toArray(ValueLayout.JAVA_BYTE);
    }
}
```

**Differences:**
- ❌ No C++ wrapper code
- ✅ Type-safe at compile time
- ✅ Automatic memory management (arena)
- ✅ Pure Java
- ✅ Less code to maintain

## Build Configuration

### Maven

```xml
<project>
    <properties>
        <maven.compiler.source>22</maven.compiler.source>
        <maven.compiler.target>22</maven.compiler.target>
    </properties>
    
    <dependencies>
        <!-- No external dependencies needed - FFM is in JDK -->
    </dependencies>
    
    <build>
        <plugins>
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>exec-maven-plugin</artifactId>
                <version>3.1.0</version>
                <executions>
                    <execution>
                        <id>generate-bindings</id>
                        <phase>generate-sources</phase>
                        <goals>
                            <goal>exec</goal>
                        </goals>
                        <configuration>
                            <executable>jextract</executable>
                            <arguments>
                                <argument>--output</argument>
                                <argument>${project.build.directory}/generated-sources</argument>
                                <argument>--target-package</argument>
                                <argument>com.jabcode.panama</argument>
                                <argument>--library</argument>
                                <argument>jabcode</argument>
                                <argument>${project.basedir}/../../src/jabcode/jabcode.h</argument>
                            </arguments>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
```

### Gradle

```groovy
plugins {
    id 'java'
}

java {
    sourceCompatibility = JavaVersion.VERSION_22
    targetCompatibility = JavaVersion.VERSION_22
}

// Task to generate Panama bindings
task generateBindings(type: Exec) {
    commandLine 'jextract',
        '--output', "${buildDir}/generated/sources/panama",
        '--target-package', 'com.jabcode.panama',
        '--library', 'jabcode',
        '../../src/jabcode/jabcode.h'
}

compileJava.dependsOn generateBindings
sourceSets.main.java.srcDirs "${buildDir}/generated/sources/panama"
```

## Running

### Set Library Path

```bash
# Linux
export LD_LIBRARY_PATH="$PWD/../../lib:$LD_LIBRARY_PATH"

# macOS
export DYLD_LIBRARY_PATH="$PWD/../../lib:$DYLD_LIBRARY_PATH"

# Windows
set PATH=%CD%\..\..\lib;%PATH%
```

### Enable Native Access

FFM requires explicit permission to call native code:

```bash
java --enable-native-access=ALL-UNNAMED \
     -cp target/classes \
     com.jabcode.panama.Main
```

Or in your `module-info.java`:
```java
module com.jabcode.panama {
    requires java.base;
    // Enable native access for this module
}
```

## Advantages Over JNI

| Feature | JNI | Panama |
|---------|-----|--------|
| Wrapper Code | 500+ lines C++ | 0 lines |
| Type Safety | Runtime only | Compile-time |
| Memory Management | Manual | Automatic (arenas) |
| Performance | Excellent | Excellent (often better) |
| Maintainability | High effort | Low effort |
| Code Generation | Manual | Automatic (jextract) |

## Limitations

### Does NOT Work On:
- ❌ Android (Java 8 APIs only)
- ❌ Java 8-21 without preview flags
- ❌ Environments requiring Java 17 LTS

### Works On:
- ✅ Desktop applications (Linux, macOS, Windows)
- ✅ Server applications
- ✅ JDK 22+ environments
- ✅ JDK 25 LTS (future)

## Recommendation

### Use Panama If:
- You're building **desktop/server applications**
- You can require **JDK 22+**
- You want to **eliminate C wrapper code**
- You prefer **pure Java solutions**

### Keep JNI If:
- You need **Android support** ← Most important!
- You need **Java 17 LTS compatibility**
- Your current JNI wrapper works well
- You can't upgrade to JDK 22+

## Next Steps

1. **Experiment:** Try generating bindings and running samples
2. **Benchmark:** Compare performance with JNI version
3. **Decide:** Evaluate if JDK 22+ requirement is acceptable
4. **Consider:** Keep both versions (JNI for Android, Panama for desktop)

## Resources

- **JEP 454:** https://openjdk.org/jeps/454
- **Panama Tutorial:** https://foojay.io/today/project-panama-for-newbies-part-1/
- **jextract Guide:** https://github.com/openjdk/jextract
- **FFM API Docs:** https://docs.oracle.com/en/java/javase/22/docs/api/java.base/java/lang/foreign/package-summary.html
