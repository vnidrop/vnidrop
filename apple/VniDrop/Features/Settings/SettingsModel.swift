import Foundation
import Combine

/// Settings sections, ported from `feature/settings/SettingsViewModel.kt`.
enum SettingsSection: Hashable {
	case overview
	case preferences
	case appearance
	case notifications
	case storage
	case about
	case bugReport

	var titleKey: String {
		switch self {
		case .overview: return "settings_title"
		case .preferences: return "preferences_title"
		case .appearance: return "appearance_title"
		case .notifications: return "notifications_title"
		case .storage: return "storage_title"
		case .about: return "about_title"
		case .bugReport: return "about_bug_report"
		}
	}
}

/// On-disk usage breakdown for the Storage screen.
struct StorageBreakdown: Equatable {
	var receivedFiles: UInt64 = 0
	var transferCache: UInt64 = 0
	var appData: UInt64 = 0
	var temporary: UInt64 = 0
	var total: UInt64 { receivedFiles + transferCache + appData + temporary }
}

struct SettingsState: Equatable {
	var selectedSection: SettingsSection = .overview
	var username = ""
	var receiveFolder: ReceiveFolder?
	var folderAccessStatus: FolderAccessStatus = .unavailable
	var isValidatingFolder = false
	var supportsCustomReceiveFolders = true
	var themeMode: ThemeMode = .system
	var notificationsEnabled = false
	var notificationPermission: NotificationPermission = .notDetermined
	var diagnosticsEnabled = false
	var deviceInfo: DeviceInfo?
	var appVersion = ""
	var isLoadingDeviceInfo = false
	var bugWhatHappened = ""
	var bugExpected = ""
	var bugSteps = ""
	var bugContact = ""
	var bugIncludeLogs = true
	var isSubmittingBugReport = false
	var bugLogPreviewBytes = 0
	var storage: StorageBreakdown?
	var isCalculatingStorage = false
	var isDeletingTransfers = false

	static func == (lhs: SettingsState, rhs: SettingsState) -> Bool {
		lhs.selectedSection == rhs.selectedSection && lhs.username == rhs.username
			&& lhs.receiveFolder == rhs.receiveFolder && lhs.folderAccessStatus == rhs.folderAccessStatus
			&& lhs.isValidatingFolder == rhs.isValidatingFolder
			&& lhs.supportsCustomReceiveFolders == rhs.supportsCustomReceiveFolders
			&& lhs.themeMode == rhs.themeMode && lhs.notificationsEnabled == rhs.notificationsEnabled
			&& lhs.notificationPermission == rhs.notificationPermission
			&& lhs.diagnosticsEnabled == rhs.diagnosticsEnabled && lhs.appVersion == rhs.appVersion
			&& lhs.isLoadingDeviceInfo == rhs.isLoadingDeviceInfo
			&& lhs.bugWhatHappened == rhs.bugWhatHappened && lhs.bugExpected == rhs.bugExpected
			&& lhs.bugSteps == rhs.bugSteps && lhs.bugContact == rhs.bugContact
			&& lhs.bugIncludeLogs == rhs.bugIncludeLogs && lhs.isSubmittingBugReport == rhs.isSubmittingBugReport
			&& lhs.bugLogPreviewBytes == rhs.bugLogPreviewBytes
			&& lhs.storage == rhs.storage && lhs.isCalculatingStorage == rhs.isCalculatingStorage
			&& lhs.isDeletingTransfers == rhs.isDeletingTransfers
			&& lhs.deviceInfo?.operatingSystem == rhs.deviceInfo?.operatingSystem
	}
}

@MainActor
final class SettingsModel: ObservableObject {
	@Published private(set) var state: SettingsState
	/// Set by the view to request the receive-folder picker (macOS).
	@Published var pendingReceiveFolderPick = false

	private let environment: PlatformEnvironment
	private let deviceInfoProvider: DeviceInfoProvider
	private let fileSystemService: FileSystemService
	private let repository: CoreGateway
	private let preferences: AppPreferencesRepository
	private let notifications: LocalNotificationService
	private let messages: UiMessageController
	private let bugReports: BugReportService
	private let diagnosticsIncluded: Bool

	private var enableNotificationsAfterSettings = false
	private var usernamePersistTask: Task<Void, Never>?
	private var hasLocalUsernameDraft = false
	private var cancellables = Set<AnyCancellable>()

