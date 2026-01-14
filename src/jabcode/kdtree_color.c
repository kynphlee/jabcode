/**
 * K-d Tree Implementation for Fast Color Quantization
 * 
 * Uses median-split k-d tree in LAB color space for O(log n) nearest-neighbor lookup
 */

#include "kdtree_color.h"
#include <stdlib.h>
#include <string.h>
#include <math.h>

// Helper structure for building tree
typedef struct {
    jab_lab_color lab;
    jab_byte index;
    jab_byte* rgb;
} color_point;

// Comparison functions for quickselect
static int compare_l(const void* a, const void* b) {
    jab_float diff = ((color_point*)a)->lab.L - ((color_point*)b)->lab.L;
    return (diff > 0) - (diff < 0);
}

static int compare_a(const void* a, const void* b) {
    jab_float diff = ((color_point*)a)->lab.a - ((color_point*)b)->lab.a;
    return (diff > 0) - (diff < 0);
}

static int compare_b(const void* a, const void* b) {
    jab_float diff = ((color_point*)a)->lab.b - ((color_point*)b)->lab.b;
    return (diff > 0) - (diff < 0);
}

/**
 * Recursively build k-d tree
 */
static kd_node* build_recursive(color_point* points, jab_int32 start, jab_int32 end, jab_int32 depth) {
    if (start > end) return NULL;
    
    // Select splitting dimension (L, a, b cycling)
    jab_int32 axis = depth % 3;
    
    // Sort along selected axis and find median
    jab_int32 count = end - start + 1;
    jab_int32 median_idx = start + count / 2;
    
    // Use qsort for median finding (simple, fast for small arrays)
    int (*cmp_func)(const void*, const void*) = 
        (axis == 0) ? compare_l : (axis == 1) ? compare_a : compare_b;
    qsort(&points[start], count, sizeof(color_point), cmp_func);
    
    // Create node
    kd_node* node = (kd_node*)malloc(sizeof(kd_node));
    if (!node) return NULL;
    
    node->color = points[median_idx].lab;
    node->color_index = points[median_idx].index;
    node->rgb = points[median_idx].rgb;
    
    // Recursively build subtrees
    node->left = build_recursive(points, start, median_idx - 1, depth + 1);
    node->right = build_recursive(points, median_idx + 1, end, depth + 1);
    
    return node;
}

/**
 * Build k-d tree from palette
 */
kdtree_color* kdtree_build(jab_byte* palette, jab_int32 color_number, jab_int32 palette_index) {
    if (!palette || color_number <= 0) return NULL;
    
    // Allocate tree structure
    kdtree_color* tree = (kdtree_color*)malloc(sizeof(kdtree_color));
    if (!tree) return NULL;
    
    tree->color_count = color_number;
    
    // Create array of color points
    color_point* points = (color_point*)malloc(color_number * sizeof(color_point));
    if (!points) {
        free(tree);
        return NULL;
    }
    
    // Convert palette colors to LAB and store
    for (jab_int32 i = 0; i < color_number; i++) {
        jab_int32 offset = color_number * 3 * palette_index + i * 3;
        jab_byte r = palette[offset + 0];
        jab_byte g = palette[offset + 1];
        jab_byte b = palette[offset + 2];
        
        jab_rgb_color rgb_color = {r, g, b};
        points[i].lab = rgb_to_lab(rgb_color);
        points[i].index = (jab_byte)i;
        points[i].rgb = &palette[offset];
    }
    
    // Build tree
    tree->root = build_recursive(points, 0, color_number - 1, 0);
    
    // Free temporary array
    free(points);
    
    return tree;
}

/**
 * Recursively search k-d tree for nearest neighbor
 */
static void search_recursive(kd_node* node, jab_lab_color query, jab_int32 depth,
                            jab_byte* best_index, jab_float* best_dist) {
    if (!node) return;
    
    // Calculate distance to current node
    jab_float dist = delta_e_76(query, node->color);
    
    // Update best if this is closer
    if (dist < *best_dist) {
        *best_dist = dist;
        *best_index = node->color_index;
    }
    
    // Determine which subtree to search first
    jab_int32 axis = depth % 3;
    jab_float query_val = (axis == 0) ? query.L : (axis == 1) ? query.a : query.b;
    jab_float node_val = (axis == 0) ? node->color.L : (axis == 1) ? node->color.a : node->color.b;
    jab_float axis_dist = query_val - node_val;
    
    // Search near subtree first
    kd_node* near = (axis_dist < 0) ? node->left : node->right;
    kd_node* far = (axis_dist < 0) ? node->right : node->left;
    
    search_recursive(near, query, depth + 1, best_index, best_dist);
    
    // Check if we need to search far subtree
    // Only if splitting plane is within current best distance
    if (fabs(axis_dist) < *best_dist) {
        search_recursive(far, query, depth + 1, best_index, best_dist);
    }
}

/**
 * Find nearest color using k-d tree
 */
jab_byte kdtree_nearest(kdtree_color* tree, jab_lab_color query_lab) {
    if (!tree || !tree->root) return 0;
    
    jab_byte best_index = 0;
    jab_float best_dist = 1e10f;
    
    search_recursive(tree->root, query_lab, 0, &best_index, &best_dist);
    
    return best_index;
}

/**
 * Recursively free k-d tree nodes
 */
static void free_recursive(kd_node* node) {
    if (!node) return;
    
    free_recursive(node->left);
    free_recursive(node->right);
    free(node);
}

/**
 * Free k-d tree
 */
void kdtree_free(kdtree_color* tree) {
    if (!tree) return;
    
    free_recursive(tree->root);
    free(tree);
}
