package com.vnidrop.app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.vnidrop.app.core.FolderAccessStatus
import com.vnidrop.app.ui.components.Field
import com.vnidrop.app.ui.components.PrimaryButton
import com.vnidrop.app.ui.components.SecondaryButton
import com.vnidrop.app.ui.icons.AppIcon
import org.jetbrains.compose.resources.stringResource
import vnidrop.shared.generated.resources.Res
import vnidrop.shared.generated.resources.button_choose_folder
import vnidrop.shared.generated.resources.button_reset_default
import vnidrop.shared.generated.resources.field_username
import vnidrop.shared.generated.resources.folder_status_permission_required
import vnidrop.shared.generated.resources.folder_status_unavailable
import vnidrop.shared.generated.resources.folder_status_validating
import vnidrop.shared.generated.resources.folder_status_writable
import vnidrop.shared.generated.resources.preferences_receive_folder_title
import vnidrop.shared.generated.resources.preferences_title

@Composable
internal fun PreferencesSettings(
	state: SettingsState,
	onUsernameChanged: (String) -> Unit,
	onChooseFolder: () -> Unit,
	onResetFolder: () -> Unit,
	onBack: () -> Unit,
	showBack: Boolean,
) {
	Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
		SettingsTopBar(stringResource(Res.string.preferences_title), onBack, showBack)
		Field(state.username, onUsernameChanged, stringResource(Res.string.field_username))
		SettingsGroup {
			SettingsRow(
				icon = AppIcon.Folder,
				title = stringResource(Res.string.preferences_receive_folder_title),
				value = state.receiveFolder?.displayName?.ifBlank { state.receiveFolder.value },
				iconTone = SettingsIconTone.Neutral,
			)
			SettingsDivider()
			SettingsRow(
				icon = AppIcon.Check,
				title = state.folderStatusLabel(),
				iconTone = SettingsIconTone.Neutral,
			)
		}
		if (state.supportsCustomReceiveFolders) {
			Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
				PrimaryButton(stringResource(Res.string.button_choose_folder), onClick = onChooseFolder)
				SecondaryButton(stringResource(Res.string.button_reset_default), onClick = onResetFolder)
			}
		}
	}
}

@Composable
private fun SettingsState.folderStatusLabel(): String = if (isValidatingFolder) {
	stringResource(Res.string.folder_status_validating)
} else {
	when (folderAccessStatus) {
		FolderAccessStatus.Writable -> stringResource(Res.string.folder_status_writable)
		FolderAccessStatus.PermissionRequired -> stringResource(Res.string.folder_status_permission_required)
		FolderAccessStatus.Unavailable -> stringResource(Res.string.folder_status_unavailable)
	}
}
