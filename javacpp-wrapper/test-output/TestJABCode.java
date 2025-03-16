import com.jabcode.OptimizedJABCode;

public class TestJABCode {
    static {
        try {
            System.load(System.getProperty("user.dir") + "/target/classes/com/jabcode/linux-x86_64/libjniJABCodeNative.so");
            System.out.println("Loaded native library");
        } catch (UnsatisfiedLinkError e) {
            System.err.println("Failed to load native library: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        String outputFile = "test-output/test_jabcode.png";
        System.out.println("Output file path: " + outputFile);
        
        try {
            // Use the new OptimizedJABCode class instead of SimpleJABCode
            OptimizedJABCode.encodeToFile("Test JABCode", outputFile);
            System.out.println("JABCode generated successfully");
        } catch (Exception e) {
            System.err.println("Error generating JABCode: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
