package com.vnidrop.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vnidrop.app.core.CoreRepository
import com.vnidrop.app.core.CoreUiState
import com.vnidrop.app.core.PickedShareFile
import com.vnidrop.app.core.sharePickedFile
import com.vnidrop.app.logging.AppLogger
import com.vnidrop.app.ui.navigation.AppDestination
import com.vnidrop.app.ui.state.AppUiState
import com.vnidrop.app.ui.state.ReceiveUiState
import com.vnidrop.app.ui.state.SendUiState
import com.vnidrop.app.ui.theme.ThemeMode
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class VniDropAppState(
	val app: AppUiState = AppUiState(),
	val send: SendUiState = SendUiState(),
	val receive: ReceiveUiState = ReceiveUiState(),
)

sealed interface VniDropAppEvent {
	data class DestinationSelected(val destination: AppDestination) : VniDropAppEvent
	data class ThemeModeChanged(val mode: ThemeMode) : VniDropAppEvent
	data object SelectFileClicked : VniDropAppEvent
	data class ShareFilePicked(val file: PickedShareFile) : VniDropAppEvent
	data class ShareFilePickFailed(val reason: String) : VniDropAppEvent
	data object ClearSelectedSourceClicked : VniDropAppEvent
	data class TransferNameChanged(val value: String) : VniDropAppEvent
	data class SenderNameChanged(val value: String) : VniDropAppEvent
	data object CreateShareClicked : VniDropAppEvent
	data class CopyTicketClicked(val ticket: String) : VniDropAppEvent
	data class UseTicketLocallyClicked(val ticket: String) : VniDropAppEvent
	data class RefreshReceiverRequestsClicked(val transferId: ULong) : VniDropAppEvent
	data class RespondReceiverRequestClicked(val requestId: String, val accepted: Boolean) : VniDropAppEvent
	data class ReceiveTicketChanged(val value: String) : VniDropAppEvent
	data class OutputDirectoryChanged(val value: String) : VniDropAppEvent
	data class ReceiverNameChanged(val value: String) : VniDropAppEvent
	data object InspectTicketClicked : VniDropAppEvent
	data object ReceiveClicked : VniDropAppEvent
}

sealed interface VniDropAppEffect {
	data object OpenShareFilePicker : VniDropAppEffect
	data class CopyTicket(val ticket: String) : VniDropAppEffect
}

