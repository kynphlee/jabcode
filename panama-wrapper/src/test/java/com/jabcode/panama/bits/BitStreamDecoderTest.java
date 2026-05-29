package com.jabcode.panama.bits;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BitStreamDecoderTest {

    @Test
    void readSingleBits() {
        byte[] data = {(byte)0xB2}; // 10110010
        var dec = new BitStreamDecoder(data);
        
        assertEquals(1, dec.readBits(1)); // 1
        assertEquals(0, dec.readBits(1)); // 0
        assertEquals(1, dec.readBits(1)); // 1
        assertEquals(1, dec.readBits(1)); // 1
        assertEquals(0, dec.readBits(1)); // 0
        assertEquals(0, dec.readBits(1)); // 0
        assertEquals(1, dec.readBits(1)); // 1
        assertEquals(0, dec.readBits(1)); // 0
        assertFalse(dec.hasMore());
    }

    @Test
    void readMultiBitValues() {
        byte[] data = {(byte)0xB8}; // 10111000
        var dec = new BitStreamDecoder(data);
        
        assertEquals(0b101, dec.readBits(3));   // 101
        assertEquals(0b11, dec.readBits(2));    // 11
        assertEquals(0b000, dec.readBits(3));   // 000
        assertFalse(dec.hasMore());
    }

    @Test
    void spanMultipleBytes() {
        byte[] data = {(byte)0xFF, (byte)0x00}; // 11111111 00000000
        var dec = new BitStreamDecoder(data);
        
        assertEquals(0xFF, dec.readBits(8));
        assertEquals(0x00, dec.readBits(8));
        assertFalse(dec.hasMore());
    }

    @Test
    void readBeyondEndPadsZeros() {
        byte[] data = {(byte)0xA0}; // 10100000
        var dec = new BitStreamDecoder(data);
        
        assertEquals(0b101, dec.readBits(3));
        assertEquals(0, dec.readBits(10)); // reads remaining 5 bits + pads 5 zeros
    }

    @Test
    void roundTripEncodeDecodeMatches() {
        var enc = new BitStreamEncoder();
        enc.writeBits(0b1010, 4);
        enc.writeBits(0b110, 3);
        enc.writeBits(0b1, 1);
        enc.alignToByte();
        
        byte[] encoded = enc.toByteArray();
        var dec = new BitStreamDecoder(encoded);
        
        assertEquals(0b1010, dec.readBits(4));
        assertEquals(0b110, dec.readBits(3));
        assertEquals(0b1, dec.readBits(1));
    }
}
