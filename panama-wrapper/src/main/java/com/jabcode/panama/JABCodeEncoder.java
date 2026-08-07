package com.jabcode.panama;

import com.jabcode.panama.bindings.jabcode_h;

import java.lang.foreign.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Collections;
import java.util.HashSet;

/**
 * High-level Java API for encoding JABCode barcodes using Panama FFM.
 * 
 * This class provides a clean, type-safe interface to the JABCode C library
 * without requiring any C++ wrapper code.
 * 
 * Example usage:
 * <pre>{@code
 * var encoder = new JABCodeEncoder();
 * byte[] encoded = encoder.encode("Hello World", 8, 5);
 * }</pre>
 */
public class JABCodeEncoder {
    
    /**
     * Configuration for JABCode encoding
     */
    public static class Config {
        private final int colorNumber;
        private final int eccLevel;
        private final int symbolNumber;
        private final int moduleSize;
        private final int masterSymbolWidth;
        private final int masterSymbolHeight;
        private final List<SymbolVersion> symbolVersions;
        private final List<Integer> symbolPositions;
        private final List<Integer> symbolEccLevels;

        private Config(Builder builder) {
            this.colorNumber = builder.colorNumber;
            this.eccLevel = builder.eccLevel;
            this.symbolNumber = builder.symbolNumber;
            this.moduleSize = builder.moduleSize;
            this.masterSymbolWidth = builder.masterSymbolWidth;
            this.masterSymbolHeight = builder.masterSymbolHeight;
            this.symbolVersions = builder.symbolVersions != null
                ? Collections.unmodifiableList(builder.symbolVersions)
                : null;
            this.symbolPositions = builder.symbolPositions != null
                ? Collections.unmodifiableList(builder.symbolPositions)
                : null;
            this.symbolEccLevels = builder.symbolEccLevels != null
                ? Collections.unmodifiableList(builder.symbolEccLevels)
                : null;
        }

        public int getColorNumber() { return colorNumber; }
        public int getEccLevel() { return eccLevel; }
        public int getSymbolNumber() { return symbolNumber; }
        public int getModuleSize() { return moduleSize; }
        public int getMasterSymbolWidth() { return masterSymbolWidth; }
        public int getMasterSymbolHeight() { return masterSymbolHeight; }
        public List<SymbolVersion> getSymbolVersions() { return symbolVersions; }

        /**
         * The caller-supplied lattice slots, or {@code null} for the default
         * sequential layout ({@code 0,1,2,...}).
         *
         * @return An unmodifiable list of positions, or {@code null}
         */
        public List<Integer> getSymbolPositions() { return symbolPositions; }

        /**
         * Per-symbol ECC levels, or {@code null} to apply {@link #getEccLevel()}
         * uniformly to every symbol.
         *
         * @return An unmodifiable list of levels, or {@code null}
         */
        public List<Integer> getSymbolEccLevels() { return symbolEccLevels; }

        public static Builder builder() {
            return new Builder();
        }
        
        public static Config defaults() {
            return builder().build();
        }
        
        public static class Builder {
            private int colorNumber = 8;           // 8-color mode (default)
            private int eccLevel = 5;              // ECC level 5 (medium)
            private int symbolNumber = 1;          // Single symbol
            private int moduleSize = 12;           // 12 pixel modules
            private int masterSymbolWidth = 0;     // Auto width
            private int masterSymbolHeight = 0;    // Auto height
            private List<SymbolVersion> symbolVersions; // Symbol versions for cascade
            private List<Integer> symbolPositions; // Lattice slots; null = sequential
            private List<Integer> symbolEccLevels = null;

            public Builder colorNumber(int colorNumber) {
                // Allowed JABCode color modes (Nc=0..7 → 2,4,8,16,32,64,128,256).
                // The historical Annex-G list started at 4 (omitting 2-color/Mode 0),
                // pre-dating the WS-0 (Mode 0 monochrome) library work. The C library
                // accepts color_number=2 via createEncode (jabcode swift-java-poc commit
                // 05a1acc / mode0-investigation). See WS-6.5 in
                // docs/jabcode-all-nc-plan/00-CHECKLIST.md.
                // Range is validated through the single JabCodeLimits surface.
                this.colorNumber = JabCodeLimits.validateColorNumber(colorNumber);
                return this;
            }