	init(
		environment: PlatformEnvironment,
		deviceInfoProvider: DeviceInfoProvider,
		fileSystemService: FileSystemService,
		repository: CoreGateway,
		preferences: AppPreferencesRepository,
		notifications: LocalNotificationService,
		messages: UiMessageController,
		bugReports: BugReportService,
		diagnosticsIncluded: Bool = DiagnosticsBuildConfig.included
	) {
		self.environment = environment
		self.deviceInfoProvider = deviceInfoProvider
		self.fileSystemService = fileSystemService
		self.repository = repository
		self.preferences = preferences
		self.notifications = notifications
		self.messages = messages
		self.bugReports = bugReports
		self.diagnosticsIncluded = diagnosticsIncluded
		self.state = SettingsState(
			supportsCustomReceiveFolders: fileSystemService.supportsCustomReceiveFolders,
			appVersion: environment.appVersion
		)

		preferences.$preferences
			.sink { [weak self] prefs in
				guard let self else { return }
				let previousFolder = self.state.receiveFolder
				let folder = self.fileSystemService.effectiveReceiveFolder(prefs.receiveFolder)
				self.state.username = self.hasLocalUsernameDraft ? self.state.username : prefs.username
				self.state.receiveFolder = folder
				self.state.themeMode = prefs.themeMode
				self.state.notificationsEnabled = prefs.notificationsEnabled
				self.state.diagnosticsEnabled = prefs.diagnosticsEnabled
				if folder != previousFolder { Task { await self.validateFolder(folder) } }
			}
			.store(in: &cancellables)

		refreshNotificationPermission()
		loadDeviceInfo()
	}

	func selectSection(_ section: SettingsSection) {
		state.selectedSection = section
		if section == .about || section == .bugReport {
			loadDeviceInfo()
			if section == .bugReport { refreshBugLogPreview() }
		}
	}

	func setUsername(_ value: String) {
		hasLocalUsernameDraft = true
		state.username = value
		usernamePersistTask?.cancel()
		usernamePersistTask = Task {
			try? await Task.sleep(nanoseconds: 350_000_000)
			if Task.isCancelled { return }
			preferences.setUsername(value)
		}
	}

	func setThemeMode(_ mode: ThemeMode) { preferences.setThemeMode(mode) }

	func chooseReceiveFolder() {
		if !fileSystemService.supportsCustomReceiveFolders { return }
		pendingReceiveFolderPick = true
	}

	func onReceiveFolderPicked(_ folder: ReceiveFolder) { preferences.setReceiveFolder(folder) }
	func onReceiveFolderPickFailed(_ reason: String) { messages.error(InvitationError.message(reason)) }
	func resetReceiveFolder() { preferences.resetReceiveFolder() }

	func setNotificationsEnabled(_ enabled: Bool) {
		Task {
			if !enabled {
				preferences.setNotificationsEnabled(false)
				notifications.cancelAll()
				return
			}
			let permission = await notifications.requestPermission()
			state.notificationPermission = permission
			if permission == .granted {
				await enableNotifications()
			} else {
				preferences.setNotificationsEnabled(false)
				let key = permission == .unsupported ? "notifications_unsupported" : "notifications_permission_denied"
				messages.show(UiMessage(
					text: .resource(key),
					tone: .warning,
					actionLabel: permission == .denied ? .resource("button_open_settings") : nil,
					onAction: permission == .denied ? { self.openNotificationSettings() } : nil
				))
			}
		}
	}

	func setDiagnosticsEnabled(_ enabled: Bool) {
		if !diagnosticsIncluded { return }
		Task {
			preferences.setDiagnosticsEnabled(enabled)
			messages.show(UiMessage(
				text: .resource(enabled ? "diagnostics_enabled_message" : "diagnostics_disabled_message"),
				tone: .success
			))
		}
	}

	func setBugWhatHappened(_ value: String) { state.bugWhatHappened = value }
	func setBugExpected(_ value: String) { state.bugExpected = value }
	func setBugSteps(_ value: String) { state.bugSteps = value }
	func setBugContact(_ value: String) { state.bugContact = value }
	func setBugIncludeLogs(_ value: Bool) { state.bugIncludeLogs = value }

	func submitBugReport(onSuccess: @escaping () -> Void = {}) {
		if state.isSubmittingBugReport { return }
		Task {
			let snapshot = state
			let what = snapshot.bugWhatHappened.trimmingCharacters(in: .whitespacesAndNewlines)
			let expected = snapshot.bugExpected.trimmingCharacters(in: .whitespacesAndNewlines)
			if what.isEmpty {
				messages.show(UiMessage(text: .resource("bug_report_missing_what"), tone: .warning))
				return
			}
			if expected.isEmpty {
				messages.show(UiMessage(text: .resource("bug_report_missing_expected"), tone: .warning))
				return
			}
			state.isSubmittingBugReport = true
			let result = await bugReports.submit(
				BugReportDraft(
					whatHappened: what, expected: expected, steps: snapshot.bugSteps,
					contact: snapshot.bugContact, includeLogs: snapshot.bugIncludeLogs
				),
				deviceInfo: snapshot.deviceInfo
			)
			switch result {
			case .success:
				state.isSubmittingBugReport = false
				state.bugWhatHappened = ""
				state.bugExpected = ""
				state.bugSteps = ""
				state.bugContact = ""
				state.bugIncludeLogs = true
				messages.show(UiMessage(text: .resource("bug_report_submitted"), tone: .success))
				onSuccess()
			case .failure:
				state.isSubmittingBugReport = false
				messages.show(UiMessage(text: .resource("bug_report_submit_failed"), tone: .error))
			}
		}
	}

