package com.vnidrop.app.feature.receive

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vnidrop.app.ui.state.WindowClass

@Composable
fun ReceiveRoute(viewModel: ReceiveViewModel, windowClass: WindowClass) {
	val state by viewModel.state.collectAsStateWithLifecycle()
	val coreState by viewModel.coreState.collectAsStateWithLifecycle()
	val actions = rememberReceiveInvitationActions()
	DisposableEffect(actions) { onDispose(actions::cancel) }

	ReceiveScreen(
		coreState = coreState,
		state = state,
		windowClass = windowClass,
		actions = actions,
		onOpenAcquisition = viewModel::openAcquisition,
		onDismissAcquisition = {
			actions.cancel()
			viewModel.dismissAcquisition()
		},
		onReceiverNameChanged = viewModel::setReceiverName,
		onInvitationResult = viewModel::onInvitationResult,
		onWaitingForNfc = viewModel::setWaitingForNfc,
		onReceive = viewModel::receive,
		onRequestDeleteHistoryItem = viewModel::requestDeleteHistoryItem,
		onRequestClearHistory = viewModel::requestClearHistory,
		onDismissHistoryDelete = viewModel::dismissHistoryDelete,
		onConfirmHistoryDelete = viewModel::confirmHistoryDelete,
	)
}
