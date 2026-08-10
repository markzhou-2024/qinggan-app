// swift-tools-version: 6.0
import PackageDescription

let package = Package(
    name: "QingGanCore",
    platforms: [.iOS(.v17), .macOS(.v14)],
    products: [
        .library(name: "QingGanCore", targets: ["QingGanCore"])
    ],
    targets: [
        .target(name: "QingGanCore"),
        .testTarget(
            name: "QingGanCoreTests",
            dependencies: ["QingGanCore"],
            resources: [.process("Fixtures")]
        )
    ]
)
