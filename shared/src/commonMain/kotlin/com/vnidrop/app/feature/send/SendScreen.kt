package com.vnidrop.app.feature.send

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.vnidrop.app.core.CoreState
import com.vnidrop.app.core.ShareAccessPolicy
import com.vnidrop.app.core.TransferDirection
import com.vnidrop.app.ui.components.AdaptiveDrawer
import com.vnidrop.app.ui.state.WindowClass

@Composable
fun SendScreen(
	coreState: CoreState,
	state: SendState,
	windowClass: WindowClass,
	onOpenComposer: () -> Unit,
	onDismissComposer: () -> Unit,
	onSelectFile: () -> Unit,
	onClearFile: () -> Unit,
	onTransferNameChanged: (String) -> Unit,
	onSenderNameChanged: (String) -> Unit,
	onAccessPolicyChanged: (ShareAccessPolicy) -> Unit,
	onCreateShare: () -> Unit,
	onTransferSelected: (ULong) -> Unit,
	onCloseTransferDetails: () -> Unit,
	onCopyTicket: (String) -> Unit,
) {
	val outgoingTransfers = coreState.transfers.filter { it.direction == TransferDirection.Send }
	val selectedTransfer = state.selectedTransferId?.let { id -> outgoingTransfers.firstOrNull { it.transferId == id } }

	Box(Modifier.fillMaxSize()) {
		if (selectedTransfer != null) {
			TransferDetails(selectedTransfer, onCloseTransferDetails, onCopyTicket)
		} else {
			TransferCatalog(
				transfers = outgoingTransfers,
				transferThumbnails = state.transferThumbnails,
				windowClass = windowClass,
				onOpenComposer = onOpenComposer,
				onTransferSelected = onTransferSelected,
			)
		}
	}

	if (state.isComposerOpen) {
		AdaptiveDrawer(windowClass = windowClass, onDismissRequest = onDismissComposer) {
			TransferComposer(
				coreInitialized = coreState.isInitialized,
				state = state,
				windowClass = windowClass,
				onSelectFile = onSelectFile,
				onClearFile = onClearFile,
				onTransferNameChanged = onTransferNameChanged,
				onSenderNameChanged = onSenderNameChanged,
				onAccessPolicyChanged = onAccessPolicyChanged,
				onCreateShare = onCreateShare,
			)
		}
	}
}
