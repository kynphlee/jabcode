# JABCode Native Library Fixes

This document describes the fixes applied to the JABCode native library integration.

## Issue: Undefined Symbol `_Z12createEncodeii`

When running tests in IntelliJ, the following error was encountered:

```
java: symbol lookup error: /path/to/libjniJABCodeNative.so: undefined symbol: _Z12createEncodeii
```

This error occurs because the C++ name mangling is causing the symbol `_Z12createEncodeii` (which is the mangled name for `createEncode(int, int)`) to be undefined when running in IntelliJ.

## Root Cause

The issue was caused by two main problems:

1. The JavaCPP-generated library (`libjniJABCodeNative.so`) was not properly linked with the C wrapper functions.
2. The JNI implementation in `SimpleJABCode_jni.c` was not properly linked with the C wrapper functions.
3. The JavaCPP presets were not configured to use the C wrapper functions instead of the C++ functions directly.

## Fix

The following fixes were applied:

1. Updated the `build-natives.sh` script to include the C wrapper object file in the link command:

   ```bash
   LINK_CMD="g++ -shared -o $TARGET_LIB jabcode_constants.o jabcode_wrapper.o jabcode_c_wrapper.o -L$WRAPPER_DIR/../src/jabcode/build -ljabcode -lpng -lz -lm"
   ```

2. Created a `fix-javacpp-lib.sh` script to fix the JavaCPP-generated library by linking it with the C wrapper functions:

   ```bash
   # Compile the C wrapper functions
   g++ -c -I$WRAPPER_DIR/../src/jabcode/include -fPIC $WRAPPER_DIR/jabcode_c_wrapper.cpp -o jabcode_c_wrapper.o

   # Create a new library that includes the C wrapper functions
   TEMP_LIB="${LIB_PREFIX}jniJABCodeNative_fixed${LIB_SUFFIX}"
   g++ -shared -o $TEMP_LIB $JAVACPP_LIB jabcode_c_wrapper.o -L$WRAPPER_DIR/../src/jabcode/build -ljabcode -lpng -lz -lm

   # Replace the original library with the fixed one
   mv $TEMP_LIB $JAVACPP_LIB
   ```

3. Updated the `build-natives.sh` script to run the `fix-javacpp-lib.sh` script as part of the build process.

4. Updated the JavaCPP presets in both `com.jabcode.JABCodePresets` and `com.jabcode.wrapper.JABCodePresets` to use the C wrapper functions instead of the C++ functions directly:

   ```java
   // Map functions to use C wrapper functions instead of C++ functions directly
   infoMap.put(new Info("createEncode").javaNames("createEncode").cppNames("createEncode_c"));
   infoMap.put(new Info("destroyEncode").javaNames("destroyEncode").cppNames("destroyEncode_c"));
   infoMap.put(new Info("generateJABCode").javaNames("generateJABCode").cppNames("generateJABCode_c"));
   infoMap.put(new Info("decodeJABCode").javaNames("decodeJABCode").cppNames("decodeJABCode_c"));
   infoMap.put(new Info("saveImage").javaNames("saveImage").cppNames("saveImage_c"));
   infoMap.put(new Info("readImage").javaNames("readImage").cppNames("readImage_c"));
   ```

5. Added the `jabcode_c_wrapper.h` header to the include list in the JavaCPP presets:

   ```java
   @Properties(
       target = "com.jabcode.internal.JABCodeNative",
       value = {
           @Platform(
               include = {"jabcode.h", "jabcode_c_wrapper.h"},
               link = {"jabcode", "png", "z"}
           )
       }
   )
   ```

## Testing

The fix was tested by:

1. Running the Maven tests: `mvn test`
2. Running a simple test program: `TestSimpleJABCode.java`

Both tests passed successfully, confirming that the issue has been resolved.

## Workaround

