# iOS Integration Guide for JABCode

**Deep-dive implementation guide for iOS applications**

[← Back to Index](index.md) | [Overview](overview.md)

---

## 📋 Table of Contents

1. [Environment Setup](#environment-setup)
2. [Project Structure](#project-structure)
3. [Building Native Library](#building-native-library)
4. [Swift C Interop](#swift-c-interop)
5. [Swift API Design](#swift-api-design)
6. [Camera Integration](#camera-integration)
7. [Memory Management](#memory-management)
8. [Concurrency Model](#concurrency-model)
9. [Error Handling](#error-handling)
10. [Testing Strategy](#testing-strategy)
11. [App Store Preparation](#app-store-preparation)

---

## 🔧 Environment Setup

### Prerequisites

```bash
# Required tools
- Xcode 15.0+ (includes Swift 5.9+)
- iOS 13.0+ deployment target
- Command Line Tools installed
- CocoaPods 1.12+ or Swift Package Manager

# Verify installation
xcodebuild -version
swift --version
pod --version  # If using CocoaPods
```

### Project Configuration

**Package.swift (Swift Package Manager):**

```swift
// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "JABCode",
    platforms: [
        .iOS(.v13),
        .macOS(.v11)
    ],
    products: [
        .library(
            name: "JABCode",
            targets: ["JABCode"]
        ),
    ],
    targets: [
        // Swift wrapper
        .target(
            name: "JABCode",
            dependencies: ["CJABCode"],
            path: "Sources/JABCode",
            swiftSettings: [
                .enableUpcomingFeature("StrictConcurrency")
            ]
        ),
        
        // C library
        .target(
            name: "CJABCode",
            dependencies: [],
            path: "Sources/CJABCode",
            exclude: [
                "tests/",
                "docs/",
                "examples/"
            ],
            sources: [
                "encoder.c",
                "decoder.c",
                "ldpc.c",
                "detector.c",
                "binarizer.c",
                "image.c",
                "mask.c",
                "pseudo_random.c",
                "sample.c",
                "transform.c"
            ],
            publicHeadersPath: "include",
            cSettings: [
                .headerSearchPath("include"),
                .define("JABCODE_VERSION", to: "\"2.0\""),
                .unsafeFlags(["-O3"], .when(configuration: .release))
            ],
            linkerSettings: [
                .linkedLibrary("png"),
                .linkedFramework("Accelerate")  // For SIMD optimizations
            ]
        ),
        
        // Unit tests
        .testTarget(
            name: "JABCodeTests",
            dependencies: ["JABCode"],
            path: "Tests/JABCodeTests"
        )
    ],
    cLanguageStandard: .c99
)
```

**Podspec (CocoaPods Alternative):**

```ruby
Pod::Spec.new do |s|
  s.name             = 'JABCode'
  s.version          = '2.0.0'
  s.summary          = 'JABCode color barcode encoder/decoder for iOS'
  
  s.description      = <<-DESC
    JABCode (Just Another Bar Code) is a high-capacity 2D color barcode
    system specified in ISO/IEC 23634:2022. This library provides
    encoding and decoding capabilities for iOS applications.
  DESC
  
  s.homepage         = 'https://github.com/jabcode/jabcode'
  s.license          = { :type => 'LGPL-2.1', :file => 'LICENSE' }
  s.author           = { 'Fraunhofer SIT' => 'jabcode@sit.fraunhofer.de' }
  s.source           = { :git => 'https://github.com/jabcode/jabcode.git', :tag => s.version.to_s }
  
  s.ios.deployment_target = '13.0'
  s.swift_version = '5.9'
  
  s.source_files = [
    'Sources/JABCode/**/*.swift',
    'Sources/CJABCode/**/*.{c,h}'
  ]
  
  s.public_header_files = 'Sources/CJABCode/include/*.h'
  s.preserve_paths = 'Sources/CJABCode/**/*'
  
  s.libraries = 'png', 'z'
  s.frameworks = 'UIKit', 'Accelerate', 'AVFoundation'
  
  s.pod_target_xcconfig = {
    'SWIFT_INCLUDE_PATHS' => '$(PODS_TARGET_SRCROOT)/Sources/CJABCode/include',
    'HEADER_SEARCH_PATHS' => '$(PODS_TARGET_SRCROOT)/Sources/CJABCode/include',
    'GCC_C_LANGUAGE_STANDARD' => 'c99'
  }
end
```

---

## 📁 Project Structure

```
JABCode-iOS/
├── Package.swift                         # SPM manifest
├── JABCode.xcodeproj                     # Xcode project
│
├── Sources/
│   ├── JABCode/                          # Swift wrapper
│   │   ├── JABCode.swift                 # Main public API
│   │   ├── Encoder.swift                 # Encoding interface
│   │   ├── Decoder.swift                 # Decoding interface
│   │   ├── Models/
│   │   │   ├── EncodeOptions.swift
│   │   │   ├── DecodeResult.swift
│   │   │   └── JABCodeError.swift
│   │   ├── Extensions/
│   │   │   ├── UIImage+JABCode.swift
│   │   │   ├── CGImage+Conversion.swift
│   │   │   └── Data+Utilities.swift
│   │   ├── Camera/
│   │   │   ├── CameraManager.swift
│   │   │   └── ScanViewController.swift
│   │   └── Internal/
│   │       ├── MemoryManagement.swift
│   │       └── UnsafeHelpers.swift
│   │
│   └── CJABCode/                         # C library
│       ├── include/                      # Public C headers
│       │   ├── jabcode.h
│       │   ├── encoder.h
│       │   └── decoder.h
│       ├── encoder.c
│       ├── decoder.c
│       ├── ldpc.c
│       ├── detector.c
│       ├── binarizer.c
│       ├── image.c
│       └── ...
│
├── Tests/
│   ├── JABCodeTests/                     # Unit tests
│   │   ├── EncoderTests.swift
│   │   ├── DecoderTests.swift
│   │   └── PerformanceTests.swift
│   └── Resources/                        # Test fixtures
│       ├── sample_codes/
│       └── test_images/
│
├── Examples/
│   ├── BasicEncoding/                    # Simple example app
│   ├── RealtimeScanner/                  # Camera scanning example
│   └── MultiSymbol/                      # Advanced multi-symbol example
│
└── Documentation/
    └── JABCode.docc/                     # DocC documentation
        ├── JABCode.md
        └── Tutorials/
```

---

## 🔨 Building Native Library

### Build Script (build_native.sh)

```bash
#!/bin/bash
# Build JABCode native library for iOS

set -e

# Configuration
PROJECT_ROOT=$(pwd)
C_SOURCE_DIR="$PROJECT_ROOT/Sources/CJABCode"
BUILD_DIR="$PROJECT_ROOT/build"

# Build for device architectures
ARCHS="arm64"
DEPLOYMENT_TARGET="13.0"

echo "Building JABCode native library..."

# Create build directory
mkdir -p "$BUILD_DIR"

# Build for each architecture
for ARCH in $ARCHS; do
    echo "Building for $ARCH..."
    
    ARCH_BUILD_DIR="$BUILD_DIR/$ARCH"
    mkdir -p "$ARCH_BUILD_DIR"
    
    # Configure with CMake
    cmake -S "$C_SOURCE_DIR" -B "$ARCH_BUILD_DIR" \
        -DCMAKE_SYSTEM_NAME=iOS \
        -DCMAKE_OSX_ARCHITECTURES=$ARCH \
        -DCMAKE_OSX_DEPLOYMENT_TARGET=$DEPLOYMENT_TARGET \
        -DCMAKE_BUILD_TYPE=Release \
        -DCMAKE_C_FLAGS="-O3 -flto" \
        -DBUILD_SHARED_LIBS=OFF
    
    # Build
    cmake --build "$ARCH_BUILD_DIR" --config Release -j$(sysctl -n hw.ncpu)
done

# Create universal library (if multiple architectures)
if [ $(echo $ARCHS | wc -w) -gt 1 ]; then
    echo "Creating universal library..."
    lipo -create \
        $(for arch in $ARCHS; do echo "$BUILD_DIR/$arch/libjabcode.a"; done) \
        -output "$BUILD_DIR/libjabcode.a"
fi

echo "Build complete! Library at: $BUILD_DIR/libjabcode.a"
```

### CMakeLists.txt for iOS

```cmake
cmake_minimum_required(VERSION 3.20)
project(jabcode-ios C)

set(CMAKE_C_STANDARD 99)
set(CMAKE_C_STANDARD_REQUIRED ON)

# iOS-specific settings
if(CMAKE_SYSTEM_NAME STREQUAL "iOS")
    set(CMAKE_XCODE_ATTRIBUTE_CODE_SIGNING_ALLOWED "NO")
    set(CMAKE_XCODE_ATTRIBUTE_CODE_SIGN_IDENTITY "")
    set(CMAKE_XCODE_ATTRIBUTE_ENABLE_BITCODE "YES")
endif()

# Source files
set(JABCODE_SOURCES
    encoder.c
    decoder.c
    ldpc.c
    detector.c
    binarizer.c
    image.c
    mask.c
    pseudo_random.c
    sample.c
    transform.c
)

# Create static library
add_library(jabcode STATIC ${JABCODE_SOURCES})

target_include_directories(jabcode PUBLIC
    ${CMAKE_CURRENT_SOURCE_DIR}/include
)

# Optimization flags for release
if(CMAKE_BUILD_TYPE STREQUAL "Release")
    target_compile_options(jabcode PRIVATE
        -O3
        -flto
        -fvisibility=hidden
        -ffast-math
    )
endif()

# Link libraries
find_library(ACCELERATE_FRAMEWORK Accelerate)
if(ACCELERATE_FRAMEWORK)
    target_link_libraries(jabcode ${ACCELERATE_FRAMEWORK})
endif()

# PNG library (can link system libpng or custom build)
find_library(PNG_LIBRARY png)
if(PNG_LIBRARY)
    target_link_libraries(jabcode ${PNG_LIBRARY})
endif()
```

---

## 🌉 Swift C Interop

### Module Map (module.modulemap)

```modulemap
module CJABCode [system] {
    header "jabcode.h"
    header "encoder.h"
    header "decoder.h"
    
    export *
    
    link "png"
    link framework "Accelerate"
}
```

### Bridging Header (JABCode-Bridging-Header.h)

```objc
#ifndef JABCode_Bridging_Header_h
#define JABCode_Bridging_Header_h

#import "jabcode.h"
#import "encoder.h"
#import "decoder.h"

// Convenience wrappers if needed
#ifdef __cplusplus
extern "C" {
#endif

// Add any Objective-C++ helper functions here if needed

#ifdef __cplusplus
}
#endif

#endif /* JABCode_Bridging_Header_h */
```

### Swift Wrapper for C Structures

```swift
import Foundation
import CJABCode

// MARK: - Type-safe wrappers for C structures

/// Managed wrapper for jab_encode
final class EncoderContext {
    private(set) var handle: UnsafeMutablePointer<jab_encode>
    
    init(colorNumber: Int32, symbolNumber: Int32) throws {
        guard let encoder = createEncode(colorNumber, symbolNumber) else {
            throw JABCodeError.encoderCreationFailed
        }
        self.handle = encoder
    }
    
    deinit {
        destroyEncode(handle)
    }
    
    var palette: UnsafeMutablePointer<jab_byte> {
        handle.pointee.palette
    }
    
    var bitmap: UnsafeMutablePointer<jab_bitmap>? {
        handle.pointee.bitmap
    }
}

/// Managed wrapper for jab_data
final class DataBuffer {
    private(set) var handle: UnsafeMutablePointer<jab_data>
    
    init(data: Data) throws {
        let length = Int32(data.count)
        let size = MemoryLayout<jab_data>.size + Int(length) * MemoryLayout<jab_char>.size
        
        guard let buffer = malloc(size)?.assumingMemoryBound(to: jab_data.self) else {
            throw JABCodeError.allocationFailed
        }
        
        buffer.pointee.length = length
        data.withUnsafeBytes { bytes in
            guard let baseAddress = bytes.baseAddress else { return }
            memcpy(&buffer.pointee.data, baseAddress, Int(length))
        }
        
        self.handle = buffer
    }
    
    convenience init(string: String) throws {
        guard let data = string.data(using: .utf8) else {
            throw JABCodeError.invalidInput
        }
        try self.init(data: data)
    }
    
    deinit {
        free(handle)
    }
    
    func toData() -> Data {
        let length = Int(handle.pointee.length)
        return Data(bytes: &handle.pointee.data, count: length)
    }
    
    func toString() -> String? {
        let data = toData()
        return String(data: data, encoding: .utf8)
    }
}
```

---

## 🎨 Swift API Design

### Main API (JABCode.swift)

```swift
import Foundation
import UIKit
import CJABCode

/// JABCode encoder and decoder for iOS
@available(iOS 13.0, *)
public final class JABCode {
    
    // MARK: - Singleton
    
    public static let shared = JABCode()
    
    private init() {
        // Ensure library is loaded
        _ = getVersion()
    }
    
    // MARK: - Version
    
    /// Get JABCode library version
    public static func getVersion() -> String {
        String(cString: JABCODE_VERSION)
    }
    
    // MARK: - Encoding
    
    /// Encode text data into a JABCode image
    ///
    /// - Parameters:
    ///   - text: The text to encode (UTF-8)
    ///   - options: Encoding options
    /// - Returns: UIImage containing the JABCode
    /// - Throws: JABCodeError if encoding fails
    public func encode(
        _ text: String,
        options: EncodeOptions = .default
    ) throws -> UIImage {
        try Encoder().encode(text, options: options)
    }
    
    /// Encode data into a JABCode image
    ///
    /// - Parameters:
    ///   - data: The data to encode
    ///   - options: Encoding options
    /// - Returns: UIImage containing the JABCode
    /// - Throws: JABCodeError if encoding fails
    public func encode(
        _ data: Data,
        options: EncodeOptions = .default
    ) throws -> UIImage {
        try Encoder().encode(data, options: options)
    }
    
    // MARK: - Decoding
    
    /// Decode a JABCode from an image
    ///
    /// - Parameter image: UIImage containing a JABCode
    /// - Returns: Decoded result with data and metadata
    /// - Throws: JABCodeError if decoding fails
    public func decode(_ image: UIImage) throws -> DecodeResult {
        try Decoder().decode(image)
    }
    
    /// Decode a JABCode from a CGImage
    ///
    /// - Parameter cgImage: CGImage containing a JABCode
    /// - Returns: Decoded result with data and metadata
    /// - Throws: JABCodeError if decoding fails
    public func decode(_ cgImage: CGImage) throws -> DecodeResult {
        try Decoder().decode(cgImage)
    }
}

// MARK: - Async API

@available(iOS 13.0, *)
extension JABCode {
    
    /// Asynchronously encode text data
    public func encode(
        _ text: String,
        options: EncodeOptions = .default
    ) async throws -> UIImage {
        try await Task.detached(priority: .userInitiated) {
            try self.encode(text, options: options)
        }.value
    }
    
    /// Asynchronously decode an image
    public func decode(_ image: UIImage) async throws -> DecodeResult {
        try await Task.detached(priority: .userInitiated) {
            try self.decode(image)
        }.value
    }
}
```

### Encoder (Encoder.swift)

```swift
import Foundation
import UIKit
import CJABCode

/// JABCode encoder
@available(iOS 13.0, *)
public final class Encoder {
    
    private var context: EncoderContext?
    
    public init() {}
    
    /// Encode text into a JABCode image
    public func encode(
        _ text: String,
        options: EncodeOptions = .default
    ) throws -> UIImage {
        guard let data = text.data(using: .utf8) else {
            throw JABCodeError.invalidInput
        }
        return try encode(data, options: options)
    }
    
    /// Encode data into a JABCode image
    public func encode(
        _ data: Data,
        options: EncodeOptions = .default
    ) throws -> UIImage {
        // Validate input
        guard data.count > 0 else {
            throw JABCodeError.invalidInput
        }
        
        guard data.count <= 65536 else {
            throw JABCodeError.dataTooLarge
        }
        
        // Create encoder
        let context = try EncoderContext(
            colorNumber: Int32(options.colorNumber),
            symbolNumber: Int32(options.symbolNumber)
        )
        self.context = context
        
        // Configure encoder
        context.handle.pointee.symbol_ecc_levels[0] = jab_byte(options.eccLevel)
        context.handle.pointee.module_size = Int32(options.moduleSize)
        
        // Prepare input data
        let inputData = try DataBuffer(data: data)
        
        // Generate code
        let result = generateJABCode(context.handle, inputData.handle)
        
        guard result == 0 else {
            throw JABCodeError.encodingFailed(code: Int(result))
        }
        
        // Convert bitmap to UIImage
        guard let bitmap = context.bitmap else {
            throw JABCodeError.noBitmapGenerated
        }
        
        return try createUIImage(from: bitmap.pointee)
    }
    
    // MARK: - Private Helpers
    
    private func createUIImage(from bitmap: jab_bitmap) throws -> UIImage {
        let width = Int(bitmap.width)
        let height = Int(bitmap.height)
        let bytesPerPixel = Int(bitmap.bits_per_pixel) / 8
        let bytesPerRow = width * bytesPerPixel
        
        // Create color space
        guard let colorSpace = CGColorSpace(name: CGColorSpace.sRGB) else {
            throw JABCodeError.colorSpaceCreationFailed
        }
        
        // Create data provider
        let data = Data(bytes: bitmap.pixel, count: height * bytesPerRow)
        guard let provider = CGDataProvider(data: data as CFData) else {
            throw JABCodeError.dataProviderCreationFailed
        }
        
        // Create CGImage
        let bitmapInfo = CGBitmapInfo(rawValue: CGImageAlphaInfo.last.rawValue)
        guard let cgImage = CGImage(
            width: width,
            height: height,
            bitsPerComponent: Int(bitmap.bits_per_channel),
            bitsPerPixel: Int(bitmap.bits_per_pixel),
            bytesPerRow: bytesPerRow,
            space: colorSpace,
            bitmapInfo: bitmapInfo,
            provider: provider,
            decode: nil,
            shouldInterpolate: false,
            intent: .defaultIntent
        ) else {
            throw JABCodeError.imageCreationFailed
        }
        
        return UIImage(cgImage: cgImage)
    }
}
```

### Decoder (Decoder.swift)

```swift
import Foundation
import UIKit
import CJABCode

/// JABCode decoder
@available(iOS 13.0, *)
public final class Decoder {
    
    public init() {}
    
    /// Decode a JABCode from UIImage
    public func decode(_ image: UIImage) throws -> DecodeResult {
        guard let cgImage = image.cgImage else {
            throw JABCodeError.invalidImage
        }
        return try decode(cgImage)
    }
    
    /// Decode a JABCode from CGImage
    public func decode(_ cgImage: CGImage) throws -> DecodeResult {
        // Convert CGImage to jab_bitmap
        var bitmap = try createBitmap(from: cgImage)
        defer {
            // Free allocated memory
            if let pixel = bitmap.pixel {
                free(pixel)
                bitmap.pixel = nil
            }
        }
        
        // Decode
        var detectionResult: jab_int32 = 0
        guard let decodedSymbols = decodeJABCode(&bitmap, NORMAL_DECODE, &detectionResult) else {
            throw JABCodeError.decodingFailed(code: Int(detectionResult))
        }
        defer {
            free(decodedSymbols)
        }
        
        // Extract decoded data
        guard let data = decodedSymbols.pointee.data else {
            throw JABCodeError.noDataDecoded
        }
        
        let length = Int(data.pointee.length)
        let bytes = Data(bytes: &data.pointee.data, count: length)
        
        // Extract metadata
        let metadata = DecodeResult.Metadata(
            symbolNumber: Int(decodedSymbols.pointee.symbol_number),
            colorNumber: Int(decodedSymbols.pointee.color_number),
            eccLevel: Int(decodedSymbols.pointee.ecc_level),
            side_version_x: Int(decodedSymbols.pointee.side_version.x),
            side_version_y: Int(decodedSymbols.pointee.side_version.y)
        )
        
        return DecodeResult(data: bytes, metadata: metadata)
    }
    
    // MARK: - Private Helpers
    
    private func createBitmap(from cgImage: CGImage) throws -> jab_bitmap {
        let width = cgImage.width
        let height = cgImage.height
        let bytesPerPixel = 4 // RGBA
        let bytesPerRow = width * bytesPerPixel
        let bitsPerComponent = 8
        
        // Allocate pixel buffer
        guard let pixels = malloc(height * bytesPerRow) else {
            throw JABCodeError.allocationFailed
        }
        
        // Create bitmap context
        guard let colorSpace = CGColorSpace(name: CGColorSpace.sRGB),
              let context = CGContext(
                data: pixels,
                width: width,
                height: height,
                bitsPerComponent: bitsPerComponent,
                bytesPerRow: bytesPerRow,
                space: colorSpace,
                bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
              ) else {
            free(pixels)
            throw JABCodeError.contextCreationFailed
        }
        
        // Draw image into context
        context.draw(cgImage, in: CGRect(x: 0, y: 0, width: width, height: height))
        
        // Create jab_bitmap
        var bitmap = jab_bitmap()
        bitmap.width = jab_int32(width)
        bitmap.height = jab_int32(height)
        bitmap.bits_per_pixel = jab_int32(bytesPerPixel * 8)
        bitmap.bits_per_channel = jab_int32(bitsPerComponent)
        bitmap.channel_count = jab_int32(bytesPerPixel)
        bitmap.pixel = pixels.assumingMemoryBound(to: jab_byte.self)
        
        return bitmap
    }
}
```

### Models (EncodeOptions.swift)

```swift
import Foundation

/// Options for JABCode encoding
@available(iOS 13.0, *)
public struct EncodeOptions: Sendable {
    
    /// Number of colors (4, 8, 16, 32, 64, 128, or 256)
    public let colorNumber: Int
    
    /// Number of symbols (1-61)
    public let symbolNumber: Int
    
    /// Error correction level (0-7)
    public let eccLevel: Int
    
    /// Module size in pixels
    public let moduleSize: Int
    
    public init(
        colorNumber: Int = 8,
        symbolNumber: Int = 1,
        eccLevel: Int = 3,
        moduleSize: Int = 12
    ) {
        precondition(Self.validColorNumbers.contains(colorNumber),
                     "Color number must be one of: \(Self.validColorNumbers)")
        precondition((1...61).contains(symbolNumber),
                     "Symbol number must be between 1 and 61")
        precondition((0...7).contains(eccLevel),
                     "ECC level must be between 0 and 7")
        precondition((1...100).contains(moduleSize),
                     "Module size must be between 1 and 100")
        
        self.colorNumber = colorNumber
        self.symbolNumber = symbolNumber
        self.eccLevel = eccLevel
        self.moduleSize = moduleSize
    }
    
    // MARK: - Presets
    
    public static let `default` = EncodeOptions()
    
    public static let highDensity = EncodeOptions(
        colorNumber: 256,
        symbolNumber: 1,
        eccLevel: 5,
        moduleSize: 8
    )
    
    public static let highReliability = EncodeOptions(
        colorNumber: 8,
        symbolNumber: 1,
        eccLevel: 7,
        moduleSize: 16
    )
    
    public static let compact = EncodeOptions(
        colorNumber: 64,
        symbolNumber: 1,
        eccLevel: 4,
        moduleSize: 10
    )
    
    // MARK: - Validation
    
    private static let validColorNumbers: Set<Int> = [4, 8, 16, 32, 64, 128, 256]
}

// MARK: - Codable Conformance

extension EncodeOptions: Codable {}

// MARK: - Equatable Conformance

extension EncodeOptions: Equatable {}
```

### Error Handling (JABCodeError.swift)

```swift
import Foundation

/// Errors that can occur during JABCode operations
@available(iOS 13.0, *)
public enum JABCodeError: LocalizedError, Sendable {
    
    // Encoding errors
    case encoderCreationFailed
    case encodingFailed(code: Int)
    case noBitmapGenerated
    
    // Decoding errors
    case decoderCreationFailed
    case decodingFailed(code: Int)
    case noDataDecoded
    case detectionFailed
    
    // Input validation
    case invalidInput
    case invalidImage
    case dataTooLarge
    
    // Memory errors
    case allocationFailed
    case outOfMemory
    
    // Image conversion errors
    case colorSpaceCreationFailed
    case dataProviderCreationFailed
    case contextCreationFailed
    case imageCreationFailed
    
    // MARK: - LocalizedError
    
    public var errorDescription: String? {
        switch self {
        case .encoderCreationFailed:
            return "Failed to create encoder"
        case .encodingFailed(let code):
            return "Encoding failed with code: \(code)"
        case .noBitmapGenerated:
            return "No bitmap was generated"
            
        case .decoderCreationFailed:
            return "Failed to create decoder"
        case .decodingFailed(let code):
            return "Decoding failed with code: \(code)"
        case .noDataDecoded:
            return "No data could be decoded"
        case .detectionFailed:
            return "Failed to detect JABCode in image"
            
        case .invalidInput:
            return "Invalid input data"
        case .invalidImage:
            return "Invalid image format"
        case .dataTooLarge:
            return "Data exceeds maximum size"
            
        case .allocationFailed:
            return "Memory allocation failed"
        case .outOfMemory:
            return "Out of memory"
            
        case .colorSpaceCreationFailed:
            return "Failed to create color space"
        case .dataProviderCreationFailed:
            return "Failed to create data provider"
        case .contextCreationFailed:
            return "Failed to create graphics context"
        case .imageCreationFailed:
            return "Failed to create image"
        }
    }
    
    public var recoverySuggestion: String? {
        switch self {
        case .dataTooLarge:
            return "Try encoding smaller data or split into multiple codes"
        case .invalidImage:
            return "Ensure image is in a supported format (PNG, JPEG)"
        case .detectionFailed:
            return "Ensure the JABCode is clearly visible and well-lit"
        case .outOfMemory, .allocationFailed:
            return "Close other apps to free memory"
        default:
            return nil
        }
    }
}
```

---

## 📷 Camera Integration

### Camera Manager (CameraManager.swift)

```swift
import Foundation
import AVFoundation
import UIKit
import Combine

/// Camera manager for real-time JABCode scanning
@available(iOS 13.0, *)
@MainActor
public final class JABCodeCameraManager: NSObject, ObservableObject {
    
    // MARK: - Published Properties
    
    @Published public private(set) var isSessionRunning = false
    @Published public private(set) var scanResult: DecodeResult?
    @Published public private(set) var error: Error?
    
    // MARK: - Private Properties
    
    private let session = AVCaptureSession()
    private var videoOutput: AVCaptureVideoDataOutput?
    private let sessionQueue = DispatchQueue(label: "com.jabcode.camera")
    private var isScanning = false
    
    // Configuration
    public var scanInterval: TimeInterval = 0.5 // Scan every 500ms
    private var lastScanTime: TimeInterval = 0
    
    // MARK: - Setup
    
    public override init() {
        super.init()
    }
    
    /// Request camera permission
    public func requestPermission() async -> Bool {
        await withCheckedContinuation { continuation in
            switch AVCaptureDevice.authorizationStatus(for: .video) {
            case .authorized:
                continuation.resume(returning: true)
            case .notDetermined:
                AVCaptureDevice.requestAccess(for: .video) { granted in
                    continuation.resume(returning: granted)
                }
            default:
                continuation.resume(returning: false)
            }
        }
    }
    
    /// Setup camera session
    public func setupSession() async throws {
        guard await requestPermission() else {
            throw JABCodeError.invalidInput
        }
        
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            sessionQueue.async { [weak self] in
                guard let self = self else {
                    continuation.resume(throwing: JABCodeError.encoderCreationFailed)
                    return
                }
                
                do {
                    try self.configureSession()
                    continuation.resume()
                } catch {
                    continuation.resume(throwing: error)
                }
            }
        }
    }
    
    private func configureSession() throws {
        session.beginConfiguration()
        defer { session.commitConfiguration() }
        
        // Set session preset
        session.sessionPreset = .high
        
        // Add video input
        guard let device = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .back),
              let input = try? AVCaptureDeviceInput(device: device) else {
            throw JABCodeError.encoderCreationFailed
        }
        
        guard session.canAddInput(input) else {
            throw JABCodeError.encoderCreationFailed
        }
        session.addInput(input)
        
        // Configure device for better scanning
        try? device.lockForConfiguration()
        if device.isFocusModeSupported(.continuousAutoFocus) {
            device.focusMode = .continuousAutoFocus
        }
        if device.isExposureModeSupported(.continuousAutoExposure) {
            device.exposureMode = .continuousAutoExposure
        }
        device.unlockForConfiguration()
        
        // Add video output
        let output = AVCaptureVideoDataOutput()
        output.setSampleBufferDelegate(self, queue: sessionQueue)
        output.alwaysDiscardsLateVideoFrames = true
        output.videoSettings = [
            kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32BGRA
        ]
        
        guard session.canAddOutput(output) else {
            throw JABCodeError.encoderCreationFailed
        }
        session.addOutput(output)
        
        videoOutput = output
    }
    
    // MARK: - Session Control
    
    public func startSession() {
        sessionQueue.async { [weak self] in
            guard let self = self else { return }
            self.session.startRunning()
            
            Task { @MainActor in
                self.isSessionRunning = self.session.isRunning
            }
        }
    }
    
    public func stopSession() {
        sessionQueue.async { [weak self] in
            guard let self = self else { return }
            self.session.stopRunning()
            
            Task { @MainActor in
                self.isSessionRunning = false
            }
        }
    }
    
    // MARK: - Scanning Control
    
    public func startScanning() {
        isScanning = true
        scanResult = nil
        error = nil
    }
    
    public func stopScanning() {
        isScanning = false
    }
    
    // MARK: - Preview Layer
    
    public func makePreviewLayer() -> AVCaptureVideoPreviewLayer {
        let previewLayer = AVCaptureVideoPreviewLayer(session: session)
        previewLayer.videoGravity = .resizeAspectFill
        return previewLayer
    }
}

// MARK: - AVCaptureVideoDataOutputSampleBufferDelegate

extension JABCodeCameraManager: AVCaptureVideoDataOutputSampleBufferDelegate {
    
    nonisolated public func captureOutput(
        _ output: AVCaptureOutput,
        didOutput sampleBuffer: CMSampleBuffer,
        from connection: AVCaptureConnection
    ) {
        guard isScanning else { return }
        
        // Throttle scanning
        let currentTime = CACurrentMediaTime()
        guard currentTime - lastScanTime >= scanInterval else { return }
        lastScanTime = currentTime
        
        // Extract image from sample buffer
        guard let pixelBuffer = CMSampleBufferGetImageBuffer(sampleBuffer) else {
            return
        }
        
        // Convert to UIImage
        let ciImage = CIImage(cvPixelBuffer: pixelBuffer)
        let context = CIContext()
        guard let cgImage = context.createCGImage(ciImage, from: ciImage.extent) else {
            return
        }
        
        // Attempt decode
        do {
            let result = try JABCode.shared.decode(cgImage)
            
            Task { @MainActor in
                self.scanResult = result
                self.isScanning = false
            }
        } catch {
            // Decoding failed, continue scanning
        }
    }
}
```

### SwiftUI View (ScannerView.swift)

```swift
import SwiftUI
import AVFoundation

/// SwiftUI view for real-time JABCode scanning
@available(iOS 14.0, *)
public struct JABCodeScannerView: View {
    
    @StateObject private var cameraManager = JABCodeCameraManager()
    @State private var scanResult: DecodeResult?
    @State private var showingResult = false
    
    public var onScan: ((DecodeResult) -> Void)?
    
    public init(onScan: ((DecodeResult) -> Void)? = nil) {
        self.onScan = onScan
    }
    
    public var body: some View {
        ZStack {
            // Camera preview
            CameraPreviewView(cameraManager: cameraManager)
                .edgesIgnoringSafeArea(.all)
            
            // Scanning overlay
            VStack {
                Spacer()
                
                if cameraManager.isScanning {
                    ProgressView("Scanning...")
                        .padding()
                        .background(Color.black.opacity(0.7))
                        .cornerRadius(10)
                }
                
                Spacer()
            }
        }
        .task {
            try? await cameraManager.setupSession()
            cameraManager.startSession()
            cameraManager.startScanning()
        }
        .onDisappear {
            cameraManager.stopSession()
        }
        .onChange(of: cameraManager.scanResult) { result in
            guard let result = result else { return }
            scanResult = result
            showingResult = true
            onScan?(result)
        }
        .alert("Scan Result", isPresented: $showingResult, presenting: scanResult) { _ in
            Button("OK") {
                cameraManager.startScanning()
            }
        } message: { result in
            if let text = result.text {
                Text(text)
            }
        }
    }
}

/// UIViewRepresentable wrapper for camera preview
struct CameraPreviewView: UIViewRepresentable {
    let cameraManager: JABCodeCameraManager
    
    func makeUIView(context: Context) -> UIView {
        let view = UIView(frame: .zero)
        let previewLayer = cameraManager.makePreviewLayer()
        previewLayer.frame = view.bounds
        view.layer.addSublayer(previewLayer)
        context.coordinator.previewLayer = previewLayer
        return view
    }
    
    func updateUIView(_ uiView: UIView, context: Context) {
        context.coordinator.previewLayer?.frame = uiView.bounds
    }
    
    func makeCoordinator() -> Coordinator {
        Coordinator()
    }
    
    class Coordinator {
        var previewLayer: AVCaptureVideoPreviewLayer?
    }
}
```

---

## 🧠 Memory Management

### ARC Integration

```swift
/// Wrapper for C resources with automatic cleanup
final class ManagedCResource<T> {
    private var resource: UnsafeMutablePointer<T>?
    private let deallocator: (UnsafeMutablePointer<T>) -> Void
    
    init(
        allocator: () throws -> UnsafeMutablePointer<T>,
        deallocator: @escaping (UnsafeMutablePointer<T>) -> Void
    ) throws {
        self.resource = try allocator()
        self.deallocator = deallocator
    }
    
    func withResource<Result>(_ body: (UnsafeMutablePointer<T>) throws -> Result) rethrows -> Result {
        guard let resource = resource else {
            fatalError("Accessing deallocated resource")
        }
        return try body(resource)
    }
    
    deinit {
        if let resource = resource {
            deallocator(resource)
            self.resource = nil
        }
    }
}

// Usage example
let encoder = try ManagedCResource<jab_encode>(
    allocator: { createEncode(8, 1) },
    deallocator: { destroyEncode($0) }
)
```

---

[← Back to Index](index.md) | [Android Integration ←](android-integration.md) | [Performance Optimization →](performance-optimization.md)
