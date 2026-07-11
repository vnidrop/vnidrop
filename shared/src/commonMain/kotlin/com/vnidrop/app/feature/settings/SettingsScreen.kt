package com.vnidrop.app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
					onUsernameChanged = onUsernameChanged,
					onThemeModeChanged = onThemeModeChanged,
					onChooseFolder = onChooseFolder,
					onResetFolder = onResetFolder,
					onNotificationsChanged = onNotificationsChanged,
					onOpenNotificationSettings = onOpenNotificationSettings,
				)
			}
		}
	} else {
		when (state.selectedSection) {
			SettingsSection.Overview -> SettingsOverview(state, onSectionSelected, largeTitle = true)
			else -> SettingsSectionContent(
				state = state,
				section = state.selectedSection,
				onBack = { onSectionSelected(SettingsSection.Overview) },
				showBack = true,
				onUsernameChanged = onUsernameChanged,
				onThemeModeChanged = onThemeModeChanged,
				onChooseFolder = onChooseFolder,
				onResetFolder = onResetFolder,
				onNotificationsChanged = onNotificationsChanged,
				onOpenNotificationSettings = onOpenNotificationSettings,
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
	onUsernameChanged: (String) -> Unit,
	onThemeModeChanged: (ThemeMode) -> Unit,
	onChooseFolder: () -> Unit,
	onResetFolder: () -> Unit,
	onNotificationsChanged: (Boolean) -> Unit,
	onOpenNotificationSettings: () -> Unit,
) {
	when (section) {
		SettingsSection.Overview -> Unit
		SettingsSection.Preferences -> PreferencesSettings(state, onUsernameChanged, onChooseFolder, onResetFolder, onBack, showBack)
		SettingsSection.Appearance -> AppearanceSettings(state.themeMode, onThemeModeChanged, onBack, showBack)
		SettingsSection.Notifications -> NotificationSettings(state, onNotificationsChanged, onOpenNotificationSettings, onBack, showBack)
		SettingsSection.About -> AboutSettings(state, onBack, showBack)
	}
}
