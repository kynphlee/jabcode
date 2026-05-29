package com.jabcode.panama;

/**
 * Represents the version (dimensions) of a JABCode symbol.
 * 
 * <p>JABCode symbols have versions from 1×1 to 32×32, where each version
 * determines the number of modules in each dimension. For example, version
 * 10×10 creates a symbol with 10 modules width × 10 modules height.</p>
 * 
 * <p>This class is used primarily for cascaded multi-symbol encoding, where
 * you need to explicitly specify the version of each symbol in the cascade.</p>
 * 
 * <h2>Version to Module Mapping</h2>
 * <p>The actual module count depends on the version number:</p>
 * <ul>
 *   <li>Version 1: 21×21 modules</li>
 *   <li>Version 2: 25×25 modules</li>
 *   <li>Version n: (17 + 4n)×(17 + 4n) modules</li>
 *   <li>Version 32: 145×145 modules (maximum)</li>
 * </ul>
 * 
 * <h2>Example Usage</h2>
 * <pre>{@code
 * // Create a 2-symbol cascade with different versions
 * var config = JABCodeEncoder.Config.builder()
 *     .colorNumber(64)
 *     .symbolNumber(2)
 *     .symbolVersions(List.of(
 *         new SymbolVersion(10, 10),  // Primary: larger symbol
 *         new SymbolVersion(8, 8)      // Secondary: smaller symbol
 *     ))
 *     .build();
 * }</pre>
 * 
 * @see JABCodeEncoder.Config.Builder#symbolVersions(java.util.List)
 */
public final class SymbolVersion {
    
    /**
     * Minimum symbol version (1×1)
     */
    public static final int MIN_VERSION = 1;
    
    /**
     * Maximum symbol version (32×32)
     */
    public static final int MAX_VERSION = 32;
    
    private final int x;
    private final int y;
    
    /**
     * Creates a symbol version with specified dimensions.
     * 
     * @param x Width version (1-32)
     * @param y Height version (1-32)
     * @throws IllegalArgumentException if x or y is outside the valid range [1, 32]
     */
    public SymbolVersion(int x, int y) {
        if (x < MIN_VERSION || x > MAX_VERSION) {
            throw new IllegalArgumentException(
                "X version must be between " + MIN_VERSION + " and " + MAX_VERSION + 
                ", got: " + x);
        }
        if (y < MIN_VERSION || y > MAX_VERSION) {
            throw new IllegalArgumentException(
                "Y version must be between " + MIN_VERSION + " and " + MAX_VERSION + 
                ", got: " + y);
        }
        this.x = x;
        this.y = y;
    }
    
    /**
     * Creates a square symbol version (x == y).
     * 
     * @param version Version number (1-32)
     * @throws IllegalArgumentException if version is outside the valid range [1, 32]
     */
    public SymbolVersion(int version) {
        this(version, version);
    }
    
    /**
     * @return Width version (1-32)
     */
    public int getX() {
        return x;
    }
    
    /**
     * @return Height version (1-32)
     */
    public int getY() {
        return y;
    }
    
    /**
     * @return True if this is a square symbol (x == y)
     */
    public boolean isSquare() {
        return x == y;
    }
    
    /**
     * Calculates the actual module width for this version.
     * 
     * @return Number of modules in width dimension (21-145)
     */
    public int getModuleWidth() {
        return 17 + 4 * x;
    }
    
    /**
     * Calculates the actual module height for this version.
     * 
     * @return Number of modules in height dimension (21-145)
     */
    public int getModuleHeight() {
        return 17 + 4 * y;
    }
    
    /**
     * @return Total module count (width × height)
     */
    public int getTotalModules() {
        return getModuleWidth() * getModuleHeight();
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof SymbolVersion)) return false;
        SymbolVersion other = (SymbolVersion) obj;
        return x == other.x && y == other.y;
    }
    
    @Override
    public int hashCode() {
        return 31 * x + y;
    }
    
    @Override
    public String toString() {
        return "SymbolVersion{" + x + "×" + y + 
               ", modules=" + getModuleWidth() + "×" + getModuleHeight() + "}";
    }
}
