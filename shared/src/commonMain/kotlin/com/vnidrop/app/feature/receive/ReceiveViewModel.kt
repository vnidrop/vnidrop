package com.vnidrop.app.feature.receive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vnidrop.app.core.CoreGateway
import com.vnidrop.app.core.FileSystemService
import com.vnidrop.app.core.FolderAccessStatus
import com.vnidrop.app.core.ReceiveFolder
import com.vnidrop.app.core.ReceiveFolderKind
import com.vnidrop.app.preferences.PreferencesRepository
import com.vnidrop.app.ui.feedback.UiMessageController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReceiveState(
	val ticket: String = "",
	val outputDirectory: String = "",
	val receiverName: String = "",
	val receiveFolder: ReceiveFolder? = null,
	val folderAccessStatus: FolderAccessStatus = FolderAccessStatus.Unavailable,
	val isReceiving: Boolean = false,
) {
	fun canInspect(coreInitialized: Boolean): Boolean = coreInitialized && ticket.isNotBlank()
	fun canReceive(coreInitialized: Boolean): Boolean =
		coreInitialized && ticket.isNotBlank() && outputDirectory.isNotBlank() &&
			folderAccessStatus == FolderAccessStatus.Writable && !isReceiving
}

class ReceiveViewModel(
	private val repository: CoreGateway,
	private val fileSystemService: FileSystemService,
	preferencesRepository: PreferencesRepository,
	private val messages: UiMessageController,
) : ViewModel() {
	private val _state = MutableStateFlow(ReceiveState())
	val state: StateFlow<ReceiveState> = _state.asStateFlow()
	val coreState = repository.state

	init {
		viewModelScope.launch {
			preferencesRepository.preferences.collect { preferences ->
				val status = fileSystemService.validateReceiveFolder(preferences.receiveFolder)
				_state.update { current ->
					current.copy(
						receiverName = current.receiverName.ifBlank { preferences.username },
						receiveFolder = preferences.receiveFolder,
						outputDirectory = preferences.receiveFolder.value,
						folderAccessStatus = status,
					)
				}
			}
		}
	}

	fun setTicket(value: String) = _state.update { it.copy(ticket = value) }
	fun setOutputDirectory(value: String) = _state.update { it.copy(outputDirectory = value) }
	fun setReceiverName(value: String) = _state.update { it.copy(receiverName = value) }

	fun inspectTicket() {
		val current = state.value
		if (!current.canInspect(coreState.value.isInitialized)) return
		viewModelScope.launch { repository.inspectTicket(current.ticket).onFailure(messages::error) }
	}

	fun receive() {
		val current = state.value
		val folder = current.receiveFolder ?: return
		if (!current.canReceive(coreState.value.isInitialized)) return
		viewModelScope.launch {
			_state.update { it.copy(isReceiving = true) }
			try {
				val outputSink = fileSystemService.createReceiveOutputSink(folder)
				val result = when {
					outputSink != null -> repository.receiveWithOutputSink(current.ticket, outputSink, current.receiverName)
					folder.kind == ReceiveFolderKind.IosSecurityScopedUrl -> repository.receiveIntoSecurityScopedDirectory(
						current.ticket,
						folder.value,
						current.receiverName,
					)
					else -> repository.receive(current.ticket, current.outputDirectory, current.receiverName)
				}
				result.onFailure(messages::error)
			} finally {
				_state.update { it.copy(isReceiving = false) }
			}
		}
	}
}
