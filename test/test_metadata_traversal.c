/**
 * Test program to validate getNextMetadataModuleInMaster() fix
 * Verifies coordinate uniqueness for 64-color and 128-color modes
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define MASTER_METADATA_X 10
#define MASTER_METADATA_Y 10
#define COLOR_PALETTE_NUMBER 4

// Copy of the FIXED function from decoder.c
void getNextMetadataModuleInMaster(int matrix_height, int matrix_width, int next_module_count, int* x, int* y)
{
	// Flip coordinates based on modulo pattern
	if(next_module_count % 4 == 0 || next_module_count % 4 == 2)
	{
		(*y) = matrix_height - 1 - (*y);
	}
	if(next_module_count % 4 == 1 || next_module_count % 4 == 3)
	{
		(*x) = matrix_width -1 - (*x);
	}
	
	// Advance coordinates
	int mod4 = next_module_count % 4;
	
	if(mod4 == 0)
	{
        if( next_module_count <= 20 ||
           (next_module_count >= 44  && next_module_count <= 68)  ||
           (next_module_count >= 96  && next_module_count <= 124) ||
           (next_module_count >= 156 && next_module_count <= 188) ||
           (next_module_count >= 224 && next_module_count <= 260) ||
           (next_module_count >= 300 && next_module_count <= 340) ||
           (next_module_count >= 384 && next_module_count <= 428) ||
           (next_module_count >= 476 && next_module_count <= 524))
		{
			(*y) += 1;
		}
        else if((next_module_count > 20  && next_module_count < 44)  ||
                (next_module_count > 68  && next_module_count < 96)  ||
                (next_module_count > 124 && next_module_count < 156) ||
                (next_module_count > 188 && next_module_count < 224) ||
                (next_module_count > 260 && next_module_count < 300) ||
                (next_module_count > 340 && next_module_count < 384) ||
                (next_module_count > 428 && next_module_count < 476))
		{
			(*x) -= 1;
		}
	}
	else if(mod4 == 1 || mod4 == 2 || mod4 == 3)
	{
		// For counts > 156, advance on ALL modulo cases to escape fixed-point coordinates
		if(next_module_count > 156)
		{
			if((next_module_count >= 157 && next_module_count <= 188) ||
			   (next_module_count >= 224 && next_module_count <= 260) ||
			   (next_module_count >= 300 && next_module_count <= 340) ||
			   (next_module_count >= 384 && next_module_count <= 428) ||
			   (next_module_count >= 476 && next_module_count <= 524))
			{
				(*y) += 1;
			}
			else if((next_module_count > 188 && next_module_count < 224) ||
			        (next_module_count > 260 && next_module_count < 300) ||
			        (next_module_count > 340 && next_module_count < 384) ||
			        (next_module_count > 428 && next_module_count < 476))
			{
				(*x) -= 1;
			}
		}
	}
	
    if(next_module_count == 44  || next_module_count == 96  || next_module_count == 156 ||
       next_module_count == 224 || next_module_count == 300 || next_module_count == 384 ||
       next_module_count == 476)
    {
        int tmp = (*x);
        (*x) = (*y);
        (*y) = tmp;
    }
}

// Test coordinate uniqueness
int test_coordinate_uniqueness(int color_number, int max_modules)
{
    int matrix_size = 21;
    int x = MASTER_METADATA_X;
    int y = MASTER_METADATA_Y;
    
    // Track all coordinates
    int* coords = calloc(max_modules * 2, sizeof(int));
    if (!coords) {
        fprintf(stderr, "Memory allocation failed\n");
        return -1;
    }
    
    coords[0] = x;
    coords[1] = y;
    
    printf("\n=== Testing %d-color mode (%d modules) ===\n", color_number, max_modules);
    printf("Matrix: %dx%d, Center: (%d,%d)\n\n", matrix_size, matrix_size, x, y);
    
    // Generate coordinates
    for(int module_count = 1; module_count < max_modules; module_count++)
    {
        getNextMetadataModuleInMaster(matrix_size, matrix_size, module_count, &x, &y);
        
        coords[module_count * 2] = x;
        coords[module_count * 2 + 1] = y;
        
        // Show first 10 and last 10
        if(module_count < 10 || module_count >= max_modules - 10) {
            printf("Module %3d: (%2d, %2d)\n", module_count, x, y);
        } else if(module_count == 10) {
            printf("...\n");
        }
    }
    
    // Check for duplicates
    int duplicate_count = 0;
    for(int i = 0; i < max_modules; i++)
    {
        for(int j = i + 1; j < max_modules; j++)
        {
            if(coords[i*2] == coords[j*2] && coords[i*2+1] == coords[j*2+1])
            {
                printf("⚠️  DUPLICATE: Module %d and %d both at (%d,%d)\n",
                       i, j, coords[i*2], coords[i*2+1]);
                duplicate_count++;
            }
        }
    }
    
    free(coords);
    
    if(duplicate_count == 0) {
        printf("\n✓ All %d coordinates are UNIQUE\n", max_modules);
        return 0;
    } else {
        printf("\n❌ Found %d duplicate coordinate pairs\n", duplicate_count);
        return 1;
    }
}

int main(void)
{
    printf("=== Metadata Traversal Fix Validation ===\n");
    printf("Testing coordinate uniqueness for high color modes\n");
    
    int failures = 0;
    
    // Test 64-color mode (requires 252+ modules for Part II)
    // Part I: 4 modules, Palette: 62 colors × 4 = 248 modules, Total: 252
    failures += test_coordinate_uniqueness(64, 260);
    
    // Test 128-color mode (requires 508+ modules for Part II)
    // Part I: 4 modules, Palette: 126 colors × 4 = 504 modules, Total: 508
    failures += test_coordinate_uniqueness(128, 520);
    
    printf("\n=== FINAL RESULT ===\n");
    if(failures == 0) {
        printf("✓ ALL TESTS PASSED - Fix is correct!\n");
        printf("✓ 64-color mode: Coordinates unique up to module 260\n");
        printf("✓ 128-color mode: Coordinates unique up to module 520\n");
        return 0;
    } else {
        printf("❌ %d TEST(S) FAILED - Fix needs correction\n", failures);
        return 1;
    }
}
