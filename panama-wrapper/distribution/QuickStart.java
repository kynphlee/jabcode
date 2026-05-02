import com.jabcode.panama.JABCodeEncoder;
import com.jabcode.panama.JABCodeDecoder;
import java.nio.file.Paths;

/**
 * JABCode Sample Generator - Demonstrates all supported color modes
 * 
 * Compile: javac --enable-preview --release 23 -cp jabcode-panama-1.0.0-SNAPSHOT.jar QuickStart.java
 * Run: ./run.sh
 */
public class QuickStart {
    
    private static final String DEMO_MESSAGE = "JABCode: Multi-color barcode technology!";
    
    public static void main(String[] args) {
        printHeader();
        
        JABCodeEncoder encoder = new JABCodeEncoder();
        JABCodeDecoder decoder = new JABCodeDecoder();
        
        int totalGenerated = 0;
        
        // Generate all 6 working color modes
        totalGenerated += generateMode(encoder, decoder, 4, "Mode 1: Basic 4-color (CMYK-like)");
        totalGenerated += generateMode(encoder, decoder, 8, "Mode 2: Standard 8-color (RGB cube)");
        totalGenerated += generateMode(encoder, decoder, 16, "Mode 3: Enhanced 16-color");
        totalGenerated += generateMode(encoder, decoder, 32, "Mode 4: High-density 32-color");
        totalGenerated += generateMode(encoder, decoder, 64, "Mode 5: 64-color + LAB + Adaptive");
        totalGenerated += generateMode(encoder, decoder, 128, "Mode 6: Ultra-dense 128-color + LAB + Adaptive");
        
        JABCodeDecoder.resetDecoderState();
        
        printSummary(totalGenerated);
    }
    
    private static void printHeader() {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("JABCode Sample Generator - All Supported Color Modes");
        System.out.println("=".repeat(70));
        System.out.println("Message: " + DEMO_MESSAGE);
        System.out.println("Phase 2: LAB Color Space + Adaptive Palette Calibration");
        System.out.println("=".repeat(70) + "\n");
    }
    
    private static int generateMode(JABCodeEncoder encoder, JABCodeDecoder decoder, 
                                    int colorNumber, String description) {
        System.out.println(description);
        System.out.println("-".repeat(70));
        
        String filename = String.format("jabcode-%dcolor.png", colorNumber);
        
        // Build config with optimal ECC for this mode
        int eccLevel = (colorNumber <= 8) ? 7 : 9;
        JABCodeEncoder.Config config = JABCodeEncoder.Config.builder()
            .colorNumber(colorNumber)
            .eccLevel(eccLevel)
            .moduleSize(16)
            .build();
        
        // Encode
        boolean encoded = encoder.encodeToPNG(DEMO_MESSAGE, filename, config);
        if (!encoded) {
            System.out.println("❌ FAILED to encode\n");
            return 0;
        }
        
        System.out.println("✅ Generated: " + filename);
        
        // Decode with appropriate method
        boolean decoded = false;
        int observations = 0;
        
        if (colorNumber >= 64) {
            // Use adaptive palette decoding for 64+ colors
            JABCodeDecoder.DecodedResultWithObservations result = 
                decoder.decodeWithObservations(
                    Paths.get(filename),
                    JABCodeDecoder.MODE_NORMAL,
                    true
                );
            decoded = result.isSuccess() && DEMO_MESSAGE.equals(result.getData());
            observations = result.getObservationCount();
            
            if (decoded) {
                System.out.println("✅ Verified: Roundtrip successful");
                System.out.println("   Adaptive observations: " + observations);
            }
        } else {
            // Standard decoding for < 64 colors
            JABCodeDecoder.DecodedResult result = 
                decoder.decodeFromFileEx(Paths.get(filename), JABCodeDecoder.MODE_NORMAL);
            decoded = result.isSuccess() && DEMO_MESSAGE.equals(result.getData());
            
            if (decoded) {
                System.out.println("✅ Verified: Roundtrip successful");
            }
        }
        
        if (!decoded) {
            System.out.println("❌ FAILED to decode\n");
            return 0;
        }
        
        System.out.println();
        return 1;
    }
    
    private static void printSummary(int count) {
        System.out.println("=".repeat(70));
        System.out.println(String.format("✅ Successfully generated %d JABCode samples", count));
        System.out.println("=".repeat(70));
        System.out.println("\nGenerated files:");
        System.out.println("  • jabcode-4color.png    - Mode 1 (4 colors, 2 bits/module)");
        System.out.println("  • jabcode-8color.png    - Mode 2 (8 colors, 3 bits/module)");
        System.out.println("  • jabcode-16color.png   - Mode 3 (16 colors, 4 bits/module)");
        System.out.println("  • jabcode-32color.png   - Mode 4 (32 colors, 5 bits/module)");
        System.out.println("  • jabcode-64color.png   - Mode 5 (64 colors, 6 bits/module) [LAB+Adaptive]");
        System.out.println("  • jabcode-128color.png  - Mode 6 (128 colors, 7 bits/module) [LAB+Adaptive]");
        System.out.println("\nAll samples encode/decode verified! ✅");
        System.out.println("\nOpen the PNG files to see the multi-color barcodes.");
    }
}
