# Native Libraries in JABCode Java Wrapper

This document explains how the native libraries are handled in the JABCode Java wrapper.

## Library Path Issue

The JABCode Java wrapper depends on a native library called `libjabcode_jni.so` which is located in the `src` directory of the project. However, the Java code expects to find this library in the `target/classes/com/jabcode/linux-x86_64/` directory.

## Solution

To fix this issue, we've created a script called `fix-library-path.sh` that copies the library from the `src` directory to the expected location in the `target` directory. This script is automatically called by the `build-java-wrapper.sh` script during the build process.

## Manual Fix

If you need to manually fix the library path issue, you can run the following command:

```bash
./fix-library-path.sh
```

This will copy the library to the correct location.

## Troubleshooting

If you encounter issues with the native library, check the following:

1. Make sure the `libjabcode_jni.so` file exists in the `src` directory.
2. Make sure the `target/classes/com/jabcode/linux-x86_64/` directory exists.
3. Make sure the `libjabcode_jni.so` file has been copied to the `target/classes/com/jabcode/linux-x86_64/` directory.
4. Make sure the `libjabcode_jni.so` file has the correct permissions.

If you still encounter issues, try running the `fix-library-path.sh` script manually.
