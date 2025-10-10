#!/usr/bin/env bash
set -euo pipefail

# Args:
# 1 = project.basedir (wrapper dir)
# 2 = project.build.directory (target dir)
# 3 = javacpp.platform (e.g., linux-x86_64)

BASEDIR=${1:?missing basedir}

# Build dedicated pointer-based JNI library and stage it
build_ptr_jni() {
  echo "[create-symlinks] Building libjniJABCodeNativePtr.so"
  local SRC_DIR="$BASEDIR/src/main/c"
  local OUT_DIR_CLS="$TARGET/classes/com/jabcode/$PLATFORM"
  local OUT_DIR_TEST="$TARGET/test-classes/com/jabcode/$PLATFORM"
  mkdir -p "$OUT_DIR_CLS" "$OUT_DIR_TEST"
  # Resolve JDK includes
  local JDK_HOME="${JAVA_HOME:-}"
  if [[ -z "$JDK_HOME" ]]; then
    if command -v javac >/dev/null 2>&1; then
      JDK_HOME=$(readlink -f "$(dirname "$(which javac)")/.." 2>/dev/null || true)
    fi
  fi
  local INC_JNI1=""
  local INC_JNI2=""
  if [[ -n "$JDK_HOME" && -d "$JDK_HOME/include" ]]; then
    INC_JNI1="-I$JDK_HOME/include"
    # best-effort linux include
    if [[ -d "$JDK_HOME/include/linux" ]]; then INC_JNI2="-I$JDK_HOME/include/linux"; fi
  fi
  g++ -I"$SRC_DIR" -I"$BASEDIR/../src/jabcode/include" $INC_JNI1 $INC_JNI2 \
      -fPIC -shared -std=c++11 -O3 \
      -o "$OUT_DIR_CLS/libjniJABCodeNativePtr.so" \
      "$SRC_DIR/JABCodeNative_jni.cpp" \
      "$SRC_DIR/jabcode_c_wrapper.c" \
      -Wl,-rpath,'$ORIGIN/' -Wl,-z,noexecstack \
      -Wl,--no-as-needed -L"$BASEDIR/src/jabcode/build" -L"$BASEDIR/../src/jabcode/build" -L"$BASEDIR/lib" -ljabcode -Wl,--as-needed -lpng16 -lz
  cp -f "$OUT_DIR_CLS/libjniJABCodeNativePtr.so" "$OUT_DIR_TEST/"
  # Also stage into lib/ and libs/
  cp -f "$OUT_DIR_CLS/libjniJABCodeNativePtr.so" "$PLAT_DIR/" || true
  cp -f "$OUT_DIR_CLS/libjniJABCodeNativePtr.so" "$LIBS_DIR/" || true
  cp -f "$OUT_DIR_CLS/libjniJABCodeNativePtr.so" "$LIB_DIR/" || true
}

# Final fallback: resolve from system ldconfig cache
copy_dep_from_system() {
  local depname="$1"
  local pattern="$2"
  local src=""
  if command -v ldconfig >/dev/null 2>&1; then
    src=$(ldconfig -p 2>/dev/null | awk -v ptn="$pattern" '$1 ~ ptn { print $NF }' | head -n1)
  fi
  if [[ -z "$src" ]]; then
    # common Debian/Ubuntu path
    src=$(ls -1 /usr/lib/x86_64-linux-gnu/$pattern 2>/dev/null | head -n1 || true)
  fi
  if [[ -n "$src" && -f "$src" ]]; then
    echo "[create-symlinks] Copying dependency $depname from system $src"
    cp -f "$src" "$PLAT_DIR/" || true
    cp -f "$src" "$LIBS_DIR/" || true
    cp -f "$src" "$LIB_DIR/" || true
    if [[ -n "${JNI_DIR:-}" && -d "$JNI_DIR" ]]; then cp -f "$src" "$JNI_DIR/" || true; fi
    mkdir -p "$TARGET/classes/com/jabcode/$PLATFORM" "$TARGET/test-classes/com/jabcode/$PLATFORM"
    cp -f "$src" "$TARGET/classes/com/jabcode/$PLATFORM/" || true
    cp -f "$src" "$TARGET/test-classes/com/jabcode/$PLATFORM/" || true
  else
    echo "[create-symlinks] WARN: dependency $depname not found in system cache"
  fi
}
TARGET=${2:?missing build directory}
PLATFORM=${3:?missing platform}

# Dirs
LIB_DIR="$BASEDIR/lib"
LIBS_DIR="$BASEDIR/libs"
PLAT_DIR="$BASEDIR/libs/$PLATFORM"

