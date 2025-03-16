# JABCode Library

JABCode (Just Another Barcode) is a color 2D barcode system.

## Directory Structure

- bin/ - Command-line tools
  - jabcodeReader - Tool to read JABCode images
  - jabcodeWriter - Tool to create JABCode images
- lib/ - Libraries
  - libjabcode.a - Core JABCode library
  - libjabcode_jni.so - JNI interface for Java (if available)
- src/ - Source code
  - jabcode/ - Core library source code
  - jabcodeReader/ - Reader tool source code
  - jabcodeWriter/ - Writer tool source code
- javacpp-wrapper/ - Java wrapper for the JABCode library
- test/ - Test files

## Usage

### Creating a JABCode

```
./bin/jabcodeWriter --input "Hello JABCode!" --output output.png
```

### Reading a JABCode

```
./bin/jabcodeReader input.png
```
