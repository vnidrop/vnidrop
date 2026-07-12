package com.vnidrop.app.feature.send

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vnidrop.app.core.CoreEventModel
import com.vnidrop.app.core.ReceiverDeliveryStatus
import com.vnidrop.app.core.ReceiverRequestModel
import com.vnidrop.app.core.ShareAccessPolicy
import com.vnidrop.app.core.Transfer
import com.vnidrop.app.ui.components.AppCard
import com.vnidrop.app.ui.components.DestructiveButton
import com.vnidrop.app.ui.components.PrimaryButton
import com.vnidrop.app.ui.components.SecondaryButton
import com.vnidrop.app.ui.state.displayNameForStatus
import com.vnidrop.app.ui.state.formatBytes
import com.vnidrop.app.ui.theme.LocalVniDropColors
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.compose.resources.stringResource
import qrcode.QRCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import vnidrop.shared.generated.resources.*

enum class InvitationAction { Export, Share, Nfc }

@Composable
internal fun TransferDetails(
	transfer: Transfer,
	events: List<CoreEventModel>,
	pendingReceivers: Int = 0,
	completedReceivers: Int,
	onBack: () -> Unit,
	onActivity: () -> Unit,
	onReceivers: () -> Unit,
	onShare: () -> Unit,
	onDelete: () -> Unit,
) {
	LazyColumn(
		modifier = Modifier.fillMaxSize().statusBarsPadding(),
		contentPadding = PaddingValues(16.dp),
		verticalArrangement = Arrangement.spacedBy(16.dp),
	) {
		item {
			Row(verticalAlignment = Alignment.CenterVertically) {
				IconButton(onClick = onBack) { Icon(SendIcons.Back, stringResource(Res.string.button_back)) }
				Text(
					stringResource(Res.string.send_transfer_details_title),
					modifier = Modifier.weight(1f),
					style = MaterialTheme.typography.headlineSmall,
					fontWeight = FontWeight.Bold,
				)
				IconButton(onClick = onDelete) {
					Icon(SendIcons.Delete, stringResource(Res.string.button_delete_transfer), tint = LocalVniDropColors.current.destructiveDefault)
				}
			}
		}
		item {
			AppCard(title = transfer.transferName ?: stringResource(Res.string.send_new_transfer_title)) {
				DetailValue(stringResource(Res.string.metadata_status), displayNameForStatus(transfer.status))
				HorizontalDivider(color = LocalVniDropColors.current.borderDefault)
				DetailValue(stringResource(Res.string.metadata_size), formatBytes(transfer.totalSize))
				HorizontalDivider(color = LocalVniDropColors.current.borderDefault)
				DetailValue(stringResource(Res.string.send_access_title), accessPolicyLabel(transfer.accessPolicy))
			}
		}
		item {
			Surface(shape = RoundedCornerShape(16.dp), color = LocalVniDropColors.current.backgroundSurface200) {
				Column {
					DetailDestination(
						title = stringResource(Res.string.transfer_activity_title),
						description = stringResource(Res.string.transfer_activity_description),
						count = events.count { it.transferId == transfer.transferId && it.isMeaningfulActivity() },
						onClick = onActivity,
					)
					HorizontalDivider(color = LocalVniDropColors.current.borderDefault)
					DetailDestination(
						title = stringResource(Res.string.transfer_receivers_title),
						description = receiversDescription(pendingReceivers, completedReceivers),
						count = pendingReceivers + completedReceivers,
						onClick = onReceivers,
					)
					HorizontalDivider(color = LocalVniDropColors.current.borderDefault)
					DetailDestination(
						title = stringResource(Res.string.transfer_share_title),
						description = stringResource(Res.string.transfer_share_description),
						onClick = onShare,
					)
				}
			}
		}
	}
}

@Composable
private fun receiversDescription(pending: Int, completed: Int): String = when {
	pending > 0 && completed > 0 ->
		"${stringResource(Res.string.transfer_receivers_pending, pending)} · ${stringResource(Res.string.transfer_receivers_completed_count, completed)}"
	pending > 0 -> stringResource(Res.string.transfer_receivers_pending, pending)
	completed > 0 -> stringResource(Res.string.transfer_receivers_completed_count, completed)
	else -> stringResource(Res.string.transfer_receivers_description)
}

@Composable
private fun DetailDestination(title: String, description: String, count: Int? = null, onClick: () -> Unit) {
	Row(
		Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
			Text(title, fontWeight = FontWeight.SemiBold)
			Text(description, color = LocalVniDropColors.current.foregroundLighter, style = MaterialTheme.typography.bodySmall)
		}
		if (count != null && count > 0) {
			Text(count.toString(), modifier = Modifier.background(LocalVniDropColors.current.backgroundSelection, RoundedCornerShape(20.dp)).padding(horizontal = 9.dp, vertical = 3.dp))
			Spacer(Modifier.width(8.dp))
		}
		Icon(SendIcons.ChevronRight, null, tint = LocalVniDropColors.current.foregroundLighter, modifier = Modifier.size(18.dp))
	}
}

