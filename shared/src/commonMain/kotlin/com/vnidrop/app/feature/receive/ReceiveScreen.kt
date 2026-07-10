package com.vnidrop.app.feature.receive

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.vnidrop.app.core.CoreState
import com.vnidrop.app.core.FolderAccessStatus
import com.vnidrop.app.ui.components.AppCard
import com.vnidrop.app.ui.components.Field
import com.vnidrop.app.ui.components.MetadataRow
import com.vnidrop.app.ui.components.PrimaryButton
import com.vnidrop.app.ui.components.SecondaryButton
import com.vnidrop.app.ui.screens.ProgressSection
import com.vnidrop.app.ui.screens.ScreenHeader
import com.vnidrop.app.ui.screens.TicketInspectionCard
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
import vnidrop.shared.generated.resources.folder_status_writable
import vnidrop.shared.generated.resources.metadata_status
import vnidrop.shared.generated.resources.receive_subtitle
import vnidrop.shared.generated.resources.receive_title
import vnidrop.shared.generated.resources.ticket_card_title

@Composable
fun ReceiveScreen(
	coreState: CoreState,
	state: ReceiveState,
	onTicketChanged: (String) -> Unit,
	onReceiverNameChanged: (String) -> Unit,
	onInspectTicket: () -> Unit,
	onReceive: () -> Unit,
) {
	Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
		ScreenHeader(stringResource(Res.string.receive_title), stringResource(Res.string.receive_subtitle))
		AppCard(title = stringResource(Res.string.ticket_card_title)) {
			Field(state.ticket, onTicketChanged, stringResource(Res.string.field_ticket), minLines = 4)
			MetadataRow(
				stringResource(Res.string.field_output_directory),
				state.receiveFolder?.displayName?.ifBlank { state.outputDirectory } ?: state.outputDirectory,
			)
			MetadataRow(stringResource(Res.string.metadata_status), state.folderAccessStatus.displayName())
			Field(state.receiverName, onReceiverNameChanged, stringResource(Res.string.field_receiver_name))
			Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
				SecondaryButton(
					stringResource(Res.string.button_inspect_ticket),
					onClick = onInspectTicket,
					enabled = state.canInspect(coreState.isInitialized),
				)
				PrimaryButton(
					if (state.isReceiving) stringResource(Res.string.button_receiving) else stringResource(Res.string.button_receive),
					onClick = onReceive,
					enabled = state.canReceive(coreState.isInitialized),
				)
			}
		}
		coreState.lastInspection?.let { TicketInspectionCard(it) }
		ProgressSection(coreState)
	}
}

@Composable
private fun FolderAccessStatus.displayName(): String = when (this) {
	FolderAccessStatus.Writable -> stringResource(Res.string.folder_status_writable)
	FolderAccessStatus.PermissionRequired -> stringResource(Res.string.folder_status_permission_required)
	FolderAccessStatus.Unavailable -> stringResource(Res.string.folder_status_unavailable)
}
