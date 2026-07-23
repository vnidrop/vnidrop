package com.vnidrop.app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
				StorageRow(
					title = stringResource(Res.string.storage_calculating),
					trailing = { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) },
				)
			}
		} else {
			SettingsGroup {
				StorageRow(stringResource(Res.string.storage_received_files), storage.receivedBytes)
				SettingsDivider(startPadding = 16.dp)
				StorageRow(stringResource(Res.string.storage_transfer_data), storage.transferCacheBytes)
				SettingsDivider(startPadding = 16.dp)
				StorageRow(stringResource(Res.string.storage_app_data), storage.appDataBytes)
				SettingsDivider(startPadding = 16.dp)
				StorageRow(stringResource(Res.string.storage_temporary), storage.temporaryBytes)
				SettingsDivider(startPadding = 16.dp)
				StorageRow(
					title = stringResource(Res.string.storage_total),
					bytes = storage.deviceImpactBytes,
					emphasized = true,
				)
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
private fun StorageRow(
	title: String,
	bytes: ULong? = null,
	emphasized: Boolean = false,
	trailing: @Composable (() -> Unit)? = null,
) {
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.heightIn(min = 52.dp)
			.padding(horizontal = 16.dp, vertical = 10.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		Text(
			title,
			modifier = Modifier.weight(1f),
			style = MaterialTheme.typography.bodyLarge,
			fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Normal,
		)
		if (bytes != null) {
			Text(
				formatBytes(bytes),
				color = LocalVniDropColors.current.foregroundLighter,
				style = MaterialTheme.typography.bodyMedium,
				fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Normal,
			)
		}
		trailing?.invoke()
	}
}
