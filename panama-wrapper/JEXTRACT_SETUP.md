# jextract Setup Guide

**Status:** ⚠️ Required for Panama bindings generation  
**Current Blocker:** jextract not installed

## What is jextract?

jextract is a tool that mechanically generates Java bindings from C header files for use with Panama's Foreign Function & Memory API. It's **not** included in standard JDK distributions and must be downloaded separately.

## Installation Options

### Option 1: Download Standalone jextract (Recommended)

1. **Download latest jextract build:**
   - Visit: https://jdk.java.net/jextract/
   - Download the appropriate build for your platform (Linux x64)
   - Current early access builds support JDK 23+

2. **Extract and install:**
   ```bash
   cd ~/tools/compilers/java
   tar xzf ~/Downloads/jextract-*.tar.gz
   # Creates jextract-<version>/ directory
   ```

3. **Add to PATH:**
   ```bash
   export PATH="$HOME/tools/compilers/java/jextract-<version>/bin:$PATH"
   
   # Or add to ~/.bashrc for persistence:
   echo 'export PATH="$HOME/tools/compilers/java/jextract-<version>/bin:$PATH"' >> ~/.bashrc
   source ~/.bashrc
   ```

4. **Verify installation:**
   ```bash
   jextract --version
   ```

### Option 2: Build from Source

If pre-built binaries aren't available:

```bash
git clone https://github.com/openjdk/jextract.git
cd jextract
sh ./gradlew clean verify
# Binary will be in build/jextract/bin/jextract
```

## Verification

After installation, verify jextract is working:

```bash
# Check version
jextract --version

# Check it can find headers
jextract --help
```

Expected output:
```
jextract <version>
Usage: jextract [options] <header file>
...
```

## Generate JABCode Bindings

Once jextract is installed:

```bash
cd /mnt/b34628fa-d41e-4c37-8caf-f06a6ecbb1ae/projects/practice/barcode/jabcode/panama-wrapper

# Set environment for JDK 23+
export JAVA_HOME=/home/kynphlee/tools/compilers/java/jdk-23.0.1
export PATH="$JAVA_HOME/bin:$PATH"

# Run binding generation
./jextract.sh
```

The script will:
- Validate jextract is available
- Check JABCode headers exist
- Generate Panama bindings in `target/generated-sources/jextract/`
- Output summary of generated files

## What Gets Generated

jextract will create Java classes for:

### Functions
- `createEncode()` - Create encoder instance
- `destroyEncode()` - Free encoder
- `generateJABCode()` - Generate barcode
- `decodeJABCode()` - Decode barcode
- `decodeJABCodeEx()` - Extended decode
- `saveImage()`, `saveImageCMYK()` - Image output
- `readImage()` - Image input
- `reportError()` - Error handling

### Structures
- `jab_encode` - Encoder configuration and state
- `jab_data` - Input/output data
- `jab_bitmap` - Image bitmap
- `jab_decoded_symbol` - Decoded result
- `jab_metadata` - Symbol metadata
- `jab_symbol` - Symbol information
- `jab_vector2d` - 2D vector
- `jab_point` - Point coordinates

### Support Classes
- Memory layouts for each struct
- Accessor methods for struct fields
- Function descriptors for native calls
- Memory segment utilities

## Expected Directory Structure

After generation:

```
target/generated-sources/jextract/
└── com/
    └── jabcode/
        └── panama/
            └── bindings/
                ├── jabcode_h.java              # Main binding class
                ├── jab_encode.java             # Encoder struct
                ├── jab_data.java               # Data struct
                ├── jab_bitmap.java             # Bitmap struct
                ├── jab_decoded_symbol.java     # Decode result
                ├── RuntimeHelper.java          # Internal helpers
                └── ... (additional support files)
```

## Integration with Maven

The POM is already configured to:
1. Add `target/generated-sources/jextract/` to source path
2. Compile generated bindings
3. Skip generation by default (`-DskipJextract=true`)

To enable jextract in Maven build:

```bash
mvn clean compile  # Without -DskipJextract flag
```

Or update `pom.xml` to run jextract automatically via exec-maven-plugin.

## Troubleshooting

### "jextract: command not found"

- Check PATH: `echo $PATH`
- Verify jextract exists: `ls ~/tools/compilers/java/jextract-*/bin/jextract`
- Re-export PATH with correct version

### "Cannot find header file"

- Verify header exists: `ls -l ../src/jabcode/include/jabcode.h`
- Check jextract script paths are correct
- Ensure you're running from `panama-wrapper/` directory

### "Unsupported class file version"

- Ensure JAVA_HOME points to JDK 23+
- jextract requires JDK 23+ to run
- Check: `java -version`

### "Symbol not found: _jabcode_*"

- Native library not in library path
- Set: `export LD_LIBRARY_PATH=../lib:$LD_LIBRARY_PATH`
- Or build JABCode native library: `cd ../src/jabcode && make`

## Next Steps After Installation

1. **Generate bindings:**
   ```bash
   ./jextract.sh
   ```

2. **Build project:**
   ```bash
   JAVA_HOME=/home/kynphlee/tools/compilers/java/jdk-23.0.1 mvn clean package
   ```

3. **Implement encoder integration:**
   - Use generated bindings in `JABCodeEncoder.java`
   - Wire color palettes into encoding pipeline
   - Apply data masking
   - Embed palette metadata

4. **Implement decoder integration:**
   - Use bindings in `JABCodeDecoder.java`
   - Extract embedded palette
   - Decode Nc metadata
   - Reconstruct high-color palettes

5. **Run integration tests:**
   ```bash
   JAVA_HOME=/home/kynphlee/tools/compilers/java/jdk-23.0.1 mvn test
   ```

## Resources

- **jextract Downloads:** https://jdk.java.net/jextract/
- **jextract GitHub:** https://github.com/openjdk/jextract
- **Panama Tutorial:** https://foojay.io/today/project-panama-for-newbies-part-1/
- **JEP 454 (FFM API):** https://openjdk.org/jeps/454

## Current Status

- ✅ JDK 23 installed: `/home/kynphlee/tools/compilers/java/jdk-23.0.1`
- ✅ JDK 25 installed: `/home/kynphlee/tools/compilers/java/jdk-25.0.1`
- ✅ JABCode headers: `../src/jabcode/include/jabcode.h`
- ✅ Color modes implementation complete (Phases 1-6)
- ⚠️ jextract: **Not installed** - blocking Phase 7
- ⏸️ Panama bindings: Pending jextract installation
- ⏸️ Full encoder/decoder: Pending bindings generation

---

**To proceed:** Install jextract from https://jdk.java.net/jextract/ and add to PATH.
