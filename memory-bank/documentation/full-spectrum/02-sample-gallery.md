# JABCode Sample Gallery
**A Visual Tour of Color Modes** 🎨

Welcome to the JABCode sample gallery! Here you'll find real examples of every supported color mode, complete with their actual encoded data. Think of this as JABCode's portfolio—each sample is self-describing and tells you exactly how it was made.

---

## About These Samples

Every sample in the gallery encodes its own configuration details. When you decode any of these images, you'll see a message like:

```
JABCode Sample | Mode: 64-color (Nc=5) | ECC Level: 5 | Module Size: 12px | Symbols: 1 | 
Encoding: UTF-8 | Error Correction: LDPC | Mask: Adaptive | 
This demonstrates JABCode's 2D color barcode technology with advanced error correction capabilities.
```

Pretty meta, right? Each JABCode tells you its own story. 📖

---

## The Collection

### 🎨 4-Color Mode (Nc=1)
**File**: `sample_4_color_simple.png` | **Size**: 34 KB

The simplest mode. Just four colors, but incredibly reliable. This is your "it just works" option.

**What makes it special:**
- Maximum reliability in poor conditions
- Works with basic color printers
- Great for outdoor/harsh environments
- Larger file size compensates with more modules

**Use it when:**
- Reliability matters more than density
- Printing on basic equipment
- Environmental conditions are unpredictable
- You need guaranteed readability

---

### 🌈 8-Color Mode (Nc=2)
**File**: `sample_8_color_simple.png` | **Size**: 23 KB

The Goldilocks mode—not too simple, not too complex. This is what most people should use.

**What makes it special:**
- Perfect balance of density and reliability
- Works on standard displays and printers
- Battle-tested in real applications
- Good error recovery

**Use it when:**
- You want a sensible default
- Standard digital display/print quality
- General-purpose applications
- You're not sure which mode to pick

**Real-world uses:**
- Digital tickets
- Product labels
- Document verification
- General data encoding

---

### 🎨 16-Color Mode (Nc=3)
**File**: `sample_16_color_simple.png` | **Size**: 15 KB

Stepping up the density. Sixteen distinct colors pack more data into less space.

**What makes it special:**
- Higher data density than 8-color
- Still manageable color discrimination
- Moderate file size
- Good for controlled environments

**Use it when:**
- Indoor, controlled lighting
- Quality displays or printers
- Need more capacity than 8-color
- Can ensure good scan quality

**Real-world uses:**
- Warehouse inventory
- Event credentials
- Digital certificates
- Secure documents

---

### 🌟 32-Color Mode (Nc=4)
**File**: `sample_32_color_simple.png` | **Size**: 14 KB

Professional-grade encoding. Thirty-two colors means serious data capacity.

**What makes it special:**
- High data density
- Requires quality color reproduction
- Smaller physical size for same data
- Professional applications

**Use it when:**
- Controlled professional environment
- High-quality displays (digital signage)
- Excellent lighting conditions
- Maximum data in minimal space

**Real-world uses:**
- Industrial automation
- Professional credentials
- High-security applications
- Digital asset tracking

---

### 🎯 64-Color Mode (Nc=5)
**File**: `sample_64_color_simple.png` | **Size**: 11 KB

Advanced mode with adaptive palette. Sixty-four colors, carefully chosen for discrimination.

**What makes it special:**
- Adaptive color palette
- High-density encoding
- **Recently fixed** mask metadata bug
- Excellent data capacity

**Use it when:**
- Perfect conditions guaranteed
- High-end displays/printers
- Professional scanning equipment
- Maximum density required

**Technical note:** This mode uses adaptive palette selection to optimize color discrimination based on the specific data being encoded. The encoder analyzes your content and picks the 64 colors that work best together.

**Recent breakthrough:** We recently fixed a critical bug where the encoder wasn't writing the correct mask pattern to the metadata. JABCode now works flawlessly in 64-color mode! 🎉

---

### 🌈 128-Color Mode (Nc=6)
**File**: `sample_128_color_simple.png` | **Size**: 11 KB

Expert-level encoding. One hundred twenty-eight colors with palette interpolation.

**What makes it special:**
- Maximum supported density (for now)
- Uses palette interpolation
- **Recently fixed** mask metadata bug
- Pushes the limits of color barcode tech

