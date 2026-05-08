#!/usr/bin/env python3
"""
Extract and validate the metadata traversal pattern from ISO Figure 9.

Based on visual analysis of Figure 9:
- Modules spiral from center to 4 corners
- Pattern: clockwise rotation through corners
- Each corner gets modules in sequence
"""

def extract_figure9_pattern():
    """Extract the exact module sequence from Figure 9 visual"""
    
    print("=== FIGURE 9 PATTERN EXTRACTION ===\n")
    
    # From visual observation of Figure 9
    part1 = {
        0: "top center",
        1: "top right area", 
        2: "bottom center",
        3: "center positions"
    }
    
    # Color palette + Part II modules by corner
    upper_left_cyan = [4, 8, 12, 16, 20, 24, 28, 32, 36, 40]
    upper_right_yellow = [1, 5, 9, 13, 17, 21, 25, 29, 33, 37]
    lower_left_cyan = [7, 11, 15, 19, 23, 27, 31, 35, 39]
    lower_right_yellow = [6, 10, 14, 18, 22, 26, 30, 34, 38]
    
    print("Part I (modules 0-3):")
    for mod, pos in part1.items():
        print(f"  Module {mod}: {pos}")
    
    print("\nCorner assignments (palette + Part II):")
    print(f"  Upper Left (Cyan):   {upper_left_cyan}")
    print(f"  Upper Right (Yellow): {upper_right_yellow}")
    print(f"  Lower Right (Yellow): {lower_right_yellow}")
    print(f"  Lower Left (Cyan):    {lower_left_cyan}")
    
    # Analyze the pattern
    print("\n=== PATTERN ANALYSIS ===\n")
    
    # Check if it's a simple modulo-4 pattern
    all_modules = sorted(
        upper_left_cyan + upper_right_yellow + 
        lower_right_yellow + lower_left_cyan
    )
    
    print(f"All palette/Part II modules: {all_modules[:20]}...")
    print(f"Total modules shown: {len(all_modules)}")
    
    # Group by modulo 4
    mod0 = [m for m in all_modules if m % 4 == 0]
    mod1 = [m for m in all_modules if m % 4 == 1]
    mod2 = [m for m in all_modules if m % 4 == 2]
    mod3 = [m for m in all_modules if m % 4 == 3]
    
    print("\nGrouped by mod 4:")
    print(f"  mod 0: {mod0}")
    print(f"  mod 1: {mod1}")
    print(f"  mod 2: {mod2}")
    print(f"  mod 3: {mod3}")
    
    # Check corner assignments by modulo
    print("\nCorner assignment pattern:")
    
    def check_corner(modules, name):
        mods = [m % 4 for m in modules]
        print(f"  {name:20s}: modulo pattern = {set(mods)}")
    
    check_corner(upper_left_cyan, "Upper Left (Cyan)")
    check_corner(upper_right_yellow, "Upper Right (Yellow)")
    check_corner(lower_right_yellow, "Lower Right (Yellow)")
    check_corner(lower_left_cyan, "Lower Left (Cyan)")
    
    # Key insight
    print("\n=== KEY INSIGHT ===\n")
    print("Upper Left gets:   mod 4 == 0  (4, 8, 12, 16, 20...)")
    print("Upper Right gets:  mod 4 == 1  (1, 5, 9, 13, 17...)")
    print("Lower Right gets:  mod 4 == 2  (6, 10, 14, 18, 22...)")
    print("Lower Left gets:   mod 4 == 3  (7, 11, 15, 19, 23...)")
    
    print("\nPattern: Clockwise rotation through 4 corners!")
    print("Each corner receives every 4th module in sequence")
    
    return all_modules


def map_to_coordinates():
    """
    Map module numbers to (x, y) coordinates based on Figure 9.
    This will reveal the actual traversal algorithm.
    """
    
    print("\n=== COORDINATE MAPPING ===\n")
    print("Matrix: 21x21, Center: (10, 10)")
    print("Corners at: (0,0), (20,0), (20,20), (0,20)\n")
    
    # From Figure 9, identify specific coordinate positions
    # Module 0-3: Part I metadata (center region)
    # Module 4+: Spiral outward to corners
    
    # Upper left corner modules (cyan): 4, 8, 12, 16, 20...
    # These should be at positions near (0, 0) - (3, 3)
    
    # Upper right corner modules (yellow): 1, 5, 9, 13, 17...  
    # These should be near (17, 0) - (20, 3)
    
    # The pattern appears to be:
    # 1. Start at center (10, 10)
    # 2. Move outward in a spiral
    # 3. Rotate through 4 quadrants
    
    print("Hypothesis: Modules spiral from center (10,10) to corners")
    print("Pattern rotates: NW → NE → SE → SW → repeat")
    print("\nThis matches the mod-4 pattern:")
    print("  mod 0 → NW (upper left)")
    print("  mod 1 → NE (upper right)")
    print("  mod 2 → SE (lower right)")
    print("  mod 3 → SW (lower left)")
    
    return True


def extrapolate_for_high_colors():
    """Extrapolate pattern for 64-color and 128-color modes"""
    
    print("\n=== EXTRAPOLATION FOR HIGH COLOR MODES ===\n")
    
    # The pattern is a simple modulo-4 rotation
    # It should continue indefinitely!
    
    print("For 64-color mode (module 252 needed):")
    print(f"  Module 252 % 4 = {252 % 4} → Should go to corner 0 (NW/upper left)")
    
    print("\nFor 128-color mode (module 508 needed):")
    print(f"  Module 508 % 4 = {508 % 4} → Should go to corner 0 (NW/upper left)")
    
    print("\n=== ALGORITHM EXTENSION ===\n")
    print("The existing hardcoded ranges (0-172) were ARTIFICIALLY LIMITED")
    print("The TRUE pattern is INFINITE and based on:")
    print("  1. Modulo-4 determines which corner")
    print("  2. Spiral outward from center")
    print("  3. Coordinate flipping pattern continues")
    
    print("\nThe fix should:")
    print("  1. Remove hardcoded range limits (156-172)")
    print("  2. Extend to support arbitrary module counts")
    print("  3. Keep the modulo-4 rotation pattern")
    print("  4. Continue the spiral/coordinate advancement")


if __name__ == "__main__":
    # Extract pattern from Figure 9
    modules = extract_figure9_pattern()
    
    # Map to coordinate space
    map_to_coordinates()
    
    # Extrapolate for high colors
    extrapolate_for_high_colors()
    
    print("\n=== NEXT STEP ===")
    print("Create C implementation that extends ranges to 548+ modules")
    print("Pattern formula: Continue Y-increment and X-decrement ranges")
    print("Incrementing by +4 at each transition (21→25→29, 23→27→31)")
