import com.jabcode.panama.bindings.jabcode_h;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;

class TestHigherColors {
    public static void main(String[] args) {
        try (Arena arena = Arena.ofConfined()) {
            // Test 16 colors
            for (int colors : new int[]{4, 8, 16}) {
                System.out.println("\n=== Testing " + colors + " colors ===");
                
                MemorySegment enc = jabcode_h.createEncode(colors, 1);
                if (enc == null || enc.address() == 0) {
                    System.out.println("createEncode failed!");
                    continue;
                }
                
                try {
                    // Set module size
                    enc.set(ValueLayout.JAVA_INT, 8, 20);
                    
                    // Create data
                    String message = "Test " + colors;
                    byte[] bytes = message.getBytes(StandardCharsets.UTF_8);
                    long size = 4 + bytes.length;
                    MemorySegment jabData = arena.allocate(size, 4);
                    jabData.set(ValueLayout.JAVA_INT, 0, bytes.length);
                    MemorySegment.copy(bytes, 0, jabData, ValueLayout.JAVA_BYTE, 4, bytes.length);
                    
                    // Generate
                    int result = jabcode_h.generateJABCode(enc, jabData);
                    System.out.println("generateJABCode result: " + result);
                    
                    if (result == 0) {
                        // Get bitmap
                        MemorySegment bitmapPtr = enc.get(ValueLayout.ADDRESS, 64);
                        System.out.println("Bitmap address: " + (bitmapPtr != null ? bitmapPtr.address() : "null"));
                        
                        if (bitmapPtr != null && bitmapPtr.address() != 0) {
                            // Save
                            String filename = "test_" + colors + "colors.png";
                            MemorySegment filenameSegment = arena.allocateFrom(filename);
                            byte saveResult = jabcode_h.saveImage(bitmapPtr, filenameSegment);
                            System.out.println("saveImage result: " + saveResult);
                            System.out.println("Saved to: " + filename);
                        }
                    }
                } finally {
                    jabcode_h.destroyEncode(enc);
                }
            }
        }
    }
}
