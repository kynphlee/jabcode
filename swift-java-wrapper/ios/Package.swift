// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "JABCodeMobile",
    platforms: [
        .iOS(.v13),
        .macOS(.v10_15)
    ],
    products: [
        .library(
            name: "JABCodeMobile",
            targets: ["JABCodeMobile"]
        ),
    ],
    targets: [
        // C library target
        .target(
            name: "JABCodeCore",
            path: ".",
            exclude: [
                "Sources/JABCodeMobile",
                "../android",
                "../test",
                "../build"
            ],
            sources: [
                "../src/c/mobile_bridge.c",
                "../src/c/mobile_utils.c",
                "../../src/jabcode/encoder.c",
                "../../src/jabcode/decoder.c",
                "../../src/jabcode/ldpc.c",
                "../../src/jabcode/detector.c",
                "../../src/jabcode/detector_synthetic.c",
                "../../src/jabcode/binarizer.c",
                "../../src/jabcode/mask.c",
                "../../src/jabcode/sample.c",
                "../../src/jabcode/transform.c",
                "../../src/jabcode/interleave.c",
                "../../src/jabcode/pseudo_random.c",
                "../../src/jabcode/color_calibration.c"
            ],
            publicHeadersPath: "include",
            cSettings: [
                .headerSearchPath("../include"),
                .headerSearchPath("../../src/jabcode/include"),
                .headerSearchPath("../../src/jabcode"),
                .define("MOBILE_BUILD"),
                .unsafeFlags(["-O3", "-ffast-math"])
            ]
        ),
        // Swift wrapper target
        .target(
            name: "JABCodeMobile",
            dependencies: ["JABCodeCore"],
            path: "Sources/JABCodeMobile"
        ),
        // Test target
        .testTarget(
            name: "JABCodeMobileTests",
            dependencies: ["JABCodeMobile"],
            path: "Tests/JABCodeMobileTests"
        ),
    ],
    cLanguageStandard: .c11
)
