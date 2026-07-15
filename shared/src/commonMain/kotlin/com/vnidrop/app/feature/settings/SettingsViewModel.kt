package com.vnidrop.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vnidrop.app.DeviceInfo
import com.vnidrop.app.DeviceInfoProvider
import com.vnidrop.app.PlatformEnvironment
import com.vnidrop.app.core.FileSystemService
import com.vnidrop.app.core.FolderAccessStatus
import com.vnidrop.app.core.ReceiveFolder
import com.vnidrop.app.diagnostics.BugReportDraft
import com.vnidrop.app.diagnostics.BugReportService
import com.vnidrop.app.diagnostics.DiagnosticsBuildConfig
import com.vnidrop.app.diagnostics.DiagnosticsCoordinator
import com.vnidrop.app.notifications.LocalNotificationService
import com.vnidrop.app.notifications.NotificationPermission
import com.vnidrop.app.preferences.PreferencesRepository
import com.vnidrop.app.ui.feedback.UiMessage
import com.vnidrop.app.ui.feedback.UiMessageController
import com.vnidrop.app.ui.feedback.UiMessageTone
import com.vnidrop.app.ui.feedback.UiText
import com.vnidrop.app.ui.theme.ThemeMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import vnidrop.shared.generated.resources.Res
import vnidrop.shared.generated.resources.bug_report_missing_expected
import vnidrop.shared.generated.resources.bug_report_missing_what
import vnidrop.shared.generated.resources.bug_report_submit_failed
import vnidrop.shared.generated.resources.bug_report_submitted
import vnidrop.shared.generated.resources.button_open_settings
import vnidrop.shared.generated.resources.diagnostics_disabled_message
import vnidrop.shared.generated.resources.diagnostics_enabled_message
import vnidrop.shared.generated.resources.notifications_enabled_message
import vnidrop.shared.generated.resources.notifications_permission_denied
import vnidrop.shared.generated.resources.notifications_settings_open_failed
import vnidrop.shared.generated.resources.notifications_unsupported

enum class SettingsSection {
	Overview,
	Preferences,
	Appearance,
	Notifications,
	About,
	BugReport,
}

data class SettingsState(
	val selectedSection: SettingsSection = SettingsSection.Overview,
	val username: String = "",
	val receiveFolder: ReceiveFolder? = null,
	val folderAccessStatus: FolderAccessStatus = FolderAccessStatus.Unavailable,
	val isValidatingFolder: Boolean = false,
	val themeMode: ThemeMode = ThemeMode.System,
	val notificationsEnabled: Boolean = false,
	val notificationPermission: NotificationPermission = NotificationPermission.NotDetermined,
	val diagnosticsEnabled: Boolean = false,
	val deviceInfo: DeviceInfo? = null,
	val appVersion: String = "",
	val isLoadingDeviceInfo: Boolean = false,
	val bugWhatHappened: String = "",
	val bugExpected: String = "",
	val bugSteps: String = "",
	val bugContact: String = "",
	val bugIncludeLogs: Boolean = true,
	val isSubmittingBugReport: Boolean = false,
	val bugLogPreviewBytes: Int = 0,
)

sealed interface SettingsEffect {
	data object OpenReceiveFolderPicker : SettingsEffect
}

