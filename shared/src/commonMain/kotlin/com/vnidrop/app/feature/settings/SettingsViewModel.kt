package com.vnidrop.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vnidrop.app.DeviceInfo
import com.vnidrop.app.DeviceInfoProvider
import com.vnidrop.app.PlatformEnvironment
import com.vnidrop.app.core.FileSystemService
import com.vnidrop.app.core.FolderAccessStatus
import com.vnidrop.app.core.ReceiveFolder
import com.vnidrop.app.notifications.LocalNotificationService
import com.vnidrop.app.notifications.NotificationPermission
import com.vnidrop.app.preferences.PreferencesRepository
import com.vnidrop.app.ui.feedback.UiMessage
import com.vnidrop.app.ui.feedback.UiMessageController
import com.vnidrop.app.ui.feedback.UiMessageTone
import com.vnidrop.app.ui.feedback.UiText
import com.vnidrop.app.ui.theme.ThemeMode
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import vnidrop.shared.generated.resources.Res
import vnidrop.shared.generated.resources.button_open_settings
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
	val deviceInfo: DeviceInfo? = null,
	val appVersion: String = "",
	val isLoadingDeviceInfo: Boolean = false,
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
) : ViewModel() {
	private val _state = MutableStateFlow(SettingsState(appVersion = environment.appVersion))
	val state: StateFlow<SettingsState> = _state.asStateFlow()

	private val effects = Channel<SettingsEffect>(Channel.BUFFERED)
	val effectFlow = effects.receiveAsFlow()
	private var enableNotificationsAfterSettings = false

	init {
		viewModelScope.launch {
			preferencesRepository.preferences.collect { preferences ->
				_state.update {
					it.copy(
						username = preferences.username,
						receiveFolder = preferences.receiveFolder,
						themeMode = preferences.themeMode,
						notificationsEnabled = preferences.notificationsEnabled,
					)
				}
				validateFolder(preferences.receiveFolder)
			}
		}
		refreshNotificationPermission()
		loadDeviceInfo()
	}

	fun selectSection(section: SettingsSection) {
		_state.update { it.copy(selectedSection = section) }
		if (section == SettingsSection.About) loadDeviceInfo()
	}

	fun setUsername(value: String) {
		viewModelScope.launch { preferencesRepository.setUsername(value) }
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
				messages.error(error, "Could not load device information.")
			}
		}
	}

	private suspend fun validateFolder(folder: ReceiveFolder) {
		_state.update { it.copy(isValidatingFolder = true) }
		val status = fileSystemService.validateReceiveFolder(folder)
		_state.update { it.copy(folderAccessStatus = status, isValidatingFolder = false) }
	}
}
