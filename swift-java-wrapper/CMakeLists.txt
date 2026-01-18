cmake_minimum_required(VERSION 3.10)
project(jabcode-mobile C)

# Set C11 standard
set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)

# Mobile-specific compilation flags
add_compile_definitions(MOBILE_BUILD)
add_compile_options(
    -O3                      # Maximum optimization
    -ffast-math              # Faster floating point
    -Wall                    # All warnings
    -Wextra                  # Extra warnings
)

# Debug build options
if(CMAKE_BUILD_TYPE MATCHES Debug)
    add_compile_options(-O0 -g)
    # Optionally enable AddressSanitizer for memory debugging
    # add_compile_options(-fsanitize=address)
    # add_link_options(-fsanitize=address)
endif()

# Include directories
include_directories(
    ${CMAKE_CURRENT_SOURCE_DIR}/include
    ${CMAKE_CURRENT_SOURCE_DIR}/../src/jabcode/include
    ${CMAKE_CURRENT_SOURCE_DIR}/../src/jabcode
)

# JABCode native source files (EXCLUDING image.c)
set(JABCODE_SOURCES
    ../src/jabcode/encoder.c
    ../src/jabcode/decoder.c
    ../src/jabcode/ldpc.c
    ../src/jabcode/detector.c
    ../src/jabcode/binarizer.c
    ../src/jabcode/mask.c
    ../src/jabcode/sample.c
    ../src/jabcode/transform.c
    ../src/jabcode/interleave.c
    ../src/jabcode/pseudo_random.c
)

# Mobile bridge source
set(MOBILE_BRIDGE_SOURCES
    src/c/mobile_bridge.c
    src/c/mobile_utils.c
)

# Create static library
add_library(jabcode-mobile STATIC 
    ${JABCODE_SOURCES}
    ${MOBILE_BRIDGE_SOURCES}
)

# Link against math library only (NO png/tiff)
target_link_libraries(jabcode-mobile m)

# Set output directory
set_target_properties(jabcode-mobile PROPERTIES
    ARCHIVE_OUTPUT_DIRECTORY ${CMAKE_CURRENT_SOURCE_DIR}/build/lib
)

# Testing
enable_testing()

# Unity test framework (lightweight C testing)
add_library(unity STATIC
    test/c/unity/unity.c
)
target_include_directories(unity PUBLIC
    ${CMAKE_CURRENT_SOURCE_DIR}/test/c/unity
)

# Test executable (single main, not linking test files with multiple mains)
add_executable(mobile_bridge_tests
    test/c/test_mobile_bridge.c
)

target_link_libraries(mobile_bridge_tests
    jabcode-mobile
    unity
    m
)

# Add test
add_test(NAME mobile_bridge_tests COMMAND mobile_bridge_tests)

# Encoding roundtrip test (separate executable)
add_executable(test_encode_roundtrip
    test/c/test_encode_roundtrip.c
)

target_link_libraries(test_encode_roundtrip
    jabcode-mobile
    unity
    m
)

add_test(NAME test_encode_roundtrip COMMAND test_encode_roundtrip)

# Bitmap debug test
add_executable(test_bitmap_debug
    test/c/test_bitmap_debug.c
)

target_link_libraries(test_bitmap_debug
    jabcode-mobile
    m
)

# Print summary
message(STATUS "JABCode Mobile Build Configuration:")
message(STATUS "  Build type: ${CMAKE_BUILD_TYPE}")
message(STATUS "  C Compiler: ${CMAKE_C_COMPILER}")
message(STATUS "  C Standard: C${CMAKE_C_STANDARD}")
message(STATUS "  Mobile Build: ENABLED")
message(STATUS "  Image I/O: DISABLED (no libpng/libtiff)")
