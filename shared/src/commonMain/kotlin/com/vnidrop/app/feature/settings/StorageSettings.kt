package com.vnidrop.app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.vnidrop.app.ui.icons.AppIcon
import com.vnidrop.app.ui.state.formatBytes
import com.vnidrop.app.ui.theme.LocalVniDropColors
import org.jetbrains.compose.resources.stringResource
import vnidrop.shared.generated.resources.Res
import vnidrop.shared.generated.resources.storage_app_data
import vnidrop.shared.generated.resources.storage_calculating
import vnidrop.shared.generated.resources.storage_delete_transfers
import vnidrop.shared.generated.resources.storage_deleting
import vnidrop.shared.generated.resources.storage_total
import vnidrop.shared.generated.resources.storage_received_files
import vnidrop.shared.generated.resources.storage_temporary
import vnidrop.shared.generated.resources.storage_title
import vnidrop.shared.generated.resources.storage_transfer_data
import vnidrop.shared.generated.resources.storage_footer

@Composable
internal fun StorageSettings(
	state: SettingsState,
	onDeleteAllTransfers: () -> Unit,
	onBack: () -> Unit,
	showBack: Boolean,
) {
	Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
		SettingsTopBar(stringResource(Res.string.storage_title), onBack, showBack)
		val storage = state.storage
		if (storage == null || state.isCalculatingStorage) {
			SettingsGroup {
				SettingsRow(
					icon = AppIcon.Storage,
					title = stringResource(Res.string.storage_calculating),
					trailing = { CircularProgressIndicator() },
				)
			}
		} else {
			SettingsGroup {
				StorageRow(AppIcon.TransferData, stringResource(Res.string.storage_transfer_data), storage.transferCacheBytes)
				SettingsDivider()
				StorageRow(AppIcon.Storage, stringResource(Res.string.storage_app_data), storage.appDataBytes)
				SettingsDivider()
				StorageRow(AppIcon.Temporary, stringResource(Res.string.storage_temporary), storage.temporaryBytes)
				SettingsDivider()
				SettingsRow(
					icon = AppIcon.Folder,
					title = stringResource(Res.string.storage_received_files),
					value = formatBytes(storage.receivedBytes),
				)
				SettingsDivider()
				StorageRow(AppIcon.TotalStorage, stringResource(Res.string.storage_total), storage.deviceImpactBytes)
			}
		}
		Button(
			onClick = onDeleteAllTransfers,
			enabled = !state.isDeletingTransfers,
			colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
		) {
			Text(stringResource(if (state.isDeletingTransfers) Res.string.storage_deleting else Res.string.storage_delete_transfers))
		}
		Text(
			stringResource(Res.string.storage_footer),
			style = MaterialTheme.typography.bodySmall,
			color = LocalVniDropColors.current.foregroundLighter,
		)
	}
}

@Composable
private fun StorageRow(icon: AppIcon, title: String, bytes: ULong) {
	SettingsRow(icon = icon, title = title, value = formatBytes(bytes))
}
