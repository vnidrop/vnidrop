package com.vnidrop.app.feature.send

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.vnidrop.app.core.CoreState
import com.vnidrop.app.core.Share
import com.vnidrop.app.ui.components.AppCard
import com.vnidrop.app.ui.components.Field
import com.vnidrop.app.ui.components.MetadataRow
import com.vnidrop.app.ui.components.PillTone
import com.vnidrop.app.ui.components.PrimaryButton
import com.vnidrop.app.ui.components.SecondaryButton
import com.vnidrop.app.ui.components.StatusPill
import com.vnidrop.app.ui.screens.EmptyText
import com.vnidrop.app.ui.screens.ProgressSection
import com.vnidrop.app.ui.screens.ScreenHeader
import com.vnidrop.app.ui.screens.SendEmptyState
import com.vnidrop.app.ui.screens.TicketText
import com.vnidrop.app.ui.screens.shouldShowEmptyState
import com.vnidrop.app.ui.state.WindowClass
import com.vnidrop.app.ui.state.formatBytes
import org.jetbrains.compose.resources.stringResource
import vnidrop.shared.generated.resources.Res
import vnidrop.shared.generated.resources.button_clear
import vnidrop.shared.generated.resources.button_copy
import vnidrop.shared.generated.resources.button_create_share
import vnidrop.shared.generated.resources.button_creating_share
import vnidrop.shared.generated.resources.button_select_file
import vnidrop.shared.generated.resources.button_use_locally
import vnidrop.shared.generated.resources.field_sender_name
import vnidrop.shared.generated.resources.field_transfer_name
import vnidrop.shared.generated.resources.metadata_name
import vnidrop.shared.generated.resources.metadata_size
import vnidrop.shared.generated.resources.metadata_source
import vnidrop.shared.generated.resources.metadata_transfer
import vnidrop.shared.generated.resources.send_source_empty
import vnidrop.shared.generated.resources.send_subtitle
import vnidrop.shared.generated.resources.send_title
import vnidrop.shared.generated.resources.share_details_title
import vnidrop.shared.generated.resources.source_title
import vnidrop.shared.generated.resources.transfer_details_title

@Composable
fun SendScreen(
	coreState: CoreState,
	state: SendState,
	windowClass: WindowClass,
	onSelectFile: () -> Unit,
	onClearFile: () -> Unit,
	onTransferNameChanged: (String) -> Unit,
	onSenderNameChanged: (String) -> Unit,
	onCreateShare: () -> Unit,
	onCopyTicket: (String) -> Unit,
	onUseTicket: (String) -> Unit,
) {
	Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
		if (!state.shouldShowEmptyState(coreState)) {
			ScreenHeader(stringResource(Res.string.send_title), stringResource(Res.string.send_subtitle))
		}
		if (state.shouldShowEmptyState(coreState)) {
			SendEmptyState(windowClass = windowClass, onCreateNewTransfer = onSelectFile)
		} else {
			SourceCard(state, onSelectFile, onClearFile)
			DetailsCard(coreState, state, onTransferNameChanged, onSenderNameChanged, onCreateShare)
			coreState.lastShare?.let { ShareCard(it, onCopyTicket, onUseTicket) }
			ProgressSection(coreState)
		}
	}
}

@Composable
private fun SourceCard(state: SendState, onSelectFile: () -> Unit, onClearFile: () -> Unit) {
	AppCard(title = stringResource(Res.string.source_title)) {
		if (state.selectedSource.isBlank()) {
			EmptyText(stringResource(Res.string.send_source_empty))
		} else {
			MetadataRow(stringResource(Res.string.metadata_name), state.selectedDisplayName.ifBlank { state.selectedSource.substringAfterLast('/') })
			MetadataRow(stringResource(Res.string.metadata_source), state.selectedSource)
		}
		Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
			PrimaryButton(stringResource(Res.string.button_select_file), onClick = onSelectFile)
			SecondaryButton(stringResource(Res.string.button_clear), onClick = onClearFile, enabled = state.hasSelectedSource)
		}
	}
}

@Composable
private fun DetailsCard(
	coreState: CoreState,
	state: SendState,
	onTransferNameChanged: (String) -> Unit,
	onSenderNameChanged: (String) -> Unit,
	onCreateShare: () -> Unit,
) {
	AppCard(title = stringResource(Res.string.transfer_details_title)) {
		Field(state.transferName, onTransferNameChanged, stringResource(Res.string.field_transfer_name))
		Field(state.senderName, onSenderNameChanged, stringResource(Res.string.field_sender_name))
		PrimaryButton(
			text = if (state.isSharing) stringResource(Res.string.button_creating_share) else stringResource(Res.string.button_create_share),
			onClick = onCreateShare,
			enabled = state.canCreateShare(coreState.isInitialized),
		)
	}
}

@Composable
private fun ShareCard(share: Share, onCopyTicket: (String) -> Unit, onUseTicket: (String) -> Unit) {
	AppCard(
		title = stringResource(Res.string.share_details_title),
		trailing = {
			StatusPill("${share.fileCount} file${if (share.fileCount == 1UL) "" else "s"}", tone = PillTone.Brand)
		},
	) {
		MetadataRow(stringResource(Res.string.metadata_transfer), share.transferName)
		MetadataRow(stringResource(Res.string.metadata_size), formatBytes(share.totalSize))
		TicketText(share.ticket)
		Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
			PrimaryButton(stringResource(Res.string.button_copy), onClick = { onCopyTicket(share.ticket) })
			SecondaryButton(stringResource(Res.string.button_use_locally), onClick = { onUseTicket(share.ticket) })
		}
	}
}
