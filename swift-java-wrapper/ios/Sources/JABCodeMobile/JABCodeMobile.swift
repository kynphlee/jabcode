import Foundation
import JABCodeCore

/// Cross-platform JABCode mobile encoder/decoder for iOS
/// Mirrors Android API for consistency
public final class JABCodeMobile {
    
    // MARK: - Public API
    
    /// Encoding parameters
    public struct EncodeParams {
        public let colorNumber: Int32      // 4, 8, 16, 32, 64, 128
        public let symbolNumber: Int32     // Default: 1, Max: 4
        public let eccLevel: Int32         // 0-7 (default: 3)
        public let moduleSize: Int32       // Pixels per module (default: 12)
        
        public init(colorNumber: Int32 = 4,
                    symbolNumber: Int32 = 1,
                    eccLevel: Int32 = 3,
                    moduleSize: Int32 = 12) {
            self.colorNumber = colorNumber
            self.symbolNumber = symbolNumber
            self.eccLevel = eccLevel
            self.moduleSize = moduleSize
        }
    }
    
    /// Encoding result containing bitmap and metadata
    public final class EncodeResult {
        let nativePtr: UnsafeMutablePointer<jab_mobile_encode_result>
        public let width: Int
        public let height: Int
        public let rgbaData: Data
        
        internal init(nativePtr: UnsafeMutablePointer<jab_mobile_encode_result>) {
            self.nativePtr = nativePtr
            self.width = Int(nativePtr.pointee.width)
            self.height = Int(nativePtr.pointee.height)
            
            // Copy RGBA buffer to Swift Data
            let bufferSize = width * height * 4
            self.rgbaData = Data(bytes: nativePtr.pointee.rgba_buffer,
                                count: bufferSize)
        }
        
        deinit {
            jabMobileEncodeResultFree(nativePtr)
        }
    }
    
    /// Encode data to JABCode bitmap
    /// - Parameters:
    ///   - data: Data to encode
    ///   - params: Encoding parameters
    /// - Returns: Encode result or nil on failure
    /// - Throws: JABCodeError on encoding failure
    public static func encode(data: Data, params: EncodeParams = EncodeParams()) throws -> EncodeResult {
        var cParams = jab_mobile_encode_params(
            color_number: params.colorNumber,
            symbol_number: params.symbolNumber,
            ecc_level: params.eccLevel,
            module_size: params.moduleSize
        )
        
        let result = data.withUnsafeBytes { (bytes: UnsafeRawBufferPointer) -> UnsafeMutablePointer<jab_mobile_encode_result>? in
            guard let baseAddress = bytes.baseAddress else { return nil }
            let charPtr = baseAddress.assumingMemoryBound(to: jab_char.self)
            return jabMobileEncode(charPtr, Int32(data.count), &cParams)
        }
        
        guard let result = result else {
            throw JABCodeError.encodeFailed(getLastError())
        }
        
        return EncodeResult(nativePtr: result)
    }
    
    /// Decode JABCode from encode result (optimal for roundtrip)
    /// - Parameters:
    ///   - encodeResult: The encode result to decode
    ///   - colorNumber: Color count used during encoding
    ///   - eccLevel: Error correction level used during encoding
    /// - Returns: Decoded data
    /// - Throws: JABCodeError on decoding failure
    public static func decode(encodeResult: EncodeResult,
                             colorNumber: Int32,
                             eccLevel: Int32) throws -> Data {
        let decoded = jabMobileDecode(encodeResult.nativePtr, colorNumber, eccLevel)
        
        guard let decoded = decoded else {
            throw JABCodeError.decodeFailed(getLastError())
        }
        
        defer { jabMobileDataFree(decoded) }
        
        return Data(bytes: decoded.pointee.data,
                   count: Int(decoded.pointee.length))
    }
    
    /// Decode JABCode from camera bitmap (full detection pipeline)
    /// - Parameters:
    ///   - rgbaData: RGBA pixel data from camera
    ///   - width: Image width in pixels
    ///   - height: Image height in pixels
    /// - Returns: Decoded data
    /// - Throws: JABCodeError on decoding failure
    public static func decodeFromBitmap(rgbaData: Data,
                                       width: Int32,
                                       height: Int32) throws -> Data {
        let decoded = rgbaData.withUnsafeBytes { (bytes: UnsafeRawBufferPointer) -> UnsafeMutablePointer<jab_data>? in
            guard let baseAddress = bytes.baseAddress else { return nil }
            let bytePtr = baseAddress.assumingMemoryBound(to: jab_byte.self)
            return jabMobileDecodeCamera(bytePtr, width, height)
        }
        
        guard let decoded = decoded else {
            throw JABCodeError.cameraDecodeFailed(getLastError())
        }
        
        defer { jabMobileDataFree(decoded) }
        
        return Data(bytes: decoded.pointee.data,
                   count: Int(decoded.pointee.length))
    }
    
    /// Get library version
    public static var version: String {
        String(cString: jabMobileGetVersion())
    }
    
    /// Load calibration profile from JSON
    /// - Parameter json: Calibration profile in JSON format
    /// - Returns: true on success, false on failure
    public static func loadCalibration(json: String) -> Bool {
        json.withCString { cString in
            jabMobileLoadCalibration(cString) != 0
        }
    }
    
    /// Clear active calibration profile
    public static func clearCalibration() {
        jabMobileClearCalibration()
    }
    
    /// Check if calibration is active
    public static var hasCalibration: Bool {
        jabMobileHasCalibration() != 0
    }
    
    // MARK: - Error Handling
    
    private static func getLastError() -> String {
        if let errorPtr = jabMobileGetLastError() {
            return String(cString: errorPtr)
        }
        return "Unknown error"
    }
}

// MARK: - Error Types

public enum JABCodeError: Error, LocalizedError {
    case encodeFailed(String)
    case decodeFailed(String)
    case cameraDecodeFailed(String)
    
    public var errorDescription: String? {
        switch self {
        case .encodeFailed(let message):
            return "Encoding failed: \(message)"
        case .decodeFailed(let message):
            return "Decoding failed: \(message)"
        case .cameraDecodeFailed(let message):
            return "Camera decoding failed: \(message)"
        }
    }
}