	func openNotificationSettings() {
		Task {
			enableNotificationsAfterSettings = true
			let result = await notifications.openSettings()
			if case .failure = result {
				enableNotificationsAfterSettings = false
				messages.show(UiMessage(text: .resource("notifications_settings_open_failed"), tone: .error))
			}
		}
	}

	func refreshNotificationPermission() {
		Task {
			let permission = await notifications.refreshPermission()
			state.notificationPermission = permission
			if enableNotificationsAfterSettings {
				enableNotificationsAfterSettings = false
				if permission == .granted { await enableNotifications() }
			} else if permission != .granted && state.notificationsEnabled {
				preferences.setNotificationsEnabled(false)
				notifications.cancelAll()
			}
		}
	}

	private func enableNotifications() async {
		preferences.setNotificationsEnabled(true)
		messages.show(UiMessage(text: .resource("notifications_enabled_message"), tone: .success))
	}

	// MARK: - Storage

	/// Recomputes the on-disk usage breakdown off the main actor.
	func loadStorageUsage() {
		if state.isCalculatingStorage { return }
		state.isCalculatingStorage = true
		let tempDir = NSTemporaryDirectory()
		Task {
			let coreResult = await repository.storageUsage()
			let artifactsResult = await repository.receivedArtifacts()
			guard case .success(let core) = coreResult,
				case .success(let artifacts) = artifactsResult else {
				state.isCalculatingStorage = false
				return
			}
			let diskSizes = await Task.detached {
				let received = artifacts.reduce(UInt64(0)) { total, artifact in
					total + SettingsModel.fileSize(artifact.locator)
				}
				return (received, SettingsModel.directorySize(tempDir))
			}.value
			let breakdown = StorageBreakdown(
				receivedFiles: diskSizes.0,
				transferCache: core.blobStoreBytes,
				appData: core.appDataBytes,
				temporary: diskSizes.1
			)
			state.storage = breakdown
			state.isCalculatingStorage = false
		}
	}

	/// Deletes every send/receive transfer via the core, freeing the imported
	/// shared-file content and clearing history. Node identity and received files
	/// are left untouched.
	func deleteAllTransfers() {
		if state.isDeletingTransfers { return }
		state.isDeletingTransfers = true
		Task {
			var failures = 0
			for id in repository.state.transfers.map(\.transferId) {
				if case .failure = await repository.delete(transferId: id) { failures += 1 }
			}
			_ = await repository.refresh()
			state.isDeletingTransfers = false
			if failures == 0 {
				loadStorageUsage()
				messages.show(UiMessage(text: .resource("storage_transfers_deleted"), tone: .success))
			} else {
				messages.error(InvitationError.message("Could not delete \(failures) transfer records"))
			}
		}
	}

	nonisolated static func fileSize(_ path: String) -> UInt64 {
		let values = try? URL(fileURLWithPath: path).resourceValues(
			forKeys: [.isRegularFileKey, .totalFileAllocatedSizeKey, .fileSizeKey]
		)
		guard values?.isRegularFile == true else { return 0 }
		return UInt64(values?.totalFileAllocatedSize ?? values?.fileSize ?? 0)
	}

	/// Recursive size of every regular file under `path` (0 if missing).
	nonisolated static func directorySize(_ path: String) -> UInt64 {
		let url = URL(fileURLWithPath: path)
		guard let enumerator = FileManager.default.enumerator(
			at: url, includingPropertiesForKeys: [.isRegularFileKey, .totalFileAllocatedSizeKey, .fileSizeKey]
		) else { return 0 }
		var total: UInt64 = 0
		for case let fileURL as URL in enumerator {
			let values = try? fileURL.resourceValues(forKeys: [.isRegularFileKey, .totalFileAllocatedSizeKey, .fileSizeKey])
			guard values?.isRegularFile == true else { continue }
			total += UInt64(values?.totalFileAllocatedSize ?? values?.fileSize ?? 0)
		}
		return total
	}

	private func loadDeviceInfo() {
		if state.isLoadingDeviceInfo { return }
		state.isLoadingDeviceInfo = true
		Task {
			let info = await deviceInfoProvider.load()
			state.deviceInfo = info
			state.isLoadingDeviceInfo = false
		}
	}

	private func refreshBugLogPreview() {
		Task {
			let bytes = await bugReports.previewLogBytes()
			state.bugLogPreviewBytes = bytes
		}
	}

	private func validateFolder(_ folder: ReceiveFolder) async {
		state.isValidatingFolder = true
		let status = await fileSystemService.validateReceiveFolder(folder)
		state.folderAccessStatus = status
		state.isValidatingFolder = false
	}
}
