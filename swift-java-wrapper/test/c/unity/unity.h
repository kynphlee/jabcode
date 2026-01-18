/* Unity Test Framework - Minimal Header */
#ifndef UNITY_H
#define UNITY_H

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define TEST_ASSERT(condition) \
    do { if (!(condition)) { \
        printf("FAIL: %s:%d: %s\n", __FILE__, __LINE__, #condition); \
        exit(1); \
    } } while(0)

#define TEST_ASSERT_TRUE(condition) TEST_ASSERT(condition)
#define TEST_ASSERT_FALSE(condition) TEST_ASSERT(!(condition))
#define TEST_ASSERT_NULL(ptr) TEST_ASSERT((ptr) == NULL)
#define TEST_ASSERT_NOT_NULL(ptr) TEST_ASSERT((ptr) != NULL)
#define TEST_ASSERT_EQUAL(expected, actual) TEST_ASSERT((expected) == (actual))
#define TEST_ASSERT_EQUAL_STRING(expected, actual) \
    TEST_ASSERT(strcmp((expected), (actual)) == 0)

void setUp(void) {}
void tearDown(void) {}

#endif // UNITY_H