While we've fixed the underlying issue, there are still some challenges with using the JABCodeNative class directly due to JavaCPP dependencies. If you encounter issues, you can use the `SimpleJABCode` class instead of the `JABCodeNative` class. The `SimpleJABCode` class provides a simpler interface to the JABCode library and is not affected by the C++ name mangling issue.

Example:

```java
import com.jabcode.wrapper.SimpleJABCode;

public class TestSimpleJABCode {
    public static void main(String[] args) {
        try {
            System.out.println("Generating JABCode...");
            String outputFile = "test_generated_jabcode.png";
            SimpleJABCode.generateJABCodeWithColorMode("Test JABCode", outputFile, 8);
            System.out.println("JABCode generated successfully!");
            System.out.println("Output file: " + outputFile);
        } catch (Throwable t) {
            System.err.println("Error: " + t.getMessage());
            t.printStackTrace();
        }
    }
}
```

## Comprehensive Solution

We've implemented a comprehensive solution to fix the issues with the JABCodeNative class:

1. **Enhanced NativeLibraryLoader**: Modified the NativeLibraryLoader class to apply the fix at runtime when loading the library from the classpath.

2. **Java-based Fix Implementation**: Added a Java-based implementation of the fix-javacpp-lib.sh script to ensure the fix is applied regardless of how the library is loaded.

3. **Updated Build Process**: Modified the build process to include the C wrapper files in the JAR file.

4. **JABCodeWrapper Class**: Created a comprehensive wrapper class (JABCodeWrapper) that doesn't directly depend on JavaCPP's InfoMapper. This wrapper uses SimpleJABCode internally but provides similar functionality to JABCodeNative.

5. **Maven Configuration Updates**: Updated the Maven shade plugin configuration to include all necessary JavaCPP dependencies in the JAR file.

6. **Test Helper Class**: Created a JABCodeTestHelper class that helps set up the correct classpath for standalone tests and provides diagnostic information.

These changes ensure that the JABCode library can be used from Java applications without encountering the undefined symbol error, regardless of how the library is loaded.

## Usage Examples

### Using JABCodeWrapper

```java
import com.jabcode.wrapper.JABCodeWrapper;

public class TestJABCodeWrapper {
    public static void main(String[] args) {
        try {
            // Create an encoder
            JABCodeWrapper.Encoder encoder = JABCodeWrapper.createEncoder(
                JABCodeWrapper.COLOR_MODE_8, 1);
            
            // Generate a JABCode
            String text = "This is a test JABCode";
            String outputFile = "test_jabcode.png";
            int result = encoder.generate(text, outputFile);
            
            if (result == JABCodeWrapper.JAB_SUCCESS) {
                System.out.println("Successfully generated JABCode!");
                System.out.println("Output file: " + encoder.getGeneratedFilePath());
            }
            
            // Decode a JABCode
            String decodedText = JABCodeWrapper.decodeJABCode(outputFile);
            System.out.println("Decoded text: " + decodedText);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
```

### Using JABCodeTestHelper

```java
import com.jabcode.util.JABCodeTestHelper;

public class TestWithHelper {
    public static void main(String[] args) {
        try {
            // Set up the classpath
            JABCodeTestHelper.setupClasspath();
            
            // Print diagnostic information
            JABCodeTestHelper.printDiagnosticInfo();
            
            // Check if the native library is available
            if (JABCodeTestHelper.isNativeLibraryAvailable()) {
                System.out.println("Native library is available!");
                // Use JABCodeWrapper or JABCodeNative here
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
```

## Future Improvements

While the current solution addresses the immediate issues, there are still some potential improvements:

1. **Further Simplify Dependencies**: Consider reducing the dependency on JavaCPP even further by implementing a pure JNI solution.

2. **Improve Error Handling**: Enhance error reporting and handling in the wrapper classes.

3. **Add More Features**: Extend the JABCodeWrapper class to support more features of the JABCode library.

4. **Optimize Performance**: Profile and optimize the performance of the wrapper classes.