class SettingsViewModel(
	private val environment: PlatformEnvironment,
	private val deviceInfoProvider: DeviceInfoProvider,
	private val fileSystemService: FileSystemService,
	private val preferencesRepository: PreferencesRepository,
	private val notifications: LocalNotificationService,
	private val messages: UiMessageController,
	private val bugReports: BugReportService,
	private val diagnostics: DiagnosticsCoordinator? = null,
) : ViewModel() {
	private val _state = MutableStateFlow(SettingsState(appVersion = environment.appVersion))
	val state: StateFlow<SettingsState> = _state.asStateFlow()

	private val effects = Channel<SettingsEffect>(Channel.BUFFERED)
	val effectFlow = effects.receiveAsFlow()
	private var enableNotificationsAfterSettings = false
	private var usernamePersistJob: Job? = null

	init {
		viewModelScope.launch {
			preferencesRepository.preferences.collect { preferences ->
				val previousFolder = _state.value.receiveFolder
				// While the user is typing, keep the in-progress value. DataStore
				// echoes can race keystrokes and trim trailing spaces mid-edit.
				val editingUsername = usernamePersistJob?.isActive == true
				_state.update { current ->
					current.copy(
						username = if (editingUsername) current.username else preferences.username,
						receiveFolder = preferences.receiveFolder,
						themeMode = preferences.themeMode,
						notificationsEnabled = preferences.notificationsEnabled,
						diagnosticsEnabled = preferences.diagnosticsEnabled,
					)
				}
				if (preferences.receiveFolder != previousFolder) {
					validateFolder(preferences.receiveFolder)
				}
			}
		}
		refreshNotificationPermission()
		loadDeviceInfo()
	}

	fun selectSection(section: SettingsSection) {
		_state.update { it.copy(selectedSection = section) }
		when (section) {
			SettingsSection.About, SettingsSection.BugReport -> {
				loadDeviceInfo()
				if (section == SettingsSection.BugReport) refreshBugLogPreview()
			}
			else -> Unit
		}
	}

	fun setUsername(value: String) {
		_state.update { it.copy(username = value) }
		usernamePersistJob?.cancel()
		usernamePersistJob = viewModelScope.launch {
			delay(UsernamePersistDebounceMs)
			preferencesRepository.setUsername(value)
		}
	}

	fun setThemeMode(mode: ThemeMode) {
		viewModelScope.launch { preferencesRepository.setThemeMode(mode) }
	}

	fun chooseReceiveFolder() {
		viewModelScope.launch { effects.send(SettingsEffect.OpenReceiveFolderPicker) }
	}

	fun onReceiveFolderPicked(folder: ReceiveFolder) {
		viewModelScope.launch { preferencesRepository.setReceiveFolder(folder) }
	}

	fun onReceiveFolderPickFailed(reason: String) = messages.error(IllegalStateException(reason))

	fun resetReceiveFolder() {
		viewModelScope.launch { preferencesRepository.resetReceiveFolder() }
	}

	fun setNotificationsEnabled(enabled: Boolean) {
		viewModelScope.launch {
			if (!enabled) {
				preferencesRepository.setNotificationsEnabled(false)
				notifications.cancelAll()
				return@launch
			}

			val permission = notifications.requestPermission()
			_state.update { it.copy(notificationPermission = permission) }
			if (permission == NotificationPermission.Granted) {
				enableNotifications()
			} else {
				preferencesRepository.setNotificationsEnabled(false)
				messages.show(
					UiMessage(
						UiText.Resource(
							if (permission == NotificationPermission.Unsupported) Res.string.notifications_unsupported
							else Res.string.notifications_permission_denied,
						),
						UiMessageTone.Warning,
						actionLabel = if (permission == NotificationPermission.Denied) {
							UiText.Resource(Res.string.button_open_settings)
						} else {
							null
						},
						onAction = if (permission == NotificationPermission.Denied) ::openNotificationSettings else null,
					),
				)
			}
		}
	}

	fun setDiagnosticsEnabled(enabled: Boolean) {
		if (!DiagnosticsBuildConfig.INCLUDED) return
		viewModelScope.launch {
			preferencesRepository.setDiagnosticsEnabled(enabled)
			diagnostics?.record(
				if (enabled) "diagnostics_enabled" else "diagnostics_disabled",
			)
			messages.show(
				UiMessage(
					UiText.Resource(
						if (enabled) Res.string.diagnostics_enabled_message
						else Res.string.diagnostics_disabled_message,
					),
					UiMessageTone.Success,
				),
			)
		}
	}

	fun setBugWhatHappened(value: String) = _state.update { it.copy(bugWhatHappened = value) }
	fun setBugExpected(value: String) = _state.update { it.copy(bugExpected = value) }
	fun setBugSteps(value: String) = _state.update { it.copy(bugSteps = value) }
	fun setBugContact(value: String) = _state.update { it.copy(bugContact = value) }
	fun setBugIncludeLogs(value: Boolean) = _state.update { it.copy(bugIncludeLogs = value) }

	fun submitBugReport() {
		if (_state.value.isSubmittingBugReport) return
		viewModelScope.launch {
			val snapshot = _state.value
			val what = snapshot.bugWhatHappened.trim()
			val expected = snapshot.bugExpected.trim()
			if (what.isEmpty()) {
				messages.show(UiMessage(UiText.Resource(Res.string.bug_report_missing_what), UiMessageTone.Warning))
				return@launch
			}
			if (expected.isEmpty()) {
				messages.show(UiMessage(UiText.Resource(Res.string.bug_report_missing_expected), UiMessageTone.Warning))
				return@launch
			}
			_state.update { it.copy(isSubmittingBugReport = true) }
			try {
				val result = bugReports.submit(
					BugReportDraft(
						whatHappened = what,
						expected = expected,
						steps = snapshot.bugSteps,
						contact = snapshot.bugContact,
						includeLogs = snapshot.bugIncludeLogs,
					),
					deviceInfo = snapshot.deviceInfo,
				)
				result.fold(
					onSuccess = {
						diagnostics?.record("bug_report_submitted")
						_state.update {
							it.copy(
								isSubmittingBugReport = false,
								bugWhatHappened = "",
								bugExpected = "",
								bugSteps = "",
								bugContact = "",
								bugIncludeLogs = true,
							)
						}
						messages.show(
							UiMessage(UiText.Resource(Res.string.bug_report_submitted), UiMessageTone.Success),
						)
						selectSection(SettingsSection.About)
					},
					onFailure = {
						_state.update { it.copy(isSubmittingBugReport = false) }
						messages.show(
							UiMessage(UiText.Resource(Res.string.bug_report_submit_failed), UiMessageTone.Error),
						)
					},
				)
			} catch (error: Throwable) {
				if (error is CancellationException) throw error
				_state.update { it.copy(isSubmittingBugReport = false) }
				messages.show(UiMessage(UiText.Resource(Res.string.bug_report_submit_failed), UiMessageTone.Error))
			}
		}
	}

	fun openNotificationSettings() {
		viewModelScope.launch {
			enableNotificationsAfterSettings = true
			notifications.openSettings().onFailure {
				enableNotificationsAfterSettings = false
				messages.show(
					UiMessage(UiText.Resource(Res.string.notifications_settings_open_failed), UiMessageTone.Error),
				)
			}
		}
	}

	fun refreshNotificationPermission() {
		viewModelScope.launch {
			val permission = notifications.refreshPermission()
			_state.update { it.copy(notificationPermission = permission) }
			if (enableNotificationsAfterSettings) {
				enableNotificationsAfterSettings = false
				if (permission == NotificationPermission.Granted) enableNotifications()
			} else if (permission != NotificationPermission.Granted && _state.value.notificationsEnabled) {
				preferencesRepository.setNotificationsEnabled(false)
				notifications.cancelAll()
			}
		}
	}

	private suspend fun enableNotifications() {
		preferencesRepository.setNotificationsEnabled(true)
		messages.show(
			UiMessage(UiText.Resource(Res.string.notifications_enabled_message), UiMessageTone.Success),
		)
	}

	private fun loadDeviceInfo() {
		if (_state.value.isLoadingDeviceInfo) return
		viewModelScope.launch {
			_state.update { it.copy(isLoadingDeviceInfo = true) }
			try {
				val info = deviceInfoProvider.load()
				_state.update { it.copy(deviceInfo = info, isLoadingDeviceInfo = false) }
			} catch (error: Throwable) {
				if (error is CancellationException) throw error
				_state.update { it.copy(isLoadingDeviceInfo = false) }
				messages.error(
					if (error.message.isNullOrBlank()) {
						IllegalStateException("Could not load device information.")
					} else {
						error
					},
				)
			}
		}
	}

	private fun refreshBugLogPreview() {
		viewModelScope.launch {
			val bytes = runCatching { bugReports.previewLogBytes() }.getOrDefault(0)
			_state.update { it.copy(bugLogPreviewBytes = bytes) }
		}
	}

	private suspend fun validateFolder(folder: ReceiveFolder) {
		_state.update { it.copy(isValidatingFolder = true) }
		val status = fileSystemService.validateReceiveFolder(folder)
		_state.update { it.copy(folderAccessStatus = status, isValidatingFolder = false) }
	}

	private companion object {
		const val UsernamePersistDebounceMs = 350L
	}
}
