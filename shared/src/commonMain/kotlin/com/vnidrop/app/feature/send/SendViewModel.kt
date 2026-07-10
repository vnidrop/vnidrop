package com.vnidrop.app.feature.send

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vnidrop.app.core.CoreGateway
import com.vnidrop.app.core.FileSystemService
import com.vnidrop.app.core.PickedShareFile
import com.vnidrop.app.core.ShareAccessPolicy
import com.vnidrop.app.preferences.PreferencesRepository
import com.vnidrop.app.ui.feedback.UiMessage
import com.vnidrop.app.ui.feedback.UiMessageController
import com.vnidrop.app.ui.feedback.UiMessageTone
import com.vnidrop.app.ui.feedback.UiText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import vnidrop.shared.generated.resources.Res
import vnidrop.shared.generated.resources.send_transfer_created

data class SendState(
	val isComposerOpen: Boolean = false,
	val selectedFile: PickedShareFile? = null,
	val transferName: String = "",
	val senderName: String = "",
	val accessPolicy: ShareAccessPolicy = ShareAccessPolicy.RequireApproval,
	val isSharing: Boolean = false,
	val selectedTransferId: ULong? = null,
	val transferThumbnails: Map<ULong, ByteArray> = emptyMap(),
) {
	fun canCreateShare(coreInitialized: Boolean): Boolean =
		coreInitialized && selectedFile != null && transferName.isNotBlank() && !isSharing
}

sealed interface SendEffect {
	data object OpenFilePicker : SendEffect
	data class CopyTicket(val ticket: String) : SendEffect
}

class SendViewModel(
	private val repository: CoreGateway,
	private val fileSystemService: FileSystemService,
	preferencesRepository: PreferencesRepository,
	private val messages: UiMessageController,
) : ViewModel() {
	private val _state = MutableStateFlow(SendState())
	val state: StateFlow<SendState> = _state.asStateFlow()
	val coreState = repository.state

	private val effects = Channel<SendEffect>(Channel.BUFFERED)
	val effectFlow = effects.receiveAsFlow()

	init {
		viewModelScope.launch {
			preferencesRepository.preferences.collect { preferences ->
				_state.update { current ->
					if (current.senderName.isBlank()) current.copy(senderName = preferences.username) else current
				}
			}
		}
	}

	fun openComposer() {
		if (_state.value.isSharing) return
		_state.update {
			it.copy(
				isComposerOpen = true,
				selectedFile = null,
				transferName = "",
				accessPolicy = ShareAccessPolicy.RequireApproval,
			)
		}
	}

	fun dismissComposer() {
		if (_state.value.isSharing) return
		_state.update {
			it.copy(
				isComposerOpen = false,
				selectedFile = null,
				transferName = "",
				accessPolicy = ShareAccessPolicy.RequireApproval,
			)
		}
	}

	fun selectFile() = sendEffect(SendEffect.OpenFilePicker)

	fun onFilePicked(file: PickedShareFile) {
		_state.update {
			it.copy(
				isComposerOpen = true,
				selectedFile = file,
				transferName = file.displayName,
			)
		}
	}

	fun onFilePickFailed(reason: String) = messages.error(IllegalStateException(reason))

	fun clearSelectedSource() {
		_state.update { it.copy(selectedFile = null, transferName = "") }
	}

	fun setTransferName(value: String) = _state.update { it.copy(transferName = value) }
	fun setSenderName(value: String) = _state.update { it.copy(senderName = value) }
	fun setAccessPolicy(value: ShareAccessPolicy) = _state.update { it.copy(accessPolicy = value) }
	fun openTransfer(transferId: ULong) = _state.update { it.copy(selectedTransferId = transferId) }
	fun closeTransferDetails() = _state.update { it.copy(selectedTransferId = null) }
	fun copyTicket(ticket: String) = sendEffect(SendEffect.CopyTicket(ticket))

	fun createShare() {
		val current = state.value
		val file = current.selectedFile ?: return
		if (!current.canCreateShare(coreState.value.isInitialized)) return
		viewModelScope.launch {
			_state.update { it.copy(isSharing = true) }
			val result = fileSystemService.sharePickedFile(
				repository = repository,
				file = file,
				transferName = current.transferName.trim(),
				senderName = current.senderName.trim(),
				accessPolicy = current.accessPolicy,
			)
			result.fold(
				onSuccess = { share ->
					_state.update {
						it.copy(
							isComposerOpen = false,
							selectedFile = null,
							transferName = "",
							accessPolicy = ShareAccessPolicy.RequireApproval,
							isSharing = false,
							transferThumbnails = file.thumbnailBytes?.let { bytes ->
								it.transferThumbnails + (share.transferId to bytes)
							} ?: it.transferThumbnails,
						)
					}
					messages.show(UiMessage(UiText.Resource(Res.string.send_transfer_created), UiMessageTone.Success))
				},
				onFailure = { error ->
					_state.update { it.copy(isSharing = false) }
					messages.error(error)
				},
			)
		}
	}

	private fun sendEffect(effect: SendEffect) {
		viewModelScope.launch { effects.send(effect) }
	}
}
