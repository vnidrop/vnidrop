// swift-tools-version:5.9
import PackageDescription

// Core/UI Swift sources built as a library so the shared logic can be typechecked
// and unit-tested from the command line (macOS). The iOS/macOS app target in the
// Xcode project links the same sources plus the app entry point.
let package = Package(
	name: "VniDropApp",
	defaultLocalization: "en",
	platforms: [
		.iOS(.v16),
		.macOS(.v13),
	],
	products: [
		.library(name: "VniDropApp", targets: ["VniDropApp"]),
	],
	dependencies: [
		.package(path: "VnidropCore"),
	],
	targets: [
		.target(
			name: "VniDropApp",
			dependencies: [.product(name: "VnidropCore", package: "VnidropCore")],
			path: "VniDrop",
			// The @main entry belongs to the Xcode app target only; excluding it
			// keeps this library free of a conflicting `_main` symbol for tests.
			exclude: ["Resources", "App/VniDropApp.swift"],
			// The Rust core (iroh network stack) links these system libraries. The
			// Xcode app target must add the same frameworks under "Link Binary With
			// Libraries" (SystemConfiguration, Security, libresolv).
			linkerSettings: [
				.linkedFramework("SystemConfiguration"),
				.linkedFramework("Security"),
				.linkedLibrary("resolv"),
			]
		),
		.testTarget(
			name: "VniDropAppTests",
			dependencies: ["VniDropApp"],
			path: "Tests"
		),
	]
)
