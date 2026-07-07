package com.vnidrop.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vnidrop.app.VniDropAppEvent
import com.vnidrop.app.core.CoreUiState
import com.vnidrop.app.ui.components.AppCard
import com.vnidrop.app.ui.components.Field
import com.vnidrop.app.ui.components.MetadataRow
import com.vnidrop.app.ui.components.PillTone
import com.vnidrop.app.ui.components.PrimaryButton
import com.vnidrop.app.ui.components.SecondaryButton
import com.vnidrop.app.ui.components.StatusPill
import com.vnidrop.app.ui.state.SendUiState
import com.vnidrop.app.ui.state.formatBytes
import org.jetbrains.compose.resources.stringResource
import uniffi.vnidrop.ReceiverRequest
import uniffi.vnidrop.ShareResult
import vnidrop.shared.generated.resources.Res
import vnidrop.shared.generated.resources.button_approve
import vnidrop.shared.generated.resources.button_clear
import vnidrop.shared.generated.resources.button_copy
import vnidrop.shared.generated.resources.button_create_share_ticket
import vnidrop.shared.generated.resources.button_creating_ticket
import vnidrop.shared.generated.resources.button_refresh
import vnidrop.shared.generated.resources.button_refuse
import vnidrop.shared.generated.resources.button_select_file
import vnidrop.shared.generated.resources.button_use_locally
import vnidrop.shared.generated.resources.field_sender_name
import vnidrop.shared.generated.resources.field_transfer_name
import vnidrop.shared.generated.resources.metadata_name
import vnidrop.shared.generated.resources.metadata_size
import vnidrop.shared.generated.resources.metadata_source
import vnidrop.shared.generated.resources.metadata_transfer
import vnidrop.shared.generated.resources.receiver_requests_title
import vnidrop.shared.generated.resources.send_source_empty
import vnidrop.shared.generated.resources.send_subtitle
import vnidrop.shared.generated.resources.send_title
import vnidrop.shared.generated.resources.share_ticket_title
import vnidrop.shared.generated.resources.source_title
import vnidrop.shared.generated.resources.transfer_details_title

@Composable
fun SendScreen(
	coreState: CoreUiState,
	sendState: SendUiState,
	onEvent: (VniDropAppEvent) -> Unit,
) {
	Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
		ScreenHeader(stringResource(Res.string.send_title), stringResource(Res.string.send_subtitle))
		ErrorSection(coreState)
		SendSourceCard(
			sendState = sendState,
			onEvent = onEvent,
		)
		SendDetailsCard(
			coreState = coreState,
			sendState = sendState,
			onEvent = onEvent,
		)
		coreState.lastShare?.let { share ->
			ShareResultCard(
				share = share,
				requests = coreState.receiverRequests,
				onEvent = onEvent,
			)
		}
		ProgressSection(coreState)
	}
}

@Composable
private fun SendSourceCard(
	sendState: SendUiState,
	onEvent: (VniDropAppEvent) -> Unit,
) {
	AppCard(title = stringResource(Res.string.source_title)) {
		if (sendState.selectedSource.isBlank()) {
			EmptyText(stringResource(Res.string.send_source_empty))
		} else {
			MetadataRow(stringResource(Res.string.metadata_name), sendState.selectedDisplayName.ifBlank { sendState.selectedSource.substringAfterLast('/') })
			MetadataRow(stringResource(Res.string.metadata_source), sendState.selectedSource)
		}
		Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
			PrimaryButton(
				text = stringResource(Res.string.button_select_file),
				onClick = { onEvent(VniDropAppEvent.SelectFileClicked) },
			)
			SecondaryButton(
				text = stringResource(Res.string.button_clear),
				onClick = { onEvent(VniDropAppEvent.ClearSelectedSourceClicked) },
				enabled = sendState.hasSelectedSource,
			)
		}
	}
}

@Composable
private fun SendDetailsCard(
	coreState: CoreUiState,
	sendState: SendUiState,
	onEvent: (VniDropAppEvent) -> Unit,
) {
	AppCard(title = stringResource(Res.string.transfer_details_title)) {
		Field(
			value = sendState.transferName,
			onValueChange = { onEvent(VniDropAppEvent.TransferNameChanged(it)) },
			label = stringResource(Res.string.field_transfer_name),
		)
		Field(
			value = sendState.senderName,
			onValueChange = { onEvent(VniDropAppEvent.SenderNameChanged(it)) },
			label = stringResource(Res.string.field_sender_name),
		)
		PrimaryButton(
			text = if (sendState.isSharing) stringResource(Res.string.button_creating_ticket) else stringResource(Res.string.button_create_share_ticket),
			onClick = { onEvent(VniDropAppEvent.CreateShareClicked) },
			enabled = sendState.canCreateShare(coreState.isInitialized),
		)
	}
}

@Composable
private fun ShareResultCard(
	share: ShareResult,
	requests: List<ReceiverRequest>,
	onEvent: (VniDropAppEvent) -> Unit,
) {
	AppCard(title = stringResource(Res.string.share_ticket_title), trailing = {
		StatusPill("${share.fileCount} file${if (share.fileCount == 1UL) "" else "s"}", tone = PillTone.Brand)
	}) {
		MetadataRow(stringResource(Res.string.metadata_transfer), share.transferName)
		MetadataRow(stringResource(Res.string.metadata_size), formatBytes(share.totalSize))
		TicketText(share.ticket)
		Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
			PrimaryButton(
				text = stringResource(Res.string.button_copy),
				onClick = { onEvent(VniDropAppEvent.CopyTicketClicked(share.ticket)) },
			)
			SecondaryButton(
				text = stringResource(Res.string.button_use_locally),
				onClick = { onEvent(VniDropAppEvent.UseTicketLocallyClicked(share.ticket)) },
			)
			SecondaryButton(
				text = stringResource(Res.string.button_refresh),
				onClick = { onEvent(VniDropAppEvent.RefreshReceiverRequestsClicked(share.transferId)) },
			)
		}
		if (requests.isNotEmpty()) {
			SectionDivider()
			Text(stringResource(Res.string.receiver_requests_title), fontWeight = FontWeight.SemiBold)
			ReceiverRequestList(
				requests = requests,
				onRespondRequest = { requestId, accepted ->
					onEvent(VniDropAppEvent.RespondReceiverRequestClicked(requestId, accepted))
				},
			)
		}
	}
}