@Composable
internal fun ReceiverHistoryPanel(receivers: List<ReceiverRequestModel>, loading: Boolean) {
	PanelContainer(stringResource(Res.string.transfer_receivers_title)) {
		when {
			loading -> Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
			receivers.isEmpty() -> Text(stringResource(Res.string.transfer_no_receivers), color = LocalVniDropColors.current.foregroundLighter)
			else -> receivers.forEachIndexed { index, receiver ->
				if (index > 0) HorizontalDivider(color = LocalVniDropColors.current.borderDefault)
				ReceiverRow(receiver)
			}
		}
	}
}

@Composable
private fun ReceiverRow(receiver: ReceiverRequestModel) {
	val name = receiver.receiverName ?: receiver.receiverDeviceName ?: stringResource(Res.string.transfer_nearby_device)
	Column(Modifier.fillMaxWidth().padding(vertical = 13.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
		Text(name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
		receiver.receiverDeviceName?.takeIf { it != name }?.let {
			Text(it, color = LocalVniDropColors.current.foregroundLighter, style = MaterialTheme.typography.bodySmall)
		}
		Text(receiverStatusText(receiver.status), color = receiverStatusColor(receiver.status), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
		receiver.reason?.takeIf { it.isNotBlank() }?.let { reason ->
			Text(reason, color = LocalVniDropColors.current.foregroundLighter, style = MaterialTheme.typography.bodySmall)
		}
	}
}

@Composable
internal fun TransferActivityPanel(events: List<CoreEventModel>, transferId: ULong) {
	val visible = events.filter { it.transferId == transferId && it.isMeaningfulActivity() }.sortedByDescending(CoreEventModel::timestamp)
	PanelContainer(stringResource(Res.string.transfer_activity_title)) {
		if (visible.isEmpty()) Text(stringResource(Res.string.transfer_no_activity), color = LocalVniDropColors.current.foregroundLighter)
		else visible.forEachIndexed { index, event ->
			if (index > 0) HorizontalDivider(color = LocalVniDropColors.current.borderDefault)
			Text(eventTitle(event), modifier = Modifier.padding(vertical = 14.dp), fontWeight = FontWeight.Medium)
		}
	}
}

@Composable
internal fun TransferSharePanel(
	transfer: Transfer,
	actions: TransferShareActions,
	qrBitmap: androidx.compose.ui.graphics.ImageBitmap?,
	onQrRendered: (String, androidx.compose.ui.graphics.ImageBitmap) -> Unit,
	onResult: (InvitationAction, Result<Unit>) -> Unit,
) {
	DisposableEffect(actions) { onDispose(actions::cancelNfcWrite) }
	val ticket = transfer.ticket
	PanelContainer(stringResource(Res.string.transfer_share_title)) {
		if (ticket == null) {
			Text(stringResource(Res.string.transfer_event_preparing), color = LocalVniDropColors.current.foregroundLighter)
			return@PanelContainer
		}
		val renderedBitmap by produceState(qrBitmap, ticket, qrBitmap) {
			if (value == null) {
				value = withContext(Dispatchers.Default) {
					runCatching { QRCode.ofSquares().withSize(8).build(ticket).renderToBytes().decodeToImageBitmap() }.getOrNull()
				}
				value?.let { onQrRendered(ticket, it) }
			}
		}
		val renderedQr = renderedBitmap
		Surface(
			modifier = Modifier.align(Alignment.CenterHorizontally).size(268.dp),
			shape = RoundedCornerShape(18.dp),
			color = Color.White,
		) {
			if (renderedQr != null) {
				Image(renderedQr, null, Modifier.padding(14.dp).fillMaxSize().clip(RoundedCornerShape(8.dp)))
			} else {
				Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
			}
		}
		Text(
			stringResource(Res.string.transfer_scan_qr),
			modifier = Modifier.align(Alignment.CenterHorizontally),
			color = LocalVniDropColors.current.foregroundLighter,
			style = MaterialTheme.typography.bodySmall,
		)
		if (actions.nfcAvailability != NfcShareAvailability.Hidden) {
			var writingNfc by remember(ticket) { mutableStateOf(false) }
			SecondaryButton(
				if (writingNfc) stringResource(Res.string.transfer_nfc_waiting) else stringResource(Res.string.button_write_nfc),
				onClick = {
					writingNfc = true
					actions.writeInvitationToNfc(ticket) {
						writingNfc = false
						onResult(InvitationAction.Nfc, it)
					}
				},
				modifier = Modifier.fillMaxWidth(),
				enabled = actions.nfcAvailability == NfcShareAvailability.Available && !writingNfc,
			)
			if (actions.nfcAvailability == NfcShareAvailability.Unavailable) {
				Text(stringResource(Res.string.transfer_nfc_unavailable), color = LocalVniDropColors.current.foregroundLighter, style = MaterialTheme.typography.bodySmall)
			}
		}
		SecondaryButton(
			stringResource(Res.string.button_download_invitation),
			onClick = { actions.exportInvitation(ticket, transfer.transferName.orEmpty()) { onResult(InvitationAction.Export, it) } },
			modifier = Modifier.fillMaxWidth(),
		)
		PrimaryButton(
			stringResource(Res.string.button_native_share),
			onClick = { actions.shareInvitation(ticket, transfer.transferName.orEmpty()) { onResult(InvitationAction.Share, it) } },
			modifier = Modifier.fillMaxWidth(),
			enabled = actions.canUseNativeShare,
		)
	}
}

@Composable
internal fun DeleteTransferPanel(
	transferName: String?,
	isDeleting: Boolean,
	onCancel: () -> Unit,
	onConfirm: () -> Unit,
) {
	PanelContainer(stringResource(Res.string.transfer_delete_title)) {
		Text(
			stringResource(Res.string.transfer_delete_description, transferName ?: stringResource(Res.string.send_new_transfer_title)),
			color = LocalVniDropColors.current.foregroundLighter,
			style = MaterialTheme.typography.bodyMedium,
		)
		Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
			SecondaryButton(stringResource(Res.string.button_cancel), onClick = onCancel, enabled = !isDeleting)
			DestructiveButton(
				if (isDeleting) stringResource(Res.string.transfer_deleting) else stringResource(Res.string.button_delete_transfer),
				onClick = onConfirm,
				enabled = !isDeleting,
			)
		}
	}
}

@Composable
private fun PanelContainer(title: String, content: @Composable ColumnScope.() -> Unit) {
	Column(
		Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 14.dp),
		verticalArrangement = Arrangement.spacedBy(14.dp),
	) {
		Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
		content()
	}
}

@Composable
private fun receiverStatusText(status: ReceiverDeliveryStatus) = stringResource(when (status) {
	ReceiverDeliveryStatus.Requested -> Res.string.transfer_receiver_requested
	ReceiverDeliveryStatus.Accepted -> Res.string.transfer_receiver_accepted
	ReceiverDeliveryStatus.Refused -> Res.string.transfer_receiver_refused
	ReceiverDeliveryStatus.Expired -> Res.string.transfer_receiver_expired
	ReceiverDeliveryStatus.Completed -> Res.string.transfer_receiver_completed
	ReceiverDeliveryStatus.Unknown -> Res.string.transfer_receiver_unknown
})

@Composable
private fun receiverStatusColor(status: ReceiverDeliveryStatus) = when (status) {
	ReceiverDeliveryStatus.Completed -> LocalVniDropColors.current.brandDefault
	ReceiverDeliveryStatus.Refused, ReceiverDeliveryStatus.Expired -> LocalVniDropColors.current.destructiveDefault
	else -> LocalVniDropColors.current.foregroundLighter
}

private fun CoreEventModel.isMeaningfulActivity() =
	(phase == "import" && kind == "started") ||
		(phase == "ticket" && kind == "created") ||
		(phase == "network" && kind in setOf("connecting", "connected")) ||
		(phase == "download" && kind == "found-collection") ||
		(phase == "lifecycle" && kind in setOf("done", "cancelled", "share-stopped")) ||
		kind in setOf(
			"receiver-requested", "receiver-accepted", "receiver-auto-approved",
			"receiver-refused", "receiver-completed", "share-stopped", "failed",
		)

@Composable
private fun eventTitle(event: CoreEventModel) = stringResource(when {
	event.phase == "import" && event.kind == "started" -> Res.string.transfer_event_preparing
	event.phase == "ticket" && event.kind == "created" -> Res.string.transfer_event_ready
	event.phase == "network" -> Res.string.transfer_event_connecting
	event.phase == "download" -> Res.string.transfer_event_downloading
	event.phase == "export" -> Res.string.transfer_event_saving
	event.kind == "receiver-requested" -> Res.string.transfer_event_requested
	event.kind == "receiver-accepted" || event.kind == "receiver-auto-approved" -> Res.string.transfer_event_approved
	event.kind == "receiver-refused" -> Res.string.transfer_event_refused
	event.kind == "receiver-completed" -> Res.string.transfer_event_completed
	event.kind == "share-stopped" || (event.phase == "lifecycle" && event.kind == "cancelled") ->
		Res.string.transfer_event_stopped
	event.kind == "failed" -> Res.string.transfer_event_failed
	else -> Res.string.transfer_event_updated
})

@Composable
private fun DetailValue(label: String, value: String) {
	Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
		Text(label, color = LocalVniDropColors.current.foregroundLighter, style = MaterialTheme.typography.bodySmall)
		Text(value, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
	}
}

@Composable
internal fun accessPolicyLabel(policy: ShareAccessPolicy): String = when (policy) {
	ShareAccessPolicy.RequireApproval -> stringResource(Res.string.send_access_approval)
	ShareAccessPolicy.AnyoneWithTransfer -> stringResource(Res.string.send_access_anyone)
}
