# JABCode Panama Wrapper

**Version:** 1.0.0-SNAPSHOT  
**Requires:** JDK 21+ with FFM preview support

---

## Installation

### Maven

```bash
mvn install:install-file \
  -Dfile=jabcode-panama-1.0.0-SNAPSHOT.jar \
  -DgroupId=com.jabcode \
  -DartifactId=jabcode-panama \
  -Dversion=1.0.0-SNAPSHOT \
  -Dpackaging=jar
```

Add to `pom.xml`:
```xml
<dependency>
    <groupId>com.jabcode</groupId>
    <artifactId>jabcode-panama</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### Gradle

```gradle
dependencies {
    implementation files('path/to/jabcode-panama-1.0.0-SNAPSHOT.jar')
}
```

### Native Library

Copy `lib/libjabcode.so` to:
- System library: `sudo cp lib/libjabcode.so /usr/local/lib/ && sudo ldconfig`
- Or set path: `export LD_LIBRARY_PATH=/path/to/lib:$LD_LIBRARY_PATH`

---

## Usage

```java
import com.jabcode.panama.JABCodeEncoder;
import com.jabcode.panama.JABCodeDecoder;
import java.nio.file.Paths;

// Encode
JABCodeEncoder encoder = new JABCodeEncoder();
JABCodeEncoder.Config config = JABCodeEncoder.Config.builder()
    .colorNumber(64)
    .eccLevel(9)
    .moduleSize(16)
    .build();

encoder.encodeToPNG("Hello World", "output.png", config);

// Decode
JABCodeDecoder decoder = new JABCodeDecoder();
JABCodeDecoder.DecodedResult result = 
    decoder.decodeFromFileEx(Paths.get("output.png"), JABCodeDecoder.MODE_NORMAL);

if (result.isSuccess()) {
    System.out.println(result.getData());
}

JABCodeDecoder.resetDecoderState();
```

---

## Run Requirements

```bash
java --enable-preview \
     --enable-native-access=ALL-UNNAMED \
     -Djava.library.path=/path/to/lib \
     YourApp
```

---

## Supported Color Modes

| Mode | Colors | Status |
|------|--------|--------|
| 1 | 4 | ✅ |
| 2 | 8 | ✅ |
| 3 | 16 | ✅ |
| 4 | 32 | ✅ |
| 5 | 64 | ✅ + LAB + Adaptive |
| 6 | 128 | ✅ + LAB + Adaptive |
| 7 | 256 | ❌ Known issue |

**Pass Rate:** 81% overall, 100% for modes 1-6
