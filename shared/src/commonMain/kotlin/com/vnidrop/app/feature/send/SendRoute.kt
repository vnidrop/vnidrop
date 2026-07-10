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
	onUseTicket: (String) -> Unit,
) {
	val state by viewModel.state.collectAsStateWithLifecycle()
	val coreState by viewModel.coreState.collectAsStateWithLifecycle()
	val clipboard = LocalClipboardManager.current
	val picker = rememberShareFilePicker(viewModel::onFilePicked, viewModel::onFilePickFailed)

	LaunchedEffect(viewModel) {
		viewModel.effectFlow.collect { effect ->
			when (effect) {
				SendEffect.OpenFilePicker -> picker.pickFile()
				is SendEffect.CopyTicket -> clipboard.setText(AnnotatedString(effect.ticket))
				is SendEffect.UseTicket -> onUseTicket(effect.ticket)
			}
		}
	}

	SendScreen(
		coreState = coreState,
		state = state,
		windowClass = windowClass,
		onSelectFile = viewModel::selectFile,
		onClearFile = viewModel::clearSelectedSource,
		onTransferNameChanged = viewModel::setTransferName,
		onSenderNameChanged = viewModel::setSenderName,
		onCreateShare = viewModel::createShare,
		onCopyTicket = viewModel::copyTicket,
		onUseTicket = viewModel::useTicket,
	)
}