            public Builder eccLevel(int eccLevel) {
                this.eccLevel = JabCodeLimits.validateEccLevel(eccLevel);
                return this;
            }

            public Builder symbolNumber(int symbolNumber) {
                this.symbolNumber = JabCodeLimits.validateSymbolNumber(symbolNumber);
                return this;
            }
            
            public Builder moduleSize(int moduleSize) {
                if (moduleSize < 1) {
                    throw new IllegalArgumentException("Module size must be positive");
                }
                this.moduleSize = moduleSize;
                return this;
            }
            
            public Builder masterSymbolWidth(int width) {
                this.masterSymbolWidth = width;
                return this;
            }
            
            public Builder masterSymbolHeight(int height) {
                this.masterSymbolHeight = height;
                return this;
            }
            
            /**
             * Set explicit symbol versions for cascaded multi-symbol encoding.
             * 
             * <p>When encoding with multiple symbols (symbolNumber > 1), you can optionally
             * specify the exact version of each symbol. This is required when the encoder
             * cannot automatically determine optimal sizes.</p>
             * 
             * @param versions List of symbol versions, one per symbol
             * @return This builder instance
             * @throws IllegalArgumentException if version count doesn't match symbol count
             */
            public Builder symbolVersions(List<SymbolVersion> versions) {
                if (versions != null && !versions.isEmpty()) {
                    if (versions.size() != symbolNumber) {
                        throw new IllegalArgumentException(
                            "Symbol version count (" + versions.size() + 
                            ") must match symbol count (" + symbolNumber + ")");
                    }
                    this.symbolVersions = List.copyOf(versions);
                } else {
                    this.symbolVersions = null;
                }
                return this;
            }

            /**
             * Set explicit symbol positions — the lattice slot each symbol
             * occupies in a multi-symbol cascade.
             *
             * <p>A position is an index into the codec's 61-slot placement
             * lattice ({@code jab_symbol_pos}, {@code src/jabcode/encoder.h}),
             * so the range is {@code 0..60}. Slot {@code 0} is the master
             * symbol's; the codec insists a multi-symbol code contain it.</p>
             *
             * <p>Leaving this unset keeps the historical layout — sequential
             * slots {@code 0,1,2,...,n-1} — so existing callers are
             * unaffected.</p>
             *
             * <p>Only what the wrapper can cheaply check is checked here:
             * range and uniqueness. Whether the chosen slots actually form a
             * valid cascade — each slave docked to a host, docked sides of
             * matching size — is left to the layer above and, ultimately, to
             * the codec's own {@code assignDockedSymbols} /
             * {@code checkDockedSymbolSize}.</p>
             *
             * @param positions Lattice slots, one per symbol; {@code null} or
             *                  empty restores the sequential default
             * @return This builder instance
             * @throws IllegalArgumentException if a position is outside
             *         {@code 0..60} or appears twice
             */
            public Builder symbolPositions(List<Integer> positions) {
                if (positions != null && !positions.isEmpty()) {
                    // Count is checked in build(), not here: symbolNumber may
                    // not have been set yet on this builder.
                    var seen = new HashSet<Integer>();
                    for (Integer position : positions) {
                        if (position == null) {
                            throw new IllegalArgumentException(
                                "Symbol positions must not contain null");
                        }
                        JabCodeLimits.validateSymbolPosition(position);
                        if (!seen.add(position)) {
                            throw new IllegalArgumentException(
                                "Duplicate symbol position: " + position);
                        }
                    }
                    this.symbolPositions = List.copyOf(positions);
                } else {
                    this.symbolPositions = null;
                }
                return this;
            }

