package com.vnidrop.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.vnidrop.app.core.CoreUiState
import com.vnidrop.app.ui.components.AppCard
import com.vnidrop.app.ui.components.Field
import com.vnidrop.app.ui.components.PrimaryButton
import com.vnidrop.app.ui.components.SecondaryButton
import com.vnidrop.app.ui.state.ReceiveUiState
import org.jetbrains.compose.resources.stringResource
import vnidrop.shared.generated.resources.Res
import vnidrop.shared.generated.resources.button_inspect_ticket
import vnidrop.shared.generated.resources.button_receive
import vnidrop.shared.generated.resources.button_receiving
import vnidrop.shared.generated.resources.field_output_directory
import vnidrop.shared.generated.resources.field_receiver_name
import vnidrop.shared.generated.resources.field_ticket
import vnidrop.shared.generated.resources.receive_subtitle
import vnidrop.shared.generated.resources.receive_title
import vnidrop.shared.generated.resources.ticket_card_title

@Composable
fun ReceiveScreen(
	coreState: CoreUiState,
	receiveState: ReceiveUiState,
	onReceiveStateChange: (ReceiveUiState) -> Unit,
	onInspect: () -> Unit,
	onReceive: () -> Unit,
) {
	Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
		ScreenHeader(stringResource(Res.string.receive_title), stringResource(Res.string.receive_subtitle))
		ErrorSection(coreState)
		AppCard(title = stringResource(Res.string.ticket_card_title)) {
			Field(
				value = receiveState.ticket,
				onValueChange = { onReceiveStateChange(receiveState.copy(ticket = it)) },
				label = stringResource(Res.string.field_ticket),
				minLines = 4,
			)
			Field(
				value = receiveState.outputDirectory,
				onValueChange = { onReceiveStateChange(receiveState.copy(outputDirectory = it)) },
				label = stringResource(Res.string.field_output_directory),
			)
			Field(
				value = receiveState.receiverName,
				onValueChange = { onReceiveStateChange(receiveState.copy(receiverName = it)) },
				label = stringResource(Res.string.field_receiver_name),
			)
			Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
				SecondaryButton(
					text = stringResource(Res.string.button_inspect_ticket),
					onClick = onInspect,
					enabled = coreState.isInitialized && receiveState.ticket.isNotBlank(),
				)
				PrimaryButton(
					text = if (receiveState.isReceiving) stringResource(Res.string.button_receiving) else stringResource(Res.string.button_receive),
					onClick = onReceive,
					enabled = coreState.isInitialized &&
						receiveState.ticket.isNotBlank() &&
						receiveState.outputDirectory.isNotBlank() &&
						!receiveState.isReceiving,
				)
			}
		}
		coreState.lastInspection?.let { TicketInspectionCard(it) }
		ProgressSection(coreState)
	}
}