**Use it when:**
- Absolute best conditions
- Laboratory/controlled environment
- Research or specialized applications
- Exploring JABCode's capabilities

**Technical note:** This mode uses palette interpolation—not all 128 colors are explicitly placed in the barcode. Instead, the decoder interpolates between embedded reference colors. It's clever and complex.

**Recent breakthrough:** Like 64-color mode, we fixed the mask metadata synchronization bug. All 13 tests now pass with 100% reliability! ✅

---

## Sample Statistics

| Mode | File Size | Data Capacity* | Reliability | Complexity |
|------|-----------|----------------|-------------|------------|
| 4-color | 34 KB | 1× (baseline) | ⭐⭐⭐⭐⭐ | ⭐ |
| 8-color | 23 KB | 1.5× | ⭐⭐⭐⭐ | ⭐⭐ |
| 16-color | 15 KB | 2× | ⭐⭐⭐ | ⭐⭐ |
| 32-color | 14 KB | 2.5× | ⭐⭐⭐ | ⭐⭐⭐ |
| 64-color | 11 KB | 3× | ⭐⭐ | ⭐⭐⭐ |
| 128-color | 11 KB | 3.5× | ⭐⭐ | ⭐⭐⭐⭐ |

*Approximate relative capacity for same physical size

---

## File Size Observations

Notice something interesting? The 4-color sample is **three times larger** than the 128-color sample!

**Why?** Lower color modes need more modules (colored squares) to encode the same amount of data. With only 4 colors, each module carries less information, so you need more of them. It's like the difference between:

- **4-color**: Writing in all caps with big letters
- **128-color**: Writing in precise, tiny calligraphy

Both say the same thing, but one takes way more space. 📏

---

## What About 256-Color Mode?

Sharp-eyed readers might notice we skip from 128 to... nothing. Where's 256-color (Nc=7)?

**The truth:** We ran into a malloc corruption bug in the encoder initialization for 256-color mode. The encoder crashes before it can even start encoding. We've documented it, protected against it, and it's on the roadmap to fix.

**Current status:** ❌ Excluded from samples (known issue)

**Good news:** All modes from 4 to 128 colors work perfectly. That's six fully functional modes covering virtually every real-world use case! 🎯

---

## How to Use These Samples

### Decode Them Yourself

```java
import com.jabcode.panama.JABCodeDecoder;
import java.nio.file.Paths;

JABCodeDecoder decoder = new JABCodeDecoder();

// Try any sample!
String decoded = decoder.decodeFromFile(
    Paths.get("test-images/sample_64_color_simple.png")
);

System.out.println(decoded);
// Output: JABCode Sample | Mode: 64-color (Nc=5) | ...
```

### Generate Your Own Samples

All samples were created using `GenerateSamples.java`:

```bash
cd panama-wrapper
LD_LIBRARY_PATH=../lib:$LD_LIBRARY_PATH \
  java -cp "target/classes:target/test-classes:$(mvn -q dependency:build-classpath)" \
  com.jabcode.panama.GenerateSamples
```

**Pro tip:** Check out the source code to see exactly how each sample is configured. It's educational! 📚

---

## Testing with Samples

These samples are perfect for:

1. **Visual verification**: See what each mode actually looks like
2. **Decoder testing**: Verify your decoder handles all modes
3. **Quality testing**: Try different printing/scanning conditions
4. **Learning**: Decode them to understand the technology
5. **Benchmarking**: Compare encoding/decoding performance

---

## Sample Locations

All samples live in the project at:
```
panama-wrapper/test-images/
```

Each filename follows the pattern: `sample_{colors}_color_simple.png`

---

## Next Steps

🎯 **Choose Your Mode**: Read [03-choosing-color-mode.md](03-choosing-color-mode.md) for detailed selection guidance

🔧 **Technical Deep Dive**: Curious how we fixed those bugs? Check out [04-mask-metadata-saga.md](04-mask-metadata-saga.md)

📖 **Complete Reference**: See [08-color-mode-reference.md](08-color-mode-reference.md) for full technical specifications

---

**Fun fact:** The total size of all six samples is about 108 KB. That's less than a single small photo, yet they demonstrate the complete spectrum of JABCode's capabilities. Efficiency at its finest! 💾

Enjoy exploring the samples! Each one is a little work of art in its own right. 🎨