            /**
             * Set a per-symbol error correction level for a cascade.
             *
             * <p>Without this, {@link #eccLevel(int)} applies to <b>every</b>
             * symbol. With it, each symbol takes its own level and
             * <b>{@code 0} means "inherit from the host symbol"</b> — the
             * spec's {@code SE} flag, which the codec resolves per docking
             * pair.</p>
             *
             * <p>The list length must equal {@code symbolNumber}; that is
             * checked in {@link #build()} rather than here, since the two
             * setters may be called in either order.</p>
             *
             * @param eccLevels One level per symbol, index 0 = primary, or
             *                  {@code null} to apply the scalar level uniformly
             * @return This builder
             * @throws IllegalArgumentException if a level is outside {@code 0..10}
             */
            public Builder symbolEccLevels(List<Integer> eccLevels) {
                if (eccLevels != null && !eccLevels.isEmpty()) {
                    for (Integer level : eccLevels) {
                        if (level == null) {
                            throw new IllegalArgumentException(
                                "Symbol ECC levels must not contain null");
                        }
                        JabCodeLimits.validateSymbolEccLevel(level);
                    }
                    this.symbolEccLevels = List.copyOf(eccLevels);
                } else {
                    this.symbolEccLevels = null;
                }
                return this;
            }

            public Config build() {
                // Validate: if symbolNumber > 1, versions should be provided
                if (symbolNumber > 1 && symbolVersions == null) {
                    // Warning: Multi-symbol without explicit versions may fail
                    // Native encoder requires version configuration for cascades
                    System.err.println("[WARNING] Multi-symbol encoding without explicit " +
                        "symbol versions may fail. Consider using symbolVersions().");
                }
                // One position per symbol, or none at all. Deferred to build()
                // so the two setters can be called in either order.
                if (symbolPositions != null && symbolPositions.size() != symbolNumber) {
                    throw new IllegalArgumentException(
                        "Symbol position count (" + symbolPositions.size() +
                        ") must match symbol count (" + symbolNumber + ")");
                }
                // Same rule, same reason, for per-symbol ECC.
                if (symbolEccLevels != null && symbolEccLevels.size() != symbolNumber) {
                    throw new IllegalArgumentException(
                        "Symbol ECC level count (" + symbolEccLevels.size() +
                        ") must match symbol count (" + symbolNumber + ")");
                }
                return new Config(this);
            }
        }
    }
    
    /**
     * Encode data into JABCode format with default settings (8-color, ECC level 5)
     * 
     * @param data The data to encode
     * @return Encoded bitmap data as byte array, or null if encoding fails
     */
    public byte[] encode(String data) {
        return encode(data, 8, 5);
    }
    
    /**
     * Encode data into JABCode format with specified parameters
     * 
     * @param data The data to encode
     * @param colorNumber Number of colors (4,8,16,32,64,128,256)
     * @param eccLevel Error correction level (1-10, ISO/IEC 23634:2022 Table 20)
     * @return Encoded bitmap data as byte array, or null if encoding fails
     */
    public byte[] encode(String data, int colorNumber, int eccLevel) {
        var config = Config.builder()
            .colorNumber(colorNumber)
            .eccLevel(eccLevel)
            .build();
        return encodeWithConfig(data, config);
    }
    
    /**
     * Encode data with full configuration control.
     *
     * <p>UTF-8 convenience over {@link #encodeBytes(byte[], Config)}. For binary
     * payloads (ciphertext, compressed or random data), call {@code encodeBytes}
     * directly — round-tripping such data through {@code String} is lossy.</p>
     *
     * @param data The data to encode
     * @param config Encoding configuration
     * @return Encoded bitmap data as byte array, or null if encoding fails
     */
    public byte[] encodeWithConfig(String data, Config config) {
        if (data == null || data.isEmpty()) {
            throw new IllegalArgumentException("Data cannot be null or empty");
        }
        return encodeBytes(data.getBytes(StandardCharsets.UTF_8), config);
    }

    /**
     * Encode raw bytes into JABCode format — the canonical entry point.
     *
     * <p>The native codec's payload model ({@code jab_data} = length + bytes) is
     * binary-clean; this method preserves that end to end. The {@code String}
     * overloads are thin UTF-8 adapters over this one.</p>
     *
     * @param data The bytes to encode (any content, including non-UTF-8)
     * @param config Encoding configuration
     * @return PNG image bytes, or null if encoding fails
     */
    public byte[] encodeBytes(byte[] data, Config config) {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("Data cannot be null or empty");
        }

