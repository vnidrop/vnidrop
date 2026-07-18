import Foundation
import Combine

/// Settings sections, ported from `feature/settings/SettingsViewModel.kt`.
enum SettingsSection: Hashable {
	case overview
	case preferences
	case appearance
	case notifications
	case about
	case bugReport

	var titleKey: String {
		switch self {
		case .overview: return "settings_title"
		case .preferences: return "preferences_title"
		case .appearance: return "appearance_title"
		case .notifications: return "notifications_title"
		case .about: return "about_title"
		case .bugReport: return "about_bug_report"
		}
	}
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
		preferences: AppPreferencesRepository,
		notifications: LocalNotificationService,
		messages: UiMessageController,
		bugReports: BugReportService,
		diagnosticsIncluded: Bool = DiagnosticsBuildConfig.included
	) {
		self.environment = environment
		self.deviceInfoProvider = deviceInfoProvider
		self.fileSystemService = fileSystemService
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
					onAction: permission == .denied ? { [weak self] in self?.openNotificationSettings() } : nil
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

	func submitBugReport() {
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
				selectSection(.about)
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
