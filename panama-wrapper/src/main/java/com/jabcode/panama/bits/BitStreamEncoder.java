package com.jabcode.panama.bits;

import java.io.ByteArrayOutputStream;

/**
 * Packs values using fixed bit widths (2..8) MSB-first.
 */
public class BitStreamEncoder {
    private int currentByte = 0;
    private int bitCount = 0; // bits currently in currentByte
    private final ByteArrayOutputStream out = new ByteArrayOutputStream();

    /**
     * Write lower bitCount bits of value (MSB-first) into the stream.
     */
    public void writeBits(int value, int bits) {
        if (bits <= 0 || bits > 32) throw new IllegalArgumentException("bits 1..32");
        for (int i = bits - 1; i >= 0; i--) {
            int bit = (value >> i) & 1;
            currentByte = (currentByte << 1) | bit;
            bitCount++;
            if (bitCount == 8) {
                out.write(currentByte & 0xFF);
                currentByte = 0;
                bitCount = 0;
            }
        }
    }

    /**
     * Pad with zeros to the next byte boundary.
     */
    public void alignToByte() {
        if (bitCount > 0) {
            currentByte <<= (8 - bitCount);
            out.write(currentByte & 0xFF);
            currentByte = 0;
            bitCount = 0;
        }
    }

    public byte[] toByteArray() {
        return out.toByteArray();
    }
}
