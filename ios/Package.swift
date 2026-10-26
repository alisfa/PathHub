// swift-tools-version: 6.0
import PackageDescription

let package = Package(
    name: "PathHub",
    platforms: [
        .iOS(.v17),
    ],
    products: [
        .library(
            name: "PathHub",
            targets: ["PathHub"]
        ),
    ],
    targets: [
        .target(
            name: "PathHub",
            path: "Sources/PathHub",
            exclude: [
                "Resources/player.js",
                "Resources/player.css"
            ]
        ),
    ]
)
