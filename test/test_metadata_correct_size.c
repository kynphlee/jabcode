/**
 * Test program with CORRECT matrix sizes for each color mode
 * 64-color: Version 2 (25×25 = 625 modules)
 * 128-color: Version 2 (25×25 = 625 modules)
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define MASTER_METADATA_X 6   // From decoder.h - NOT center!
#define MASTER_METADATA_Y 1
#define COLOR_PALETTE_NUMBER 4

// VERSION2SIZE formula: version * 4 + 17
#define VERSION2SIZE(v) ((v) * 4 + 17)

// Copy of the FIXED function from decoder.c
void getNextMetadataModuleInMaster(int matrix_height, int matrix_width, int next_module_count, int* x, int* y)
{
	if(next_module_count % 4 == 0 || next_module_count % 4 == 2)
	{
		(*y) = matrix_height - 1 - (*y);
	}
	if(next_module_count % 4 == 1 || next_module_count % 4 == 3)
	{
		(*x) = matrix_width -1 - (*x);
	}
	
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

int test_coordinate_uniqueness(int color_number, int version, int max_modules)
{
    int matrix_size = VERSION2SIZE(version);
    int x = MASTER_METADATA_X;  // Start position from spec
    int y = MASTER_METADATA_Y;
    
    int* coords = calloc(max_modules * 2, sizeof(int));
    if (!coords) {
        fprintf(stderr, "Memory allocation failed\n");
        return -1;
    }
    
    coords[0] = x;
    coords[1] = y;
    
    printf("\n=== Testing %d-color mode (version %d, %dx%d, %d modules) ===\n", 
           color_number, version, matrix_size, matrix_size, max_modules);
    printf("Start: (%d,%d)\n\n", x, y);
    
    int out_of_bounds = 0;
    
    for(int module_count = 1; module_count < max_modules; module_count++)
    {
        getNextMetadataModuleInMaster(matrix_size, matrix_size, module_count, &x, &y);
        
        coords[module_count * 2] = x;
        coords[module_count * 2 + 1] = y;
        
        // Check bounds
        if(x < 0 || x >= matrix_size || y < 0 || y >= matrix_size) {
            if(out_of_bounds < 5) {
                printf("⚠️  OUT OF BOUNDS: Module %d at (%d,%d) outside [0,%d]\n",
                       module_count, x, y, matrix_size-1);
            }
            out_of_bounds++;
        }
        
        if(module_count < 10 || module_count >= max_modules - 10) {
            printf("Module %3d: (%2d, %2d)\n", module_count, x, y);
        } else if(module_count == 10) {
            printf("...\n");
        }
    }
    
    if(out_of_bounds > 0) {
        printf("\n❌ %d modules OUT OF BOUNDS (matrix: %dx%d)\n", out_of_bounds, matrix_size, matrix_size);
        free(coords);
        return 1;
    }
    
    // Check for duplicates
    int duplicate_count = 0;
    for(int i = 0; i < max_modules && duplicate_count < 10; i++)
    {
        for(int j = i + 1; j < max_modules; j++)
        {
            if(coords[i*2] == coords[j*2] && coords[i*2+1] == coords[j*2+1])
            {
                printf("⚠️  DUPLICATE: Module %d and %d both at (%d,%d)\n",
                       i, j, coords[i*2], coords[i*2+1]);
                duplicate_count++;
                if(duplicate_count >= 10) break;
            }
        }
    }
    
    free(coords);
    
    if(duplicate_count == 0) {
        printf("\n✓ All %d coordinates are UNIQUE and IN BOUNDS\n", max_modules);
        return 0;
    } else {
        printf("\n❌ Found duplicate coordinates (showing first 10)\n");
        return 1;
    }
}

int main(void)
{
    printf("=== Metadata Traversal Fix Validation (Correct Matrix Sizes) ===\n");
    
    int failures = 0;
    
    // 64-color: 252+ modules, should use version 2 (25×25 = 625 modules)
    failures += test_coordinate_uniqueness(64, 2, 260);
    
    // 128-color: 508+ modules, should use version 2 (25×25 = 625 modules)  
    failures += test_coordinate_uniqueness(128, 2, 520);
    
    printf("\n=== FINAL RESULT ===\n");
    if(failures == 0) {
        printf("✓ ALL TESTS PASSED - Fix is correct!\n");
        return 0;
    } else {
        printf("❌ %d TEST(S) FAILED\n", failures);
        return 1;
    }
}
