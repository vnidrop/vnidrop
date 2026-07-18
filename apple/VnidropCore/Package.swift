// swift-tools-version:5.9
import PackageDescription

// Local package wrapping the VniDrop Rust core:
//   - `vnidrop` binary target: the xcframework of static libraries + the FFI
//     C module (`vnidropFFI`), produced by apple/scripts/build-core.sh.
//   - `VnidropCore` target: the generated Swift bindings (`Vnidrop.swift`).
//
// Both `vnidrop.xcframework` and `Sources/VnidropCore/Vnidrop.swift` are build
// outputs and are gitignored; run apple/scripts/build-core.sh to (re)generate.
let package = Package(
	name: "VnidropCore",
	platforms: [
		.iOS(.v16),
		.macOS(.v13),
	],
	products: [
		.library(name: "VnidropCore", targets: ["VnidropCore"]),
	],
	targets: [
		.binaryTarget(
			name: "vnidrop",
			path: "vnidrop.xcframework"
		),
		.target(
			name: "VnidropCore",
			dependencies: ["vnidrop"]
		),
	]
)