mkdir -p "$PLAT_DIR" "$LIBS_DIR" "$LIB_DIR"

echo "[create-symlinks] BASEDIR=$BASEDIR TARGET=$TARGET PLATFORM=$PLATFORM"

# Ensure core is built (upstream and wrapper). Force relink of wrapper to pick up linker flag changes.
make -C "$BASEDIR/../src/jabcode" all
make -C "$BASEDIR/src/jabcode" clean all || true

# Locate JNI .so
JNI_SRC=""
JNI_DIR=""
JNI_EXPECT1="$TARGET/classes/com/jabcode/$PLATFORM/libjniJABCodeNative.so"
JNI_EXPECT2="$TARGET/test-classes/com/jabcode/$PLATFORM/libjniJABCodeNative.so"

echo "[create-symlinks] Checking expected JNI locations:"
ls -l "$(dirname "$JNI_EXPECT2")" || true

if [[ -f "$JNI_EXPECT2" ]]; then
  JNI_SRC="$JNI_EXPECT2"
elif [[ -f "$JNI_EXPECT1" ]]; then
  JNI_SRC="$JNI_EXPECT1"
fi

if [[ -z "$JNI_SRC" ]]; then
  JNI_SRC=$(find "$TARGET/test-classes" -type f -name libjniJABCodeNative.so -print -quit 2>/dev/null || true)
fi
if [[ -z "$JNI_SRC" ]]; then
  JNI_SRC=$(find "$TARGET/classes" -type f -name libjniJABCodeNative.so -print -quit 2>/dev/null || true)
fi

if [[ -n "$JNI_SRC" && -f "$JNI_SRC" ]]; then
  echo "[create-symlinks] JNI found at $JNI_SRC"
  cp -f "$JNI_SRC" "$PLAT_DIR/libjabcode_jni.so"
  # Provide common alternative names expected by different loaders
  cp -f "$JNI_SRC" "$PLAT_DIR/libjniJABCodeNative.so"
  cp -f "$JNI_SRC" "$PLAT_DIR/libjniJABCodeNativePtr.so"
  cp -f "$JNI_SRC" "$LIBS_DIR/libjabcode_jni.so"
  cp -f "$JNI_SRC" "$LIBS_DIR/libjniJABCodeNative.so"
  cp -f "$JNI_SRC" "$LIBS_DIR/libjniJABCodeNativePtr.so"
  cp -f "$JNI_SRC" "$LIB_DIR/libjabcode_jni.so"
  cp -f "$JNI_SRC" "$LIB_DIR/libjniJABCodeNative.so"
  cp -f "$JNI_SRC" "$LIB_DIR/libjniJABCodeNativePtr.so"
  # Also install alt-named copies into target classpaths for JavaCPP loader fallbacks
  mkdir -p "$TARGET/classes/com/jabcode/$PLATFORM" "$TARGET/test-classes/com/jabcode/$PLATFORM"
  cp -f "$JNI_SRC" "$TARGET/classes/com/jabcode/$PLATFORM/libjniJABCodeNativePtr.so"
  cp -f "$JNI_SRC" "$TARGET/test-classes/com/jabcode/$PLATFORM/libjniJABCodeNativePtr.so"
  JNI_DIR="$(dirname "$JNI_SRC")"
else
  echo "[create-symlinks] WARN: JNI not found under target/test-classes or target/classes"
fi

# Locate core libjabcode.so; prefer wrapper build
CORE_SRC=""
WRAPPER_CORE="$BASEDIR/src/jabcode/build/libjabcode.so"
UPSTREAM_CORE="$BASEDIR/../src/jabcode/build/libjabcode.so"

echo "[create-symlinks] Listing wrapper core dir: $BASEDIR/src/jabcode/build"
ls -l "$BASEDIR/src/jabcode/build" || true

# Helper to copy runtime dependencies resolved by ldd
copy_dep() {
  local depname="$1"
  local src
  src=$(ldd "$CORE_SRC" | awk -v n="$depname" '$1 ~ n { print $3 }' | head -n1)
  if [[ -n "$src" && -f "$src" ]]; then
    echo "[create-symlinks] Copying dependency $depname from $src"
    cp -f "$src" "$PLAT_DIR/" || true
    cp -f "$src" "$LIBS_DIR/" || true
    cp -f "$src" "$LIB_DIR/" || true
    if [[ -n "${JNI_DIR:-}" && -d "$JNI_DIR" ]]; then cp -f "$src" "$JNI_DIR/" || true; fi
    mkdir -p "$TARGET/classes/com/jabcode/$PLATFORM" "$TARGET/test-classes/com/jabcode/$PLATFORM"
    cp -f "$src" "$TARGET/classes/com/jabcode/$PLATFORM/" || true
    cp -f "$src" "$TARGET/test-classes/com/jabcode/$PLATFORM/" || true
  else
    echo "[create-symlinks] WARN: dependency $depname not resolved via ldd"
  fi
}

