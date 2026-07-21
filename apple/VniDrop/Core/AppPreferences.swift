import Foundation
import Combine
import VnidropCore

/// Receive-destination descriptor, ported from `core/FileSystemService.kt`.
enum ReceiveFolderKind: String, Codable, Sendable {
	case fileSystemPath
	case iosSecurityScopedUrl
}

struct ReceiveFolder: Equatable, Codable, Sendable {
	let kind: ReceiveFolderKind
	let value: String
	let displayName: String
}

enum FolderAccessStatus {
	case writable
	case permissionRequired
	case unavailable
}

/// Relay transport choice. Mirrors the core's `RelayMode` without the URLs, which
/// are stored alongside so a user can switch modes without retyping them.
///
/// `disabled` is local-network only: without relays the endpoint never discovers
/// a public address, so peers can only be reached on the same network.
enum RelayModeSetting: String, Codable, Sendable, CaseIterable {
	case standard
	case custom
	case disabled
}

struct RelaySettings: Equatable, Sendable {
	var mode: RelayModeSetting = .standard
	/// Retained across mode changes; only meaningful when `mode == .custom`.
	var customUrls: [String] = []
}

extension RelaySettings {
	/// Translates the persisted setting into the core's relay configuration.
	///
	/// Custom with no usable URL would leave the endpoint with an empty relay map
	/// — silently local-network only — so it falls back to the default relays.
	var coreRelayMode: RelayMode {
		switch mode {
		case .standard:
			return .default
		case .disabled:
			return .disabled
		case .custom:
			let urls = customUrls
				.map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
				.filter { !$0.isEmpty }
			return urls.isEmpty ? .default : .custom(urls: urls)
		}
	}
}

/// Persisted app preferences, ported from `preferences/AppPreferencesRepository.kt`.
/// Backed by `UserDefaults` instead of DataStore; keys and semantics match.
struct AppPreferences: Equatable {
	var username: String
	var receiveFolder: ReceiveFolder
	var themeMode: ThemeMode
	var notificationsEnabled: Bool
	var diagnosticsEnabled: Bool
	var diagnosticsInstallId: String
	var relay: RelaySettings
}

struct AppPreferencesDefaults {
	let username: String
	let receiveFolder: ReceiveFolder
	let themeMode: ThemeMode
	var notificationsEnabled: Bool = false
	var diagnosticsEnabled: Bool = false
}

@MainActor
final class AppPreferencesRepository: ObservableObject {
	@Published private(set) var preferences: AppPreferences

	private let defaults: UserDefaults
	private let fallback: AppPreferencesDefaults

	private enum Key {
		static let username = "username"
		static let receiveFolderKind = "receive_folder_kind"
		static let receiveFolderValue = "receive_folder_value"
		static let receiveFolderDisplayName = "receive_folder_display_name"
		static let themeMode = "theme_mode"
		static let notificationsEnabled = "notifications_enabled"
		static let diagnosticsEnabled = "diagnostics_enabled"
		static let diagnosticsInstallId = "diagnostics_install_id"
		static let relayMode = "relay_mode"
		static let relayCustomUrls = "relay_custom_urls"
	}

	init(defaults: UserDefaults = .standard, fallback: AppPreferencesDefaults) {
		self.defaults = defaults
		self.fallback = fallback
		self.preferences = Self.read(from: defaults, fallback: fallback)
	}

	private static func read(from defaults: UserDefaults, fallback: AppPreferencesDefaults) -> AppPreferences {
		let username = (defaults.string(forKey: Key.username)).flatMap { $0.isEmpty ? nil : $0 } ?? fallback.username
		let folder = resolveReceiveFolder(defaults, fallback: fallback.receiveFolder)
		let themeMode = defaults.string(forKey: Key.themeMode).flatMap(ThemeMode.init(rawValue:)) ?? fallback.themeMode
		let notifications = defaults.object(forKey: Key.notificationsEnabled) as? Bool ?? fallback.notificationsEnabled
		let diagnostics = defaults.object(forKey: Key.diagnosticsEnabled) as? Bool ?? fallback.diagnosticsEnabled
		let installId = defaults.string(forKey: Key.diagnosticsInstallId) ?? ""
		// An unknown persisted mode falls back to the shipped default rather than
		// failing initialization; the URLs stay so the user can switch back.
		let relayMode = defaults.string(forKey: Key.relayMode)
			.flatMap(RelayModeSetting.init(rawValue:)) ?? .standard
		let relayUrls = defaults.stringArray(forKey: Key.relayCustomUrls) ?? []
		return AppPreferences(
			username: username,
			receiveFolder: folder,
			themeMode: themeMode,
			notificationsEnabled: notifications,
			diagnosticsEnabled: diagnostics,
			diagnosticsInstallId: installId,
			relay: RelaySettings(mode: relayMode, customUrls: relayUrls)
		)
	}

	private static func resolveReceiveFolder(_ defaults: UserDefaults, fallback: ReceiveFolder) -> ReceiveFolder {
		let kind = defaults.string(forKey: Key.receiveFolderKind)
			.flatMap(ReceiveFolderKind.init(rawValue:)) ?? fallback.kind
		let value = defaults.string(forKey: Key.receiveFolderValue).flatMap { $0.isEmpty ? nil : $0 } ?? fallback.value
		let displayName = defaults.string(forKey: Key.receiveFolderDisplayName)
			.flatMap { $0.isEmpty ? nil : $0 } ?? fallback.displayName
		return ReceiveFolder(kind: kind, value: value, displayName: displayName)
	}

	private func reload() {
		preferences = Self.read(from: defaults, fallback: fallback)
	}

	func setUsername(_ username: String) {
		defaults.set(username.trimmingCharacters(in: .whitespacesAndNewlines), forKey: Key.username)
		reload()
	}

	func setReceiveFolder(_ folder: ReceiveFolder) {
		defaults.set(folder.kind.rawValue, forKey: Key.receiveFolderKind)
		defaults.set(folder.value, forKey: Key.receiveFolderValue)
		defaults.set(folder.displayName, forKey: Key.receiveFolderDisplayName)
		reload()
	}

	func resetReceiveFolder() {
		setReceiveFolder(fallback.receiveFolder)
	}

	func setThemeMode(_ mode: ThemeMode) {
		defaults.set(mode.rawValue, forKey: Key.themeMode)
		reload()
	}

	func setNotificationsEnabled(_ enabled: Bool) {
		defaults.set(enabled, forKey: Key.notificationsEnabled)
		reload()
	}

	/// Relay changes only take effect when the core is next initialized, because
	/// the endpoint is built once at startup.
	func setRelay(_ relay: RelaySettings) {
		defaults.set(relay.mode.rawValue, forKey: Key.relayMode)
		defaults.set(relay.customUrls, forKey: Key.relayCustomUrls)
		reload()
	}

	func setDiagnosticsEnabled(_ enabled: Bool) {
		defaults.set(enabled, forKey: Key.diagnosticsEnabled)
		reload()
	}

	@discardableResult
	func ensureDiagnosticsInstallId() -> String {
		let existing = preferences.diagnosticsInstallId
		if !existing.isEmpty { return existing }
		let created = UUID().uuidString
		defaults.set(created, forKey: Key.diagnosticsInstallId)
		reload()
		return created
	}
}
