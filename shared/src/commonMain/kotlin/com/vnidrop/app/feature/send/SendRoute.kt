package com.vnidrop.app.feature.send

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vnidrop.app.core.rememberShareFilePicker
import com.vnidrop.app.ui.state.WindowClass

@Composable
fun SendRoute(
	viewModel: SendViewModel,
	windowClass: WindowClass,
) {
	val state by viewModel.state.collectAsStateWithLifecycle()
	val coreState by viewModel.coreState.collectAsStateWithLifecycle()
	val clipboard = LocalClipboardManager.current
	val picker = rememberShareFilePicker(viewModel::onFilePicked, viewModel::onFilePickFailed)
	val shareActions = rememberTransferShareActions()

	LaunchedEffect(viewModel) {
		viewModel.effectFlow.collect { effect ->
			when (effect) {
				SendEffect.OpenFilePicker -> picker.pickFile()
				is SendEffect.CopyTicket -> clipboard.setText(AnnotatedString(effect.ticket))
			}
		}
	}

	SendScreen(
		coreState = coreState,
		state = state,
		windowClass = windowClass,
		shareActions = shareActions,
		onOpenComposer = viewModel::openComposer,
		onDismissComposer = viewModel::dismissComposer,
		onSelectFile = viewModel::selectFile,
		onClearFile = viewModel::clearSelectedSource,
		onTransferNameChanged = viewModel::setTransferName,
		onSenderNameChanged = viewModel::setSenderName,
		onAccessPolicyChanged = viewModel::setAccessPolicy,
		onCreateShare = viewModel::createShare,
		onTransferSelected = viewModel::openTransfer,
		onCloseTransferDetails = viewModel::closeTransferDetails,
		onCopyTicket = viewModel::copyTicket,
		onActivity = viewModel::openActivity,
		onReceivers = viewModel::openReceivers,
		onShare = viewModel::openShare,
		onCloseDetailPanel = viewModel::closeDetailPanel,
		onInvitationResult = viewModel::onInvitationResult,
		onRequestDelete = viewModel::requestDeleteTransfer,
		onDismissDelete = viewModel::dismissDeleteTransfer,
		onConfirmDelete = viewModel::confirmDeleteTransfer,
	)
}