# Also try resolving from the JNI library if core doesn't declare NEEDED entries
copy_dep_from_jni() {
  local depname="$1"
  [[ -z "${JNI_SRC:-}" || ! -f "$JNI_SRC" ]] && return 0
  local src
  src=$(ldd "$JNI_SRC" | awk -v n="$depname" '$1 ~ n { print $3 }' | head -n1)
  if [[ -n "$src" && -f "$src" ]]; then
    echo "[create-symlinks] Copying dependency $depname from JNI $src"
    cp -f "$src" "$PLAT_DIR/" || true
    cp -f "$src" "$LIBS_DIR/" || true
    cp -f "$src" "$LIB_DIR/" || true
    if [[ -n "${JNI_DIR:-}" && -d "$JNI_DIR" ]]; then cp -f "$src" "$JNI_DIR/" || true; fi
    mkdir -p "$TARGET/classes/com/jabcode/$PLATFORM" "$TARGET/test-classes/com/jabcode/$PLATFORM"
    cp -f "$src" "$TARGET/classes/com/jabcode/$PLATFORM/" || true
    cp -f "$src" "$TARGET/test-classes/com/jabcode/$PLATFORM/" || true
  else
    echo "[create-symlinks] WARN: dependency $depname not resolved via ldd on JNI"
  fi
}

# Helper to check if a built core declares a dependency
core_has_dep() {
  local core="$1" dep="$2"
  [[ -f "$core" ]] || return 1
  ldd "$core" | grep -q "$dep" && return 0 || return 1
}

# Prefer wrapper core (built here with -fPIC, -lpng16, -lz); fall back to upstream if wrapper missing
if [[ -f "$WRAPPER_CORE" ]]; then
  CORE_SRC="$WRAPPER_CORE"
elif [[ -f "$UPSTREAM_CORE" ]]; then
  CORE_SRC="$UPSTREAM_CORE"
fi
if [[ -z "$CORE_SRC" ]]; then
  CORE_SRC=$(find "$TARGET/test-classes" -type f -name libjabcode.so -print -quit 2>/dev/null || true)
fi
if [[ -z "$CORE_SRC" ]]; then
  CORE_SRC=$(find "$TARGET/classes" -type f -name libjabcode.so -print -quit 2>/dev/null || true)
fi
# Already considered UPSTREAM_CORE above

if [[ -n "$CORE_SRC" && -f "$CORE_SRC" ]]; then
  echo "[create-symlinks] CORE found at $CORE_SRC"
  cp -f "$CORE_SRC" "$PLAT_DIR/libjabcode.so"
  cp -f "$CORE_SRC" "$LIBS_DIR/libjabcode.so"
  cp -f "$CORE_SRC" "$LIB_DIR/libjabcode.so"
  # co-locate core next to JNI for $ORIGIN resolution
  if [[ -n "${JNI_DIR:-}" && -d "$JNI_DIR" ]]; then
    cp -f "$CORE_SRC" "$JNI_DIR/libjabcode.so"
  fi
  # ensure classes and test-classes have core co-located as well
  mkdir -p "$TARGET/classes/com/jabcode/$PLATFORM" "$TARGET/test-classes/com/jabcode/$PLATFORM"
  cp -f "$CORE_SRC" "$TARGET/classes/com/jabcode/$PLATFORM/libjabcode.so"
  cp -f "$CORE_SRC" "$TARGET/test-classes/com/jabcode/$PLATFORM/libjabcode.so"
  # Copy critical runtime deps (libpng16, libz) alongside core to satisfy symbol resolution at runtime
  copy_dep "libpng16.so"
  copy_dep "libz.so"
  # If the core did not list them as NEEDED (common with --as-needed), try resolving from the JNI library
  copy_dep_from_jni "libpng16.so"
  copy_dep_from_jni "libz.so"
  # Final fallback to system locations
  copy_dep_from_system "libpng16" "libpng16.so*"
  copy_dep_from_system "libz" "libz.so*"
else
  echo "[create-symlinks] WARN: core shared lib not found in target/test-classes, target/classes, or src build"
fi

# Build and stage the dedicated Ptr JNI after dependencies and core are in place
build_ptr_jni

echo "[create-symlinks] Done." 
