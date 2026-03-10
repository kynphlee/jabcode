import XCTest
@testable import JABCodeMobile

final class JABCodeMobileTests: XCTestCase {
    
    func testVersion() {
        let version = JABCodeMobile.version
        XCTAssertFalse(version.isEmpty, "Version should not be empty")
        print("JABCode version: \(version)")
    }
    
    func testEncodeDecodeRoundtrip() throws {
        let testString = "Hello, JABCode!"
        let testData = testString.data(using: .utf8)!
        
        // Test 4-color mode
        let params = JABCodeMobile.EncodeParams(
            colorNumber: 4,
            symbolNumber: 1,
            eccLevel: 3,
            moduleSize: 12
        )
        
        // Encode
        let encodeResult = try JABCodeMobile.encode(data: testData, params: params)
        XCTAssertGreaterThan(encodeResult.width, 0, "Width should be positive")
        XCTAssertGreaterThan(encodeResult.height, 0, "Height should be positive")
        XCTAssertFalse(encodeResult.rgbaData.isEmpty, "RGBA data should not be empty")
        
        print("Encoded: \(encodeResult.width)x\(encodeResult.height) bitmap")
        
        // Decode
        let decodedData = try JABCodeMobile.decode(
            encodeResult: encodeResult,
            colorNumber: params.colorNumber,
            eccLevel: params.eccLevel
        )
        
        let decodedString = String(data: decodedData, encoding: .utf8)
        XCTAssertEqual(decodedString, testString, "Decoded data should match original")
        
        print("Roundtrip successful: '\(testString)' -> encoded -> decoded -> '\(decodedString ?? "")'")
    }
    
    func testMultipleColorModes() throws {
        let testData = "Test".data(using: .utf8)!
        let colorModes: [Int32] = [4, 8, 16, 32, 64, 128]
        
        for colorNumber in colorModes {
            let params = JABCodeMobile.EncodeParams(
                colorNumber: colorNumber,
                symbolNumber: 1,
                eccLevel: 3,
                moduleSize: 12
            )
            
            let encodeResult = try JABCodeMobile.encode(data: testData, params: params)
            let decodedData = try JABCodeMobile.decode(
                encodeResult: encodeResult,
                colorNumber: params.colorNumber,
                eccLevel: params.eccLevel
            )
            
            XCTAssertEqual(decodedData, testData, "\(colorNumber)-color mode roundtrip failed")
            print("✓ \(colorNumber)-color mode: OK")
        }
    }
    
    func testEncodingFailureWithInvalidParams() {
        let testData = "Test".data(using: .utf8)!
        
        // Invalid color number (256 is known to be broken)
        let invalidParams = JABCodeMobile.EncodeParams(
            colorNumber: 256,
            symbolNumber: 1,
            eccLevel: 3,
            moduleSize: 12
        )
        
        XCTAssertThrowsError(try JABCodeMobile.encode(data: testData, params: invalidParams)) { error in
            guard case JABCodeError.encodeFailed = error else {
                XCTFail("Expected encodeFailed error")
                return
            }
            print("Correctly threw error for invalid params: \(error)")
        }
    }
    
    func testCalibration() {
        // Initially no calibration
        XCTAssertFalse(JABCodeMobile.hasCalibration, "Should not have calibration initially")
        
        // Load calibration (will fail with invalid JSON but tests the API)
        let result = JABCodeMobile.loadCalibration(json: "{\"test\":\"data\"}")
        
        // Clear calibration
        JABCodeMobile.clearCalibration()
        XCTAssertFalse(JABCodeMobile.hasCalibration, "Calibration should be cleared")
    }
    
    #if canImport(UIKit)
    func testUIImageConversion() throws {
        let testData = "UI".data(using: .utf8)!
        let params = JABCodeMobile.EncodeParams(colorNumber: 4)
        
        let encodeResult = try JABCodeMobile.encode(data: testData, params: params)
        let image = encodeResult.toUIImage()
        
        XCTAssertNotNil(image, "Should create UIImage")
        XCTAssertEqual(image?.size.width, CGFloat(encodeResult.width))
        XCTAssertEqual(image?.size.height, CGFloat(encodeResult.height))
    }
    #endif
}
