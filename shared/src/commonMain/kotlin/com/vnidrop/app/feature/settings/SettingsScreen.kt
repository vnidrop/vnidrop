package com.vnidrop.app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vnidrop.app.preferences.RelayModeSetting
import com.vnidrop.app.ui.state.WindowClass
import com.vnidrop.app.ui.theme.ThemeMode

@Composable
fun SettingsScreen(
	state: SettingsState,
	windowClass: WindowClass,
	onSectionSelected: (SettingsSection) -> Unit,
	onUsernameChanged: (String) -> Unit,
	onThemeModeChanged: (ThemeMode) -> Unit,
	onChooseFolder: () -> Unit,
	onResetFolder: () -> Unit,
	onNotificationsChanged: (Boolean) -> Unit,
	onOpenNotificationSettings: () -> Unit,
	onRelayModeChanged: (RelayModeSetting) -> Unit,
	onRelayCustomUrlsChanged: (String) -> Unit,
	onApplyRelayCustomUrls: () -> Unit,
	onDiagnosticsChanged: (Boolean) -> Unit,
	onBugWhatChanged: (String) -> Unit,
	onBugExpectedChanged: (String) -> Unit,
	onBugStepsChanged: (String) -> Unit,
	onBugContactChanged: (String) -> Unit,
	onBugIncludeLogsChanged: (Boolean) -> Unit,
	onSubmitBugReport: () -> Unit,
	onDeleteAllTransfers: () -> Unit = {},
) {
	if (windowClass == WindowClass.Desktop) {
		Row(
			modifier = Modifier.fillMaxWidth(),
			horizontalArrangement = Arrangement.spacedBy(24.dp),
		) {
			Column(Modifier.widthIn(min = 280.dp, max = 340.dp)) {
				SettingsOverview(state, onSectionSelected, largeTitle = false)
			}
			Column(Modifier.weight(1f)) {
				SettingsSectionContent(
					state = state,
					section = state.selectedSection.takeUnless { it == SettingsSection.Overview } ?: SettingsSection.Preferences,
					onBack = {},
					showBack = false,
					onSectionSelected = onSectionSelected,
					onUsernameChanged = onUsernameChanged,
					onThemeModeChanged = onThemeModeChanged,
					onChooseFolder = onChooseFolder,
					onResetFolder = onResetFolder,
					onNotificationsChanged = onNotificationsChanged,
					onOpenNotificationSettings = onOpenNotificationSettings,
					onRelayModeChanged = onRelayModeChanged,
					onRelayCustomUrlsChanged = onRelayCustomUrlsChanged,
					onApplyRelayCustomUrls = onApplyRelayCustomUrls,
					onDiagnosticsChanged = onDiagnosticsChanged,
					onBugWhatChanged = onBugWhatChanged,
					onBugExpectedChanged = onBugExpectedChanged,
					onBugStepsChanged = onBugStepsChanged,
					onBugContactChanged = onBugContactChanged,
					onBugIncludeLogsChanged = onBugIncludeLogsChanged,
					onSubmitBugReport = onSubmitBugReport,
					onDeleteAllTransfers = onDeleteAllTransfers,
				)
			}
		}
	} else {
		when (state.selectedSection) {
			SettingsSection.Overview -> SettingsOverview(state, onSectionSelected, largeTitle = true)
			else -> SettingsSectionContent(
				state = state,
				section = state.selectedSection,
				onBack = {
					onSectionSelected(
						if (state.selectedSection == SettingsSection.BugReport) {
							SettingsSection.About
						} else {
							SettingsSection.Overview
						},
					)
				},
				showBack = true,
				onSectionSelected = onSectionSelected,
				onUsernameChanged = onUsernameChanged,
				onThemeModeChanged = onThemeModeChanged,
				onChooseFolder = onChooseFolder,
				onResetFolder = onResetFolder,
				onNotificationsChanged = onNotificationsChanged,
				onOpenNotificationSettings = onOpenNotificationSettings,
				onRelayModeChanged = onRelayModeChanged,
				onRelayCustomUrlsChanged = onRelayCustomUrlsChanged,
				onApplyRelayCustomUrls = onApplyRelayCustomUrls,
				onDiagnosticsChanged = onDiagnosticsChanged,
				onBugWhatChanged = onBugWhatChanged,
				onBugExpectedChanged = onBugExpectedChanged,
				onBugStepsChanged = onBugStepsChanged,
				onBugContactChanged = onBugContactChanged,
				onBugIncludeLogsChanged = onBugIncludeLogsChanged,
				onSubmitBugReport = onSubmitBugReport,
				onDeleteAllTransfers = onDeleteAllTransfers,
			)
		}
	}
}

@Composable
private fun SettingsSectionContent(
	state: SettingsState,
	section: SettingsSection,
	onBack: () -> Unit,
	showBack: Boolean,
	onSectionSelected: (SettingsSection) -> Unit,
	onUsernameChanged: (String) -> Unit,
	onThemeModeChanged: (ThemeMode) -> Unit,
	onChooseFolder: () -> Unit,
	onResetFolder: () -> Unit,
	onNotificationsChanged: (Boolean) -> Unit,
	onOpenNotificationSettings: () -> Unit,
	onRelayModeChanged: (RelayModeSetting) -> Unit,
	onRelayCustomUrlsChanged: (String) -> Unit,
	onApplyRelayCustomUrls: () -> Unit,
	onDiagnosticsChanged: (Boolean) -> Unit,
	onBugWhatChanged: (String) -> Unit,
	onBugExpectedChanged: (String) -> Unit,
	onBugStepsChanged: (String) -> Unit,
	onBugContactChanged: (String) -> Unit,
	onBugIncludeLogsChanged: (Boolean) -> Unit,
	onSubmitBugReport: () -> Unit,
	onDeleteAllTransfers: () -> Unit,
) {
	when (section) {
		SettingsSection.Overview -> Unit
		SettingsSection.Preferences -> PreferencesSettings(state, onUsernameChanged, onChooseFolder, onResetFolder, onBack, showBack)
		SettingsSection.Appearance -> AppearanceSettings(state.themeMode, onThemeModeChanged, onBack, showBack)
		SettingsSection.Notifications -> NotificationSettings(state, onNotificationsChanged, onOpenNotificationSettings, onBack, showBack)
		SettingsSection.Relay -> RelaySettings(
			state = state,
			onModeChanged = onRelayModeChanged,
			onCustomUrlsChanged = onRelayCustomUrlsChanged,
			onApplyCustomUrls = onApplyRelayCustomUrls,
			onBack = onBack,
			showBack = showBack,
		)
		SettingsSection.Storage -> StorageSettings(state, onDeleteAllTransfers, onBack, showBack)
		SettingsSection.About -> AboutSettings(
			state = state,
			onDiagnosticsChanged = onDiagnosticsChanged,
			onReportBug = { onSectionSelected(SettingsSection.BugReport) },
			onBack = onBack,
			showBack = showBack,
		)
		SettingsSection.BugReport -> BugReportSettings(
			state = state,
			onWhatChanged = onBugWhatChanged,
			onExpectedChanged = onBugExpectedChanged,
			onStepsChanged = onBugStepsChanged,
			onContactChanged = onBugContactChanged,
			onIncludeLogsChanged = onBugIncludeLogsChanged,
			onSubmit = onSubmitBugReport,
			onBack = onBack,
			showBack = showBack,
		)
	}
}
