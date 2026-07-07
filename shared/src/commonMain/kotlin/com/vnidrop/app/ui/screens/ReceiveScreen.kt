package com.vnidrop.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.vnidrop.app.VniDropAppEvent
import com.vnidrop.app.core.CoreUiState
import com.vnidrop.app.core.FolderAccessStatus
import com.vnidrop.app.ui.components.AppCard
import com.vnidrop.app.ui.components.Field
import com.vnidrop.app.ui.components.MetadataRow
import com.vnidrop.app.ui.components.PrimaryButton
import com.vnidrop.app.ui.components.SecondaryButton
import com.vnidrop.app.ui.state.PreferencesUiState
import com.vnidrop.app.ui.state.ReceiveUiState
import org.jetbrains.compose.resources.stringResource
import vnidrop.shared.generated.resources.Res
import vnidrop.shared.generated.resources.button_inspect_ticket
import vnidrop.shared.generated.resources.button_receive
import vnidrop.shared.generated.resources.button_receiving
import vnidrop.shared.generated.resources.field_output_directory
import vnidrop.shared.generated.resources.field_receiver_name
import vnidrop.shared.generated.resources.field_ticket
import vnidrop.shared.generated.resources.folder_status_permission_required
import vnidrop.shared.generated.resources.folder_status_unavailable
import vnidrop.shared.generated.resources.folder_status_validating
import vnidrop.shared.generated.resources.folder_status_writable
import vnidrop.shared.generated.resources.metadata_status
import vnidrop.shared.generated.resources.receive_subtitle
import vnidrop.shared.generated.resources.receive_title
import vnidrop.shared.generated.resources.ticket_card_title

@Composable
fun ReceiveScreen(
	coreState: CoreUiState,
	receiveState: ReceiveUiState,
	preferencesState: PreferencesUiState,
	onEvent: (VniDropAppEvent) -> Unit,
) {
	Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
		ScreenHeader(stringResource(Res.string.receive_title), stringResource(Res.string.receive_subtitle))
		ErrorSection(coreState)
		AppCard(title = stringResource(Res.string.ticket_card_title)) {
			Field(
				value = receiveState.ticket,
				onValueChange = { onEvent(VniDropAppEvent.ReceiveTicketChanged(it)) },
				label = stringResource(Res.string.field_ticket),
				minLines = 4,
			)
			MetadataRow(
				label = stringResource(Res.string.field_output_directory),
				value = preferencesState.receiveFolder.displayName.ifBlank { preferencesState.receiveFolder.value },
			)
			MetadataRow(
				label = stringResource(Res.string.metadata_status),
				value = preferencesState.folderAccessStatus.displayName(preferencesState.isValidatingFolder),
			)
			Field(
				value = receiveState.receiverName,
				onValueChange = { onEvent(VniDropAppEvent.ReceiverNameChanged(it)) },
				label = stringResource(Res.string.field_receiver_name),
			)
			Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
				SecondaryButton(
					text = stringResource(Res.string.button_inspect_ticket),
					onClick = { onEvent(VniDropAppEvent.InspectTicketClicked) },
					enabled = receiveState.canInspect(coreState.isInitialized),
				)
				PrimaryButton(
					text = if (receiveState.isReceiving) stringResource(Res.string.button_receiving) else stringResource(Res.string.button_receive),
					onClick = { onEvent(VniDropAppEvent.ReceiveClicked) },
					enabled = receiveState.canReceive(coreState.isInitialized) && preferencesState.canReceiveIntoFolder,
				)
			}
		}
		coreState.lastInspection?.let { TicketInspectionCard(it) }
		ProgressSection(coreState)
	}
}

@Composable
private fun FolderAccessStatus.displayName(isValidating: Boolean): String =
	if (isValidating) {
		stringResource(Res.string.folder_status_validating)
	} else {
		when (this) {
			FolderAccessStatus.Writable -> stringResource(Res.string.folder_status_writable)
			FolderAccessStatus.PermissionRequired -> stringResource(Res.string.folder_status_permission_required)
			FolderAccessStatus.Unavailable -> stringResource(Res.string.folder_status_unavailable)
		}
	}
