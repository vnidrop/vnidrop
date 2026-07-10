package com.vnidrop.app.feature.receive

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun ReceiveRoute(viewModel: ReceiveViewModel) {
	val state by viewModel.state.collectAsStateWithLifecycle()
	val coreState by viewModel.coreState.collectAsStateWithLifecycle()
	ReceiveScreen(
		coreState = coreState,
		state = state,
		onTicketChanged = viewModel::setTicket,
		onReceiverNameChanged = viewModel::setReceiverName,
		onInspectTicket = viewModel::inspectTicket,
		onReceive = viewModel::receive,
	)
}