        try (Arena arena = Arena.ofConfined()) {
            // Create encoder
            MemorySegment enc = jabcode_h.createEncode(
                config.getColorNumber(),
                config.getSymbolNumber()
            );
            
            if (enc == null || enc.address() == 0) {
                return null;
            }
            
            try {
                // Configure multi-symbol cascade versions when provided
                if (config.getSymbolVersions() != null) {
                    setSymbolVersions(enc, config.getSymbolVersions());
                }

                // Lay the cascade out. Positions used to be written from inside
                // setSymbolVersions; they are their own step now so a caller can
                // choose a layout without also pinning versions. The trigger is
                // unchanged for the default path -- nothing is written unless
                // versions or positions were configured.
                if (config.getSymbolVersions() != null || config.getSymbolPositions() != null) {
                    setSymbolPositions(enc, symbolPositionCount(config), config.getSymbolPositions());
                }

                setSymbolEccLevels(enc, config);

                // Prepare jab_data structure: { int32 length; char data[]; }
                MemorySegment jabData = createJabData(arena, data);

                // Generate JABCode (0 = success per generateJABCode contract)
                int result = jabcode_h.generateJABCode(enc, jabData);
                if (result != 0) {
                    return null;
                }

                // Encode the bitmap to a PNG held entirely in memory (no temp file).
                // This keeps sensitive auth/COA payloads off the filesystem -- the PNG
                // decodes straight back to the token/signature.
                MemorySegment bitmapPtr = getBitmapFromEncoder(enc);
                if (bitmapPtr == null || bitmapPtr.address() == 0) {
                    return null;
                }

                MemorySegment outLen = arena.allocate(ValueLayout.JAVA_INT);
                MemorySegment pngPtr = jabcode_h.saveImageToMemory(bitmapPtr, outLen);
                if (pngPtr == null || pngPtr.address() == 0) {
                    return null;
                }
                try {
                    int pngLen = outLen.get(ValueLayout.JAVA_INT, 0);
                    if (pngLen <= 0) {
                        return null;
                    }
                    // Copy the native PNG bytes into a Java-owned array.
                    return pngPtr.reinterpret(pngLen).toArray(ValueLayout.JAVA_BYTE);
                } finally {
                    // saveImageToMemory malloc's the buffer; the caller owns it.
                    NativeMemory.free(pngPtr);
                }
            } finally {
                jabcode_h.destroyEncode(enc);
            }
        } catch (Exception e) {
            throw new RuntimeException("Encoding failed", e);
        }
    }
    
    /**
     * Encode data and save directly to PNG file
     * 
     * @param data The data to encode
     * @param outputPath Path to output PNG file
     * @param config Encoding configuration
     * @return true if successful, false otherwise
     */
    public boolean encodeToPNG(String data, String outputPath, Config config) {
        if (data == null || data.isEmpty()) {
            throw new IllegalArgumentException("Data cannot be null or empty");
        }
        
        try (Arena arena = Arena.ofConfined()) {
            System.err.println("[ENCODER] Config: colorNumber=" + config.getColorNumber() + 
                ", eccLevel=" + config.getEccLevel() + ", symbolNumber=" + config.getSymbolNumber());
            
            // Create encoder
            MemorySegment enc = jabcode_h.createEncode(
                config.getColorNumber(),
                config.getSymbolNumber()
            );
            
            if (enc == null || enc.address() == 0) {
                return false;
            }
            
            // Verify color_number was set correctly in struct (offset 0)
            int actualColorNumber = enc.get(ValueLayout.JAVA_INT, 0);
            System.err.println("[ENCODER] After createEncode: color_number in struct = " + actualColorNumber);
            
            try {
                // Set symbol versions if provided (for multi-symbol cascades)
                if (config.getSymbolVersions() != null) {
                    setSymbolVersions(enc, config.getSymbolVersions());
                }

                // Set symbol positions -- caller-supplied layout, else sequential
                if (config.getSymbolVersions() != null || config.getSymbolPositions() != null) {
                    setSymbolPositions(enc, symbolPositionCount(config), config.getSymbolPositions());
                }

                setSymbolEccLevels(enc, config);

                // Prepare data
                byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
                MemorySegment jabData = createJabData(arena, bytes);
                
                // Generate JABCode
                int result = jabcode_h.generateJABCode(enc, jabData);
                if (result != 0) {  // 0 = success, non-zero = error code
                    return false;
                }
                
                // Get bitmap from encoder (at offset 60 in jab_encode struct)
                MemorySegment bitmapPtr = getBitmapFromEncoder(enc);
                if (bitmapPtr == null || bitmapPtr.address() == 0) {
                    return false;
                }
                
                // Save to file
                MemorySegment filenameSegment = arena.allocateFrom(outputPath);
                byte saveResult = jabcode_h.saveImage(bitmapPtr, filenameSegment);
                
                return saveResult == 1; // JAB_SUCCESS = 1
                
            } finally {
                jabcode_h.destroyEncode(enc);
            }
        } catch (Exception e) {
            throw new RuntimeException("Encoding to PNG failed", e);
        }
    }
    
    /**
     * Create jab_data structure in native memory.
     * The C struct is: { int32 length; char data[]; }
     */
    private MemorySegment createJabData(Arena arena, byte[] data) {
        // Allocate: 4 bytes for length + data bytes
        long size = 4 + data.length;
        MemorySegment jabData = arena.allocate(size, 4); // 4-byte alignment
        
        // Set length field (first 4 bytes)
        jabData.set(ValueLayout.JAVA_INT, 0, data.length);
        
        // Copy data (flexible array member starts at offset 4)
        MemorySegment.copy(data, 0, jabData, ValueLayout.JAVA_BYTE, 4, data.length);
        
        return jabData;
    }
    
    /**
     * Get bitmap pointer from jab_encode struct.
     * The bitmap field is at offset 64 (on 64-bit systems with 8-byte alignment).
     */
    private MemorySegment getBitmapFromEncoder(MemorySegment enc) {
        // jab_encode layout (64-bit pointers with proper alignment):
        // int32 color_number (0)
        // int32 symbol_number (4)
        // int32 module_size (8)
        // int32 master_symbol_width (12)
        // int32 master_symbol_height (16)
        // [4 bytes padding for pointer alignment]
        // byte* palette (24, 8 bytes)
        // vector2d* symbol_versions (32, 8 bytes)
        // byte* symbol_ecc_levels (40, 8 bytes)
        // int32* symbol_positions (48, 8 bytes)
        // symbol* symbols (56, 8 bytes)
        // bitmap* bitmap (64, 8 bytes) <-- THIS
        
        long bitmapFieldOffset = 64;
        long bitmapAddress = enc.get(ValueLayout.ADDRESS, bitmapFieldOffset).address();
        
        if (bitmapAddress == 0) {
            return null;
        }
        
        return MemorySegment.ofAddress(bitmapAddress);
    }
    
    /**
     * Set symbol versions in the native encoder structure.
     * 
     * Symbol versions are stored as an array of vector2d structs.
     * Each vector2d is: { int32 x; int32 y; }
     * 
     * @param enc Native encoder memory segment
     * @param versions List of symbol versions to configure
     */
    private void setSymbolVersions(MemorySegment enc, List<SymbolVersion> versions) {
        // symbol_versions is at offset 32 (vector2d* pointer)
        long versionsOffset = 32;
        long versionsAddress = enc.get(ValueLayout.ADDRESS, versionsOffset).address();
        
        if (versionsAddress == 0) {
            System.err.println("[ENCODER] WARNING: symbol_versions pointer is NULL!");
            return;
        }
        
        // Each vector2d is 8 bytes (2 int32s)
        long structSize = 8;
        MemorySegment versionsArray = MemorySegment.ofAddress(versionsAddress)
            .reinterpret(structSize * versions.size());
        
        // Write each version
        for (int i = 0; i < versions.size(); i++) {
            SymbolVersion version = versions.get(i);
            long offset = i * structSize;
            
            // Write x (width version)
            versionsArray.set(ValueLayout.JAVA_INT, offset, version.getX());
            // Write y (height version)
            versionsArray.set(ValueLayout.JAVA_INT, offset + 4, version.getY());
            
            System.err.println("[ENCODER] Set symbol " + i + " version: " +
                version.getX() + "×" + version.getY());
        }
    }

    /**
     * How many slots of {@code symbol_positions} to write.
     *
     * <p>Normally {@code symbolNumber}, which {@code createEncode} sized the
     * array to. Explicit versions are preferred when present because that is
     * the length the pre-split code used, and {@code symbolVersions()} may
     * have been set against a different {@code symbolNumber}; writing the
     * shorter of the two keeps us inside the allocation either way.</p>
     *
     * @param config Encoding configuration
     * @return Number of positions to write
     */
    private int symbolPositionCount(Config config) {
        List<SymbolVersion> versions = config.getSymbolVersions();
        return versions != null
            ? Math.min(versions.size(), config.getSymbolNumber())
            : config.getSymbolNumber();
    }

    /**
     * Set symbol positions in the native encoder structure.
     *
     * <p>{@code symbol_positions} is an int32 array at offset 48, one lattice
     * slot per symbol. The array must be written for any multi-symbol code —
     * an unwritten array leaves every symbol claiming slot 0, which the codec
     * rejects with "Duplicate symbol position".</p>
     *
     * <p>When {@code positions} is {@code null} the historical sequential
     * layout is emitted ({@code 0,1,2,...,count-1}), keeping every existing
     * caller byte-identical. Supplied positions have already been range- and
     * duplicate-checked by {@link Config.Builder#symbolPositions(List)}; the
     * codec still owns adjacency and docked-size validation.</p>
     *
     * @param enc       Native encoder memory segment
     * @param count     Number of symbols to write positions for
     * @param positions Caller-supplied lattice slots, or {@code null} for the
     *                  sequential default
     */
    /**
     * Write the error correction level for EVERY symbol.
     *
     * <p>{@code symbol_ecc_levels} is a byte array at offset 40, one entry per
     * symbol. Both call sites previously wrote index 0 only, leaving every
     * slave unset — and {@code wcwr_for_level()} normalises an unset level to
     * {@code DEFAULT_ECC_LEVEL} (3). So a cascade requested at ECC 10 protected
     * the primary at 10 and all its slaves at 3, silently. Measured against the
     * native encoder the divergence reaches 1298 bytes of capacity, in both
     * directions, because a level below 3 was also being raised.</p>
     *
     * <p>Slaves are NOT left at 0 to inherit: inheritance resolves per docking
     * pair, so a chain would drift from the requested level the further it got
     * from the primary. Writing the level explicitly makes "ECC 10" mean ECC 10
     * everywhere. A caller wanting per-symbol control — including the spec's
     * {@code 0 = inherit} — supplies {@link Config.Builder#symbolEccLevels}.</p>
     *
     * @param enc    Native encoder memory segment
     * @param config Encoding configuration
     */
    private void setSymbolEccLevels(MemorySegment enc, Config config) {
        long eccLevelsAddress = enc.get(ValueLayout.ADDRESS, 40).address();
        if (eccLevelsAddress == 0) {
            System.err.println("[ENCODER] WARNING: symbol_ecc_levels pointer is NULL!");
            return;
        }

        int count = config.getSymbolNumber();
        List<Integer> perSymbol = config.getSymbolEccLevels();
        MemorySegment eccLevels = MemorySegment.ofAddress(eccLevelsAddress).reinterpret(count);

        for (int i = 0; i < count; i++) {
            int level = perSymbol != null ? perSymbol.get(i) : config.getEccLevel();
            eccLevels.set(ValueLayout.JAVA_BYTE, i, (byte) level);
        }
    }

    private void setSymbolPositions(MemorySegment enc, int count, List<Integer> positions) {
        // symbol_positions is at offset 48 (int32* pointer)
        long positionsOffset = 48;
        long positionsAddress = enc.get(ValueLayout.ADDRESS, positionsOffset).address();

        if (positionsAddress == 0) {
            System.err.println("[ENCODER] WARNING: symbol_positions pointer is NULL!");
            return;
        }

        // symbol_positions is int32 array
        MemorySegment positionsArray = MemorySegment.ofAddress(positionsAddress)
            .reinterpret(4L * count);

        for (int i = 0; i < count; i++) {
            int position = positions != null ? positions.get(i) : i;
            positionsArray.set(ValueLayout.JAVA_INT, i * 4L, position);
            System.err.println("[ENCODER] Set symbol " + i + " position: " + position);
        }
    }
}
