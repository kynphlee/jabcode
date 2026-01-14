/**
 * Performance Timing Utilities for JABCode Decoder Profiling
 * 
 * Usage:
 *   TIMING_START();
 *   // ... code to measure ...
 *   TIMING_END("Phase Name");
 */

#ifndef JABCODE_TIMING_H
#define JABCODE_TIMING_H

#define _POSIX_C_SOURCE 199309L
#include <time.h>
#include <stdio.h>

// Enable/disable timing with this flag
#define JABCODE_TIMING_ENABLED 1

#if JABCODE_TIMING_ENABLED

// Get current time in milliseconds
static inline double get_time_ms(void) {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return ts.tv_sec * 1000.0 + ts.tv_nsec / 1000000.0;
}

// Timing macros - output to file for JVM visibility
#define TIMING_START() \
    double _timing_start = get_time_ms();

#define TIMING_END(phase_name) \
    do { \
        double _timing_end = get_time_ms(); \
        double _timing_duration = _timing_end - _timing_start; \
        FILE* _timing_log = fopen("/tmp/jabcode-timing.log", "a"); \
        if (_timing_log) { \
            fprintf(_timing_log, "[TIMING] %s: %.3f ms\n", phase_name, _timing_duration); \
            fclose(_timing_log); \
        } \
    } while(0)

#define TIMING_CHECKPOINT(phase_name) \
    do { \
        double _timing_checkpoint = get_time_ms(); \
        double _timing_duration = _timing_checkpoint - _timing_start; \
        FILE* _timing_log = fopen("/tmp/jabcode-timing.log", "a"); \
        if (_timing_log) { \
            fprintf(_timing_log, "[TIMING] %s: %.3f ms\n", phase_name, _timing_duration); \
            fclose(_timing_log); \
        } \
        _timing_start = _timing_checkpoint; \
    } while(0)

#else

#define TIMING_START()
#define TIMING_END(phase_name)
#define TIMING_CHECKPOINT(phase_name)

#endif

#endif // JABCODE_TIMING_H
