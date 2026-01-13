# Getting Started with JABCode
**Your First Color Barcode in 5 Minutes** 🎨

Welcome to JABCode! Think of it as QR codes' colorful, data-dense cousin. Instead of boring black-and-white squares, JABCode uses up to 256 colors to pack more information into less space. Pretty neat, right?

---

## What You'll Need

- **Java 21 or newer** (JABCode uses the shiny new Foreign Function & Memory API)
- **Maven** (for building)
- **5 minutes** of your time
- A sense of adventure (optional, but recommended)

---

## The Absolute Basics

### 1. Add JABCode to Your Project

```xml
<!-- pom.xml -->
<dependency>
    <groupId>com.jabcode</groupId>
    <artifactId>jabcode-panama</artifactId>
    <version>2.0.0</version>
</dependency>
```

### 2. Your First Encoding

```java
import com.jabcode.panama.JABCodeEncoder;
import java.nio.file.Paths;

public class HelloJABCode {
    public static void main(String[] args) {
        // Create an encoder
        JABCodeEncoder encoder = new JABCodeEncoder();
        
        // Configure it (8 colors, medium error correction, visible modules)
        var config = JABCodeEncoder.Config.builder()
            .colorNumber(8)           // 8-color mode (good balance)
            .eccLevel(5)              // Medium error correction
            .moduleSize(12)           // 12 pixel modules (nice and visible)
            .build();
        
        // Encode your message
        String message = "Hello, JABCode! 👋";
        boolean success = encoder.encodeToPNG(
            message,
            "my-first-jabcode.png",
            config
        );
        
        if (success) {
            System.out.println("🎉 Success! Check out my-first-jabcode.png");
        }
    }
}
```

Run this, and boom! You've got your first JABCode. It'll be a colorful little square saved as `my-first-jabcode.png`.

### 3. Decoding Is Even Easier

```java
import com.jabcode.panama.JABCodeDecoder;
import java.nio.file.Paths;

public class DecodeMyJABCode {
    public static void main(String[] args) {
        JABCodeDecoder decoder = new JABCodeDecoder();
        
        String decoded = decoder.decodeFromFile(
            Paths.get("my-first-jabcode.png")
        );
        
        System.out.println("Decoded: " + decoded);
        // Output: Decoded: Hello, JABCode! 👋
    }
}
```

That's it! Encode, decode, done. ✨

---

## Color Modes: A Quick Guide

JABCode supports different color modes. More colors = more data capacity, but also more complexity:

| Mode | Colors | Best For | Difficulty |
|------|--------|----------|------------|
| 4-color | 🔴🟢🔵⚪ | Reliable transmission, poor lighting | ⭐ Easy |
| 8-color | 🎨 8 colors | General use, good balance | ⭐⭐ Standard |
| 16-color | 🌈 16 colors | Higher density, controlled environment | ⭐⭐ Moderate |
| 32-color | 🎨 32 colors | Professional use, quality displays | ⭐⭐⭐ Advanced |
| 64-color | 🌈 64 colors | High-density, excellent conditions | ⭐⭐⭐ Advanced |
| 128-color | 🎨 128 colors | Maximum density, perfect conditions | ⭐⭐⭐⭐ Expert |

**Pro Tip**: When in doubt, use 8-color mode. It's the Goldilocks zone—not too simple, not too complex, just right. 🐻

---

## Common Settings Explained

### Error Correction Level (ECC)

Think of this as your barcode's insurance policy:

- **Level 0-2**: Minimal protection (for pristine conditions)
- **Level 3-5**: Standard protection (recommended for most uses)
- **Level 6-7**: Maximum protection (for damaged or poor quality scans)

**Pro Tip**: Use level 5 unless you have a specific reason not to. It's battle-tested and reliable.

### Module Size

This controls how big each colored square is:

- **Small (4-8px)**: Compact barcodes, needs high-quality printing/scanning
- **Medium (10-16px)**: Good balance, works well digitally
- **Large (20+px)**: Maximum readability, great for displays

**Pro Tip**: Start with 12 pixels. It works great on screens and prints well too.

---

## Real-World Example: Business Card

Let's create a JABCode for a digital business card:

```java
String vcard = """
    BEGIN:VCARD
    VERSION:3.0
    FN:Jane Developer
    EMAIL:jane@example.com
    TEL:+1-555-0123
    URL:https://janedev.io
    END:VCARD
    """;

var config = JABCodeEncoder.Config.builder()
    .colorNumber(16)      // 16 colors for compact size
    .eccLevel(6)          // High ECC (might get dirty/damaged)
    .moduleSize(14)       // Print-friendly size
    .build();

encoder.encodeToPNG(vcard, "jane-card.png", config);
```

Print this on the back of Jane's business card, and people can scan it to instantly import her contact info. Fancy! 📇

---

## Troubleshooting Your First Code

### "It won't encode!"

**Check these first:**
1. Is your message empty? JABCode needs actual data
2. Is the message too long for the chosen color mode? Try more colors or higher version
3. Did the library load properly? Check that `libjabcode.so` is in the right place

### "The decoded message is garbled!"

**Common causes:**
1. Image quality too low (try larger module size)
2. Colors got compressed (save as PNG, not JPEG)
3. Lighting issues in photo (try again with better lighting)

### "java.lang.UnsatisfiedLinkError: Cannot open library"

The native library (`libjabcode.so`) isn't found. Set `LD_LIBRARY_PATH`:

```bash
export LD_LIBRARY_PATH=/path/to/jabcode/lib:$LD_LIBRARY_PATH
```

Or configure it in your IDE's run configuration.

---

## Next Steps

🎨 **Explore Samples**: Check out [02-sample-gallery.md](02-sample-gallery.md) to see all color modes in action

🎯 **Choose Your Mode**: Read [03-choosing-color-mode.md](03-choosing-color-mode.md) for detailed guidance

📖 **API Reference**: Dive into [08-color-mode-reference.md](08-color-mode-reference.md) for complete specifications

🐛 **Hit a Snag?**: Check [09-troubleshooting-guide.md](09-troubleshooting-guide.md) for solutions

---

## Quick Tips for Success

1. **Start simple**: 8-color mode, level 5 ECC, 12px modules
2. **Test early**: Encode and decode immediately to verify your setup
3. **Save as PNG**: JPEG compression will destroy color accuracy
4. **Good lighting**: When scanning with camera, lighting matters
5. **Print quality**: If printing, use high-quality color printer

---

**That's it!** You're now ready to create your own colorful barcodes. Have fun, and remember: if QR codes are black-tie events, JABCode is the vibrant festival. 🎉

Questions? Issues? The [troubleshooting guide](09-troubleshooting-guide.md) has your back.

Happy encoding! 🚀
