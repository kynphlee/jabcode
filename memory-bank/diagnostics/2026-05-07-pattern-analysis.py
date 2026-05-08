#!/usr/bin/env python3
"""
Reverse-engineer metadata traversal pattern from existing hardcoded ranges.
Goal: Understand the algorithm to extend it for 64/128-color modes.
"""

def analyze_existing_pattern():
    """Analyze the hardcoded ranges from getNextMetadataModuleInMaster()"""
    
    # From official JABCode implementation
    y_increment_ranges = [
        (0, 20),
        (44, 68),
        (96, 124),
        (156, 172)  # STOPS HERE - max module 172
    ]
    
    x_decrement_ranges = [
        (21, 43),
        (69, 95),
        (125, 155)
    ]
    
    swap_points = [44, 96, 156]
    
    print("=== EXISTING PATTERN ANALYSIS ===\n")
    print("Y-INCREMENT RANGES (mod4 == 0):")
    for start, end in y_increment_ranges:
        count = end - start + 1
        print(f"  [{start:3d}, {end:3d}] = {count:2d} modules")
    
    print("\nX-DECREMENT RANGES (mod4 == 0):")
    for start, end in x_decrement_ranges:
        count = end - start + 1
        print(f"  [{start:3d}, {end:3d}] = {count:2d} modules")
    
    print("\nSWAP POINTS (coordinate flip x<->y):")
    for point in swap_points:
        print(f"  Module {point}")
    
    # Pattern analysis
    print("\n=== PATTERN STRUCTURE ===\n")
    
    # Calculate differences
    print("Y-range lengths:")
    y_lengths = [end - start + 1 for start, end in y_increment_ranges]
    print(f"  {y_lengths}")
    
    print("\nX-range lengths:")
    x_lengths = [end - start + 1 for start, end in x_decrement_ranges]
    print(f"  {x_lengths}")
    
    print("\nSwap point spacing:")
    swap_spacing = [swap_points[i+1] - swap_points[i] for i in range(len(swap_points)-1)]
    print(f"  {swap_spacing}")
    
    # Pattern hypothesis
    print("\n=== PATTERN HYPOTHESIS ===\n")
    print("Observation: Ranges alternate between Y-increment and X-decrement")
    print("Observation: Swap points occur at end of X-decrement ranges + 1")
    print("\nPattern appears to be:")
    print("  1. Y-increment for N modules")
    print("  2. X-decrement for M modules")
    print("  3. Swap coordinates (x<->y)")
    print("  4. Repeat")
    
    # Try to find the formula
    print("\n=== ATTEMPTING TO EXTRAPOLATE ===\n")
    
    # First Y range: 0-20 = 21 modules
    # Gap: 21-43 = 23 modules (but that's in X-decrement)
    # First X range: 21-43 = 23 modules
    # Swap at 44
    
    # Pattern might be based on matrix expansion
    # Initial: (10, 10) center
    # Matrix: 21x21
    
    # Let's simulate what should happen for 64-color
    print("For 64-color mode:")
    print("  Part I: 4 modules")
    print("  Palette: 62 colors × 4 corners = 248 modules")
    print("  Part II starts at: module 252")
    print("  PROBLEM: Current max = 172, needed = 252+")
    
    print("\nFor 128-color mode:")
    print("  Part I: 4 modules")
    print("  Palette: 126 colors × 4 corners = 504 modules")
    print("  Part II starts at: module 508")
    print("  PROBLEM: Current max = 172, needed = 508+")
    
    return y_increment_ranges, x_decrement_ranges, swap_points


def generate_coordinate_sequence(matrix_size=21, max_modules=200):
    """
    Generate coordinate sequence using the reverse-engineered pattern.
    Try to understand the spiral/traversal logic.
    """
    
    print("\n=== COORDINATE SEQUENCE SIMULATION ===\n")
    print(f"Matrix: {matrix_size}x{matrix_size}")
    print(f"Center: ({matrix_size//2}, {matrix_size//2})\n")
    
    # Start at center
    x, y = matrix_size // 2, matrix_size // 2
    
    coordinates = [(x, y)]
    
    # Modulo-4 pattern with coordinate flipping
    for module_count in range(1, min(max_modules, 180)):
        mod4 = module_count % 4
        
        # Flip coordinates based on modulo pattern (from existing code)
        if mod4 == 0 or mod4 == 2:
            y = matrix_size - 1 - y
        if mod4 == 1 or mod4 == 3:
            x = matrix_size - 1 - x
        
        # Advance coordinates (only when mod4 == 0 in existing code)
        if mod4 == 0:
            if module_count <= 20:
                y += 1
            elif 44 <= module_count <= 68:
                y += 1
            elif 96 <= module_count <= 124:
                y += 1
            elif 156 <= module_count <= 172:
                y += 1
            elif 21 < module_count < 44:
                x -= 1
            elif 69 < module_count < 96:
                x -= 1
            elif 125 < module_count < 156:
                x -= 1
        
        # Swap at specific points
        if module_count in [44, 96, 156]:
            x, y = y, x
        
        coordinates.append((x, y))
        
        # Check for duplicates (stuck bug indicator)
        if len(coordinates) > 1 and coordinates[-1] == coordinates[-2]:
            print(f"⚠️  STUCK at module {module_count}: {coordinates[-1]}")
            break
    
    # Check for unique coordinates
    unique = set(coordinates)
    if len(unique) < len(coordinates):
        duplicates = len(coordinates) - len(unique)
        print(f"⚠️  Found {duplicates} duplicate coordinates!\n")
    else:
        print(f"✓ All {len(coordinates)} coordinates are unique\n")
    
    # Show first 20 and last 20
    print("First 20 coordinates:")
    for i in range(min(20, len(coordinates))):
        print(f"  Module {i:3d}: ({coordinates[i][0]:2d}, {coordinates[i][1]:2d})")
    
    if len(coordinates) > 40:
        print("\n...")
        print(f"\nLast 20 coordinates:")
        for i in range(len(coordinates) - 20, len(coordinates)):
            print(f"  Module {i:3d}: ({coordinates[i][0]:2d}, {coordinates[i][1]:2d})")
    
    return coordinates


if __name__ == "__main__":
    # Analyze existing pattern
    analyze_existing_pattern()
    
    # Try to generate sequence
    coords = generate_coordinate_sequence(matrix_size=21, max_modules=200)
    
    print("\n=== NEXT STEP ===")
    print("Need to examine Figure 9 from PDF visually to understand true pattern.")
    print("Python simulation shows existing algorithm creates unique coords up to ~172")
    print("But algorithm was never extended for 64/128-color (252+, 508+ modules)")
