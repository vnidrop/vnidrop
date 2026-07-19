import Foundation

/// Platform environment, ported from `Platform.kt` (`PlatformEnvironment`).
struct PlatformEnvironment {
	let name: String
	let appVersion: String
	let defaultCoreDataDir: String
	var defaultUsername: String = "Receiver"
}

/// Device info for diagnostics/about, ported from `DeviceInfo`.
struct DeviceInfo {
	let deviceName: String?
	let deviceModel: String?
	let operatingSystem: String
	let network: String?
	let batteryLevel: String?
}

@MainActor
protocol DeviceInfoProvider {
	func load() async -> DeviceInfo
}

/// Bundle of platform dependencies, ported from `AppDependencies`.
struct AppDependencies {
	let environment: PlatformEnvironment
	let deviceInfoProvider: DeviceInfoProvider
	let fileSystemService: FileSystemService
	let notificationService: LocalNotificationService
	let externalInvitations: ExternalInvitationController
}
