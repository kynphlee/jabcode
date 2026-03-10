# JABCode Mobile - iOS/macOS Swift Package

Cross-platform JABCode encoder/decoder for iOS and macOS, providing identical API to the Android version.

## Features

- ✅ **6/7 color modes working** (4, 8, 16, 32, 64, 128 colors)
- ✅ **Encode**: Data → JABCode bitmap (RGBA)
- ✅ **Decode**: Roundtrip decode (synthetic) or camera decode (full detection)
- ✅ **UIImage/NSImage extensions** for easy image conversion
- ✅ **Thread-safe** error handling
- ✅ **Zero external dependencies** (pure C + Swift)

## Installation

### Swift Package Manager

Add to your `Package.swift`:

```swift
dependencies: [
    .package(url: "path/to/swift-java-wrapper/ios", from: "1.0.0")
]
```

Or in Xcode: File → Add Package Dependencies → Enter local path

## Usage

### Basic Encoding & Decoding

```swift
import JABCodeMobile

// Encode data to JABCode
let data = "Hello, World!".data(using: .utf8)!
let params = JABCodeMobile.EncodeParams(
    colorNumber: 4,      // 4, 8, 16, 32, 64, 128
    symbolNumber: 1,
    eccLevel: 3,
    moduleSize: 12
)

let encodeResult = try JABCodeMobile.encode(data: data, params: params)
print("Encoded: \(encodeResult.width)x\(encodeResult.height)")

// Decode (roundtrip - optimal)
let decoded = try JABCodeMobile.decode(
    encodeResult: encodeResult,
    colorNumber: params.colorNumber,
    eccLevel: params.eccLevel
)
print("Decoded: \(String(data: decoded, encoding: .utf8)!)")
```

### iOS UIImage Integration

```swift
import UIKit

// Convert to UIImage for display
let image = encodeResult.toUIImage()
imageView.image = image

// Decode from camera image
if let rgbaData = cameraImage.rgbaData() {
    let decoded = try JABCodeMobile.decodeFromBitmap(
        rgbaData: rgbaData,
        width: Int32(cameraImage.size.width),
        height: Int32(cameraImage.size.height)
    )
}
```

### macOS NSImage Integration

```swift
import AppKit

// Convert to NSImage
let image = encodeResult.toNSImage()

// Decode from scanned image
if let rgbaData = scannedImage.rgbaData() {
    let decoded = try JABCodeMobile.decodeFromBitmap(
        rgbaData: rgbaData,
        width: Int32(scannedImage.size.width),
        height: Int32(scannedImage.size.height)
    )
}
```

### Error Handling

```swift
do {
    let result = try JABCodeMobile.encode(data: data, params: params)
} catch JABCodeError.encodeFailed(let message) {
    print("Encoding failed: \(message)")
} catch {
    print("Unexpected error: \(error)")
}
```

## API Reference

### JABCodeMobile

**Static Methods:**
- `encode(data:params:) throws -> EncodeResult` - Encode data to JABCode
- `decode(encodeResult:colorNumber:eccLevel:) throws -> Data` - Decode (roundtrip)
- `decodeFromBitmap(rgbaData:width:height:) throws -> Data` - Decode from camera
- `loadCalibration(json:) -> Bool` - Load color calibration profile
- `clearCalibration()` - Clear calibration
- `var version: String` - Library version
- `var hasCalibration: Bool` - Check if calibration active

### EncodeParams

```swift
struct EncodeParams {
    let colorNumber: Int32      // 4, 8, 16, 32, 64, 128 (NOT 256)
    let symbolNumber: Int32     // Default: 1, Max: 4
    let eccLevel: Int32         // 0-7 (default: 3)
    let moduleSize: Int32       // Pixels per module (default: 12)
}
```

### EncodeResult

```swift
class EncodeResult {
    let width: Int              // Bitmap width in pixels
    let height: Int             // Bitmap height in pixels
    let rgbaData: Data          // RGBA pixel data (width × height × 4)
    
    func toUIImage() -> UIImage?    // iOS only
    func toNSImage() -> NSImage?    // macOS only
}
```

## Testing

Run tests with:

```bash
cd ios
swift test
```

## Platform Support

- **iOS**: 13.0+
- **macOS**: 10.15+
- **Architecture**: arm64, x86_64

## Performance

Same C core as Android version:
- Encode: ~50-100ms (depends on data size and color mode)
- Decode (roundtrip): ~30-60ms
- Decode (camera): ~50-150ms (full detection pipeline)

## Known Limitations

- 256-color mode is NOT supported (encoder malloc corruption)
- Use color modes 4-128 only

## Architecture

```
Swift Wrapper (JABCodeMobile.swift)
       ↓
C Mobile Bridge (mobile_bridge.h)
       ↓
JABCode Core (encoder.c, decoder.c)
```

## Cross-Platform Compatibility

This iOS package provides **identical API** to the Android version:

| Feature | Android (Java) | iOS (Swift) |
|---------|---------------|-------------|
| Encode | `JABCodeMobile.encode()` | `JABCodeMobile.encode()` |
| Decode | `JABCodeMobile.decode()` | `JABCodeMobile.decode()` |
| Camera | `JABCodeMobile.decodeFromBitmap()` | `JABCodeMobile.decodeFromBitmap()` |
| Image conversion | `EncodeResult.toBitmap()` | `EncodeResult.toUIImage()` |

## License

Same as JABCode (see LICENSE)
