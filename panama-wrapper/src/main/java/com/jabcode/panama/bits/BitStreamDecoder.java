package com.jabcode.panama.bits;

/**
 * Reads fixed-width values (2..8 bits) MSB-first from a byte array.
 */
public class BitStreamDecoder {
    private final byte[] data;
    private int bitPos = 0; // 0..(len*8)

    public BitStreamDecoder(byte[] data) {
        this.data = data != null ? data : new byte[0];
    }

    public boolean hasMore() {
        return bitPos < data.length * 8;
    }

    public int readBits(int bits) {
        if (bits <= 0 || bits > 32) throw new IllegalArgumentException("bits 1..32");
        int value = 0;
        for (int i = 0; i < bits; i++) {
            if (!hasMore()) {
                value <<= 1; // pad with zero
                continue;
            }
            int byteIndex = bitPos / 8;
            int bitIndex = 7 - (bitPos % 8);
            int bit = (data[byteIndex] >> bitIndex) & 1;
            value = (value << 1) | bit;
            bitPos++;
        }
        return value;
    }
}
