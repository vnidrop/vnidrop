import Foundation
import Combine

/// Receive-destination descriptor, ported from `core/FileSystemService.kt`.
enum ReceiveFolderKind: String, Codable {
	case fileSystemPath
	case iosSecurityScopedUrl
}

struct ReceiveFolder: Equatable, Codable {
	let kind: ReceiveFolderKind
	let value: String
	let displayName: String
}

enum FolderAccessStatus {
	case writable
	case permissionRequired
	case unavailable
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
		return AppPreferences(
			username: username,
			receiveFolder: folder,
			themeMode: themeMode,
			notificationsEnabled: notifications,
			diagnosticsEnabled: diagnostics,
			diagnosticsInstallId: installId
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
