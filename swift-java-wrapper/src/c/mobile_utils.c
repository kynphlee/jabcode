/**
 * Mobile utility functions
 * Provides stubs for functions that exist in desktop apps but not in library
 */

#include "jabcode.h"
#include <stdio.h>
#include <stdarg.h>

/**
 * @brief Report error message (stub for mobile - errors handled via jabMobileGetLastError)
 * Note: The full reportError implementation is now in the desktop encoder.c
 * This stub is kept for mobile-specific error handling via jabMobileGetLastError()
 */
void reportError(jab_char* message) {
    // On mobile, errors are returned via jabMobileGetLastError()
    // Desktop encoder.c has its own reportError that prints to stdout
    #ifdef DEBUG_MOBILE
    fprintf(stderr, "JABCode Mobile Error: %s\n", message);
    #endif
    (void)message; // Suppress unused parameter warning
}
