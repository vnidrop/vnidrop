package com.vnidrop.app.feature.send

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vnidrop.app.core.CoreGateway
import com.vnidrop.app.core.FileSystemService
import com.vnidrop.app.core.PickedShareFile
import com.vnidrop.app.preferences.PreferencesRepository
import com.vnidrop.app.ui.feedback.UiMessageController
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val DefaultTransferName = "VniDrop transfer"

data class SendState(
	val selectedSource: String = "",
	val selectedDisplayName: String = "",
	val transferName: String = DefaultTransferName,
	val senderName: String = "",
	val isSharing: Boolean = false,
) {
	val hasSelectedSource: Boolean
		get() = selectedSource.isNotBlank()

	fun canCreateShare(coreInitialized: Boolean): Boolean = coreInitialized && hasSelectedSource && !isSharing
}

sealed interface SendEffect {
	data object OpenFilePicker : SendEffect
	data class CopyTicket(val ticket: String) : SendEffect
	data class UseTicket(val ticket: String) : SendEffect
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
	private var selectedFile: PickedShareFile? = null

	init {
		viewModelScope.launch {
			preferencesRepository.preferences.collect { preferences ->
				_state.update { current ->
					if (current.senderName.isBlank()) current.copy(senderName = preferences.username) else current
				}
			}
		}
	}

	fun selectFile() = sendEffect(SendEffect.OpenFilePicker)

	fun onFilePicked(file: PickedShareFile) {
		selectedFile = file
		_state.update {
			it.copy(
				selectedSource = file.value,
				selectedDisplayName = file.displayName,
				transferName = if (it.transferName.isBlank() || it.transferName == DefaultTransferName) file.displayName else it.transferName,
			)
		}
	}

	fun onFilePickFailed(reason: String) = messages.error(IllegalStateException(reason))

	fun clearSelectedSource() {
		selectedFile = null
		_state.update { it.copy(selectedSource = "", selectedDisplayName = "") }
	}

	fun setTransferName(value: String) = _state.update { it.copy(transferName = value) }
	fun setSenderName(value: String) = _state.update { it.copy(senderName = value) }
	fun copyTicket(ticket: String) = sendEffect(SendEffect.CopyTicket(ticket))
	fun useTicket(ticket: String) = sendEffect(SendEffect.UseTicket(ticket))

	fun createShare() {
		val current = state.value
		if (!current.canCreateShare(coreState.value.isInitialized)) return
		viewModelScope.launch {
			_state.update { it.copy(isSharing = true) }
			try {
				val result = selectedFile?.let { file ->
					fileSystemService.sharePickedFile(repository, file, current.transferName, current.senderName)
				} ?: repository.sharePath(current.selectedSource, current.transferName, current.senderName)
				result.onFailure(messages::error)
			} finally {
				_state.update { it.copy(isSharing = false) }
			}
		}
	}

	private fun sendEffect(effect: SendEffect) {
		viewModelScope.launch { effects.send(effect) }
	}

}
