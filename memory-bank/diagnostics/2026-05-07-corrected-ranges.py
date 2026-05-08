#!/usr/bin/env python3
"""
Calculate CORRECTED ranges for metadata traversal.
My previous attempt had errors after module 292.
"""

def calculate_correct_ranges():
    """Calculate ranges with proper +4 increment pattern"""
    
    print("=== CORRECTED RANGE CALCULATION ===\n")
    
    # Pattern: Y starts at 21, X starts at 23, both increment by +4
    y_ranges = []
    x_ranges = []
    swap_points = []
    
    y_len = 21
    x_len = 23
    pos = 0
    
    for cycle in range(10):  # Generate 10 cycles (more than enough)
        # Y-range
        y_start = pos
        y_end = y_start + y_len - 1
        y_ranges.append((y_start, y_end, y_len))
        pos = y_end + 1
        
        # X-range
        x_start = pos
        x_end = x_start + x_len - 1
        x_ranges.append((x_start, x_end, x_len))
        pos = x_end + 1
        
        # Swap point
        swap_points.append(pos)
        pos += 1  # Skip swap point module (it's part of next Y-range)
        
        # Increment
        y_len += 4
        x_len += 4
        
        if pos > 600:
            break
    
    print("Y-INCREMENT RANGES:")
    for i, (start, end, length) in enumerate(y_ranges):
        print(f"  Cycle {i}: [{start:3d}, {end:3d}] length={length:2d}")
    
    print("\nX-DECREMENT RANGES:")
    for i, (start, end, length) in enumerate(x_ranges):
        print(f"  Cycle {i}: [{start:3d}, {end:3d}] length={length:2d}")
    
    print("\nSWAP POINTS:")
    print(f"  {swap_points}")
    
    return y_ranges, x_ranges, swap_points


def generate_c_fix(y_ranges, x_ranges, swap_points):
    """Generate the correct C code"""
    
    print("\n=== CORRECTED C CODE ===\n")
    
    # Only generate ranges up to module 548 (enough for 128-color)
    y_ranges_needed = [r for r in y_ranges if r[0] <= 548]
    x_ranges_needed = [r for r in x_ranges if r[0] <= 548]
    swap_points_needed = [s for s in swap_points if s <= 548]
    
    print("// Y-increment ranges (mod4 == 0):")
    print("if(")
    conditions = []
    for start, end, _ in y_ranges_needed:
        conditions.append(f"   (next_module_count >= {start:3d} && next_module_count <= {min(end, 548):3d})")
    print(" ||\n".join(conditions))
    print("  )")
    print("{")
    print("    (*y) += 1;")
    print("}")
    
    print("\n// X-decrement ranges (mod4 == 0):")
    print("else if(")
    conditions = []
    for start, end, _ in x_ranges_needed:
        conditions.append(f"   (next_module_count > {start-1:3d} && next_module_count < {min(end+1, 548):3d})")
    print(" ||\n".join(conditions))
    print("  )")
    print("{")
    print("    (*x) -= 1;")
    print("}")
    
    print("\n// Swap points:")
    swap_str = " || ".join([f"next_module_count == {s}" for s in swap_points_needed])
    print(f"if({swap_str})")
    print("{")
    print("    jab_int32 tmp = (*x);")
    print("    (*x) = (*y);")
    print("    (*y) = tmp;")
    print("}")
    
    print("\n=== VALIDATION ===")
    print(f"✓ Generated {len(y_ranges_needed)} Y-ranges")
    print(f"✓ Generated {len(x_ranges_needed)} X-ranges")
    print(f"✓ Generated {len(swap_points_needed)} swap points")
    print(f"✓ Coverage: 0-{y_ranges_needed[-1][1]} modules")


if __name__ == "__main__":
    y_ranges, x_ranges, swap_points = calculate_correct_ranges()
    generate_c_fix(y_ranges, x_ranges, swap_points)
