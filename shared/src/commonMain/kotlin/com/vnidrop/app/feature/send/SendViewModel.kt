package com.vnidrop.app.feature.send

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vnidrop.app.core.CoreGateway
import com.vnidrop.app.core.CoreSignal
import com.vnidrop.app.core.FileSystemService
import com.vnidrop.app.core.PickedShareFile
import com.vnidrop.app.core.ShareAccessPolicy
import com.vnidrop.app.core.ReceiverRequestModel
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import vnidrop.shared.generated.resources.Res
import vnidrop.shared.generated.resources.send_transfer_created
import vnidrop.shared.generated.resources.transfer_nfc_written
import vnidrop.shared.generated.resources.transfer_deleted

data class SendState(
	val isComposerOpen: Boolean = false,
	val selectedFile: PickedShareFile? = null,
	val transferName: String = "",
	val senderName: String = "",
	val accessPolicy: ShareAccessPolicy = ShareAccessPolicy.RequireApproval,
	val isSharing: Boolean = false,
	val selectedTransferId: ULong? = null,
	val transferThumbnails: Map<ULong, ByteArray> = emptyMap(),
	val detailPanel: TransferDetailPanel? = null,
	val receiverHistory: List<ReceiverRequestModel> = emptyList(),
	val isLoadingReceivers: Boolean = false,
	val isDeleteConfirmationOpen: Boolean = false,
	val isDeleting: Boolean = false,
) {
	fun canCreateShare(coreInitialized: Boolean): Boolean =
		coreInitialized && selectedFile != null && transferName.isNotBlank() && !isSharing
}

enum class TransferDetailPanel { Activity, Receivers, Share }

sealed interface SendEffect {
	data object OpenFilePicker : SendEffect
	data class CopyTicket(val ticket: String) : SendEffect
}

class SendViewModel(
	private val repository: CoreGateway,
	private val fileSystemService: FileSystemService,
	preferencesRepository: PreferencesRepository,
	private val filePreviewRepository: FilePreviewRepository,
	private val messages: UiMessageController,
) : ViewModel() {
	private val _state = MutableStateFlow(SendState())
	val state: StateFlow<SendState> = _state.asStateFlow()
	val coreState = repository.state

	private val effects = Channel<SendEffect>(Channel.BUFFERED)
	val effectFlow = effects.receiveAsFlow()

	init {
		viewModelScope.launch {
			repository.signals.collect { signal ->
				val transferId = when (signal) {
					is CoreSignal.ReceiverHistoryChanged -> signal.transferId
					is CoreSignal.ApprovalChanged -> signal.transferId
				}
				if (transferId == _state.value.selectedTransferId &&
					_state.value.detailPanel == TransferDetailPanel.Receivers
				) refreshReceivers(transferId)
			}
		}
		viewModelScope.launch {
			filePreviewRepository.previews.collect { previews ->
				_state.update { it.copy(transferThumbnails = previews) }
			}
		}
		viewModelScope.launch {
			coreState.map { core ->
				core.takeIf { it.isInitialized }?.transfers?.map { it.transferId }?.toSet()
			}.distinctUntilChanged().collect { activeIds ->
				if (activeIds != null) filePreviewRepository.restore(activeIds)
			}
		}
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
	fun openTransfer(transferId: ULong) {
		_state.update { it.copy(selectedTransferId = transferId, detailPanel = null) }
		refreshReceivers(transferId)
	}
	fun closeTransferDetails() = _state.update {
		it.copy(
			selectedTransferId = null,
			detailPanel = null,
			receiverHistory = emptyList(),
			isDeleteConfirmationOpen = false,
		)
	}
	fun openActivity() = _state.update { it.copy(detailPanel = TransferDetailPanel.Activity) }
	fun openShare() = _state.update { it.copy(detailPanel = TransferDetailPanel.Share) }
	fun openReceivers() {
		val transferId = _state.value.selectedTransferId ?: return
		_state.update { it.copy(detailPanel = TransferDetailPanel.Receivers) }
		refreshReceivers(transferId)
	}
	fun closeDetailPanel() = _state.update { it.copy(detailPanel = null) }
	fun requestDeleteTransfer() = _state.update { it.copy(isDeleteConfirmationOpen = true) }
	fun dismissDeleteTransfer() {
		if (!_state.value.isDeleting) _state.update { it.copy(isDeleteConfirmationOpen = false) }
	}
	fun confirmDeleteTransfer() {
		val transferId = _state.value.selectedTransferId ?: return
		if (_state.value.isDeleting) return
		viewModelScope.launch {
			_state.update { it.copy(isDeleting = true) }
			repository.delete(transferId).fold(
				onSuccess = {
					filePreviewRepository.remove(transferId)
					_state.update {
						it.copy(
							selectedTransferId = null,
							detailPanel = null,
							receiverHistory = emptyList(),
							isDeleteConfirmationOpen = false,
							isDeleting = false,
						)
					}
					messages.tryShow(UiMessage(UiText.Resource(Res.string.transfer_deleted), UiMessageTone.Success))
				},
				onFailure = { error ->
					_state.update { it.copy(isDeleting = false) }
					messages.error(error)
				},
			)
		}
	}
	fun copyTicket(ticket: String) = sendEffect(SendEffect.CopyTicket(ticket))
	fun onInvitationResult(action: InvitationAction, result: Result<Unit>) {
		result.fold(
			onSuccess = {
				val message = when (action) {
					InvitationAction.Export -> null
					InvitationAction.Nfc -> Res.string.transfer_nfc_written
					InvitationAction.Share -> null
				}
				message?.let { messages.tryShow(UiMessage(UiText.Resource(it), UiMessageTone.Success)) }
			},
			onFailure = messages::error,
		)
	}

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
					file.thumbnailBytes?.let { filePreviewRepository.save(share.transferId, it) }
					_state.update {
						it.copy(
							isComposerOpen = false,
							selectedFile = null,
							transferName = "",
							accessPolicy = ShareAccessPolicy.RequireApproval,
							isSharing = false,
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

	private fun refreshReceivers(transferId: ULong) {
		viewModelScope.launch {
			_state.update { it.copy(isLoadingReceivers = true) }
			repository.receiverRequests(transferId).fold(
				onSuccess = { requests -> _state.update { it.copy(receiverHistory = requests, isLoadingReceivers = false) } },
				onFailure = { error ->
					_state.update { it.copy(isLoadingReceivers = false) }
					messages.error(error)
				},
			)
		}
	}
}
