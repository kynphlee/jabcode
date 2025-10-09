#!/usr/bin/env bash
set -euo pipefail

# Args:
# 1 = project.basedir (wrapper dir)
# 2 = project.build.directory (target dir)
# 3 = javacpp.platform (e.g., linux-x86_64)

BASEDIR=${1:?missing basedir}
TARGET=${2:?missing build directory}
PLATFORM=${3:?missing platform}

# Dirs
LIB_DIR="$BASEDIR/lib"
LIBS_DIR="$BASEDIR/libs"
PLAT_DIR="$BASEDIR/libs/$PLATFORM"

mkdir -p "$PLAT_DIR" "$LIBS_DIR" "$LIB_DIR"

echo "[create-symlinks] BASEDIR=$BASEDIR TARGET=$TARGET PLATFORM=$PLATFORM"

# Ensure core is built (upstream and wrapper)
make -C "$BASEDIR/../src/jabcode" all
make -C "$BASEDIR/src/jabcode" all || true

# Locate JNI .so
JNI_SRC=""
JNI_DIR=""
JNI_EXPECT1="$TARGET/classes/com/jabcode/$PLATFORM/libjniJABCodeNative.so"
JNI_EXPECT2="$TARGET/test-classes/com/jabcode/$PLATFORM/libjniJABCodeNative.so"

echo "[create-symlinks] Checking expected JNI locations:"
ls -l "$(dirname "$JNI_EXPECT1")" || true
ls -l "$(dirname "$JNI_EXPECT2")" || true

if [[ -f "$JNI_EXPECT2" ]]; then
  JNI_SRC="$JNI_EXPECT2"
elif [[ -f "$JNI_EXPECT1" ]]; then
  JNI_SRC="$JNI_EXPECT1"i

if [[ -z "$JNI_SRC" ]]; then
  JNI_SRC=$(find "$TARGET/test-classes" -type f -name libjniJABCodeNative.so -print -quit 2>/dev/null || true)
fi
if [[ -z "$JNI_SRC" ]]; then
  JNI_SRC=$(find "$TARGET/classes" -type f -name libjniJABCodeNative.so -print -quit 2>/dev/null || true)
fi

if [[ -n "$JNI_SRC" && -f "$JNI_SRC" ]]; then
  echo "[create-symlinks] JNI found at $JNI_SRC"
  cp -f "$JNI_SRC" "$PLAT_DIR/libjabcode_jni.so"
  cp -f "$JNI_SRC" "$LIBS_DIR/libjabcode_jni.so"
  cp -f "$JNI_SRC" "$LIB_DIR/libjabcode_jni.so"
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

if [[ -f "$WRAPPER_CORE" ]]; then
  CORE_SRC="$WRAPPER_CORE"
fi
if [[ -z "$CORE_SRC" ]]; then
  CORE_SRC=$(find "$TARGET/test-classes" -type f -name libjabcode.so -print -quit 2>/dev/null || true)
fi
if [[ -z "$CORE_SRC" ]]; then
  CORE_SRC=$(find "$TARGET/classes" -type f -name libjabcode.so -print -quit 2>/dev/null || true)
fi
if [[ -z "$CORE_SRC" && -f "$UPSTREAM_CORE" ]]; then
  CORE_SRC="$UPSTREAM_CORE"
fi

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
else
  echo "[create-symlinks] WARN: core shared lib not found in target/test-classes, target/classes, or src build"
fi

echo "[create-symlinks] Done." 
