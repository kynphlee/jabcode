package com.jabcode.panama.colors.interp;

/**
 * Interpolation abstraction (used for modes 6 and 7).
 */
public interface ColorInterpolator {
    /**
     * Linearly interpolate between a and b by t in [0,1].
     */
    default int lerp(int a, int b, double t) {
        return (int)Math.round(a + (b - a) * t);
    }
}