class VniDropAppViewModel(
	appDataDir: String,
	defaultReceiveDir: String,
	platformName: String,
	private val repository: CoreRepository = CoreRepository(),
) : ViewModel() {
	private val _state = MutableStateFlow(VniDropAppState(receive = ReceiveUiState(outputDirectory = defaultReceiveDir)))
	val state: StateFlow<VniDropAppState> = _state
	val coreState: StateFlow<CoreUiState> = repository.state

	private val effects = Channel<VniDropAppEffect>(Channel.BUFFERED)
	val effectFlow = effects.receiveAsFlow()

	private var selectedFile: PickedShareFile? = null

	init {
		AppLogger.initialize(appDataDir)
		AppLogger.info("lifecycle", "app started", mapOf("platform" to platformName))
		AppLogger.info("core", "automatic initialize requested", mapOf("appDataDir" to appDataDir))
		viewModelScope.launch {
			repository.initialize(appDataDir)
		}
	}

	fun onEvent(event: VniDropAppEvent) {
		when (event) {
			is VniDropAppEvent.DestinationSelected -> updateAppState { copy(destination = event.destination) }
			is VniDropAppEvent.ThemeModeChanged -> setThemeMode(event.mode)
			VniDropAppEvent.SelectFileClicked -> sendEffect(VniDropAppEffect.OpenShareFilePicker)
			is VniDropAppEvent.ShareFilePicked -> setSelectedFile(event.file)
			is VniDropAppEvent.ShareFilePickFailed -> setFilePickerError(event.reason)
			VniDropAppEvent.ClearSelectedSourceClicked -> clearSelectedSource()
			is VniDropAppEvent.TransferNameChanged -> updateSendState { copy(transferName = event.value) }
			is VniDropAppEvent.SenderNameChanged -> updateSendState { copy(senderName = event.value) }
			VniDropAppEvent.CreateShareClicked -> createShare()
			is VniDropAppEvent.CopyTicketClicked -> sendEffect(VniDropAppEffect.CopyTicket(event.ticket))
			is VniDropAppEvent.UseTicketLocallyClicked -> useTicketLocally(event.ticket)
			is VniDropAppEvent.RefreshReceiverRequestsClicked -> refreshReceiverRequests(event.transferId)
			is VniDropAppEvent.RespondReceiverRequestClicked -> respondReceiverRequest(event.requestId, event.accepted)
			is VniDropAppEvent.ReceiveTicketChanged -> updateReceiveState { copy(ticket = event.value) }
			is VniDropAppEvent.OutputDirectoryChanged -> updateReceiveState { copy(outputDirectory = event.value) }
			is VniDropAppEvent.ReceiverNameChanged -> updateReceiveState { copy(receiverName = event.value) }
			VniDropAppEvent.InspectTicketClicked -> inspectTicket()
			VniDropAppEvent.ReceiveClicked -> receive()
		}
	}

	private fun setThemeMode(mode: ThemeMode) {
		AppLogger.info("appearance", "theme mode changed", mapOf("mode" to mode.name))
		updateAppState { copy(themeMode = mode) }
	}

	private fun setSelectedFile(file: PickedShareFile) {
		AppLogger.info("file-picker", "file selected", mapOf("name" to file.displayName))
		selectedFile = file
		updateSendState { withSelectedFile(file) }
	}

	private fun setFilePickerError(reason: String) {
		AppLogger.warn("file-picker", "file picker error", mapOf("reason" to reason))
		viewModelScope.launch { repository.setError(reason) }
	}

	private fun clearSelectedSource() {
		selectedFile = null
		updateSendState {
			copy(
				selectedSource = "",
				selectedDisplayName = "",
			)
		}
	}

	private fun createShare() {
		val sendState = state.value.send
		if (!sendState.canCreateShare(coreState.value.isInitialized)) return

		viewModelScope.launch {
			AppLogger.info("send", "create share requested", mapOf("source" to sendState.selectedSource))
			updateSendState { copy(isSharing = true) }
			try {
				val file = selectedFile
				if (file == null) {
					repository.sharePath(sendState.selectedSource, sendState.transferName, sendState.senderName)
				} else {
					sharePickedFile(repository, file, sendState.transferName, sendState.senderName)
				}
				repository.state.value.lastShare?.let { share ->
					repository.refreshReceiverRequests(share.transferId)
				}
			} finally {
				updateSendState { copy(isSharing = false) }
			}
		}
	}

	private fun useTicketLocally(ticket: String) {
		updateReceiveState { copy(ticket = ticket) }
		updateAppState { copy(destination = AppDestination.Receive) }
	}

	private fun refreshReceiverRequests(transferId: ULong) {
		viewModelScope.launch {
			repository.refreshReceiverRequests(transferId)
		}
	}

	private fun respondReceiverRequest(requestId: String, accepted: Boolean) {
		viewModelScope.launch {
			repository.respondReceiverRequest(
				requestId = requestId,
				accepted = accepted,
				reason = if (accepted) null else "sender-refused",
			)
		}
	}

	private fun inspectTicket() {
		val receiveState = state.value.receive
		if (!receiveState.canInspect(coreState.value.isInitialized)) return

		viewModelScope.launch {
			repository.inspectTicket(receiveState.ticket)
		}
	}

	private fun receive() {
		val receiveState = state.value.receive
		if (!receiveState.canReceive(coreState.value.isInitialized)) return

		viewModelScope.launch {
			AppLogger.info("receive", "receive requested")
			updateReceiveState { copy(isReceiving = true) }
			try {
				repository.receive(receiveState.ticket, receiveState.outputDirectory, receiveState.receiverName)
			} finally {
				updateReceiveState { copy(isReceiving = false) }
			}
		}
	}

	private fun sendEffect(effect: VniDropAppEffect) {
		viewModelScope.launch {
			effects.send(effect)
		}
	}

	private fun updateAppState(reducer: AppUiState.() -> AppUiState) {
		_state.update { current ->
			val next = current.app.reducer()
			if (next == current.app) current else current.copy(app = next)
		}
	}

	private fun updateSendState(reducer: SendUiState.() -> SendUiState) {
		_state.update { current ->
			val next = current.send.reducer()
			if (next == current.send) current else current.copy(send = next)
		}
	}

	private fun updateReceiveState(reducer: ReceiveUiState.() -> ReceiveUiState) {
		_state.update { current ->
			val next = current.receive.reducer()
			if (next == current.receive) current else current.copy(receive = next)
		}
	}
}

private fun SendUiState.withSelectedFile(file: PickedShareFile): SendUiState =
	copy(
		selectedSource = file.value,
		selectedDisplayName = file.displayName,
		transferName = if (transferName == DefaultTransferName || transferName.isBlank()) file.displayName else transferName,
	)

private const val DefaultTransferName = "VniDrop transfer"
