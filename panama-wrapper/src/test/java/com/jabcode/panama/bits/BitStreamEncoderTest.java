package com.jabcode.panama.bits;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BitStreamEncoderTest {

    @Test
    void writeSingleBits() {
        var enc = new BitStreamEncoder();
        enc.writeBits(1, 1); // 1
        enc.writeBits(0, 1); // 10
        enc.writeBits(1, 1); // 101
        enc.writeBits(1, 1); // 1011
        enc.writeBits(0, 1); // 10110
        enc.writeBits(0, 1); // 101100
        enc.writeBits(1, 1); // 1011001
        enc.writeBits(0, 1); // 10110010
        enc.alignToByte();
        
        byte[] result = enc.toByteArray();
        assertEquals(1, result.length);
        assertEquals((byte)0xB2, result[0]); // 10110010 = 0xB2
    }

    @Test
    void writeMultiBitValues() {
        var enc = new BitStreamEncoder();
        enc.writeBits(0b101, 3);     // 101
        enc.writeBits(0b11, 2);      // 10111
        enc.writeBits(0b000, 3);     // 10111000
        enc.alignToByte();
        
        byte[] result = enc.toByteArray();
        assertEquals(1, result.length);
        assertEquals((byte)0xB8, result[0]); // 10111000 = 0xB8
    }

    @Test
    void spanMultipleBytes() {
        var enc = new BitStreamEncoder();
        enc.writeBits(0xFF, 8);      // 11111111
        enc.writeBits(0x00, 8);      // 11111111 00000000
        enc.alignToByte();
        
        byte[] result = enc.toByteArray();
        assertEquals(2, result.length);
        assertEquals((byte)0xFF, result[0]);
        assertEquals((byte)0x00, result[1]);
    }

    @Test
    void alignPadsWithZeros() {
        var enc = new BitStreamEncoder();
        enc.writeBits(0b101, 3);     // 101
        enc.alignToByte();           // 10100000
        
        byte[] result = enc.toByteArray();
        assertEquals(1, result.length);
        assertEquals((byte)0xA0, result[0]); // 10100000 = 0xA0
    }
}
