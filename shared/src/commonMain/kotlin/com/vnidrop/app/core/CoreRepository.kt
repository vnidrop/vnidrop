package com.vnidrop.app.core

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlin.random.Random
import uniffi.vnidrop.CoreEvent
import uniffi.vnidrop.CoreEventSink
import uniffi.vnidrop.ShareMetadataInput
import uniffi.vnidrop.ShareResult
import uniffi.vnidrop.ShareSource
import uniffi.vnidrop.SourceKind
import uniffi.vnidrop.TicketInspection
import uniffi.vnidrop.VnidropCore

data class CoreUiState(
	val isInitialized: Boolean = false,
	val status: String = "Not initialized",
	val events: List<CoreEvent> = emptyList(),
	val lastShare: ShareResult? = null,
	val lastInspection: TicketInspection? = null,
	val error: String? = null,
)

class CoreRepository(
	private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
	private val _state = MutableStateFlow(CoreUiState())
	val state: StateFlow<CoreUiState> = _state

	private var core: VnidropCore? = null

	private val sink = object : CoreEventSink {
		override fun onEvent(event: CoreEvent) {
			_state.update { current ->
				current.copy(events = (listOf(event) + current.events).take(200))
			}
		}
	}

	suspend fun initialize(appDataDir: String) = runCore {
		core?.shutdown()
		core = VnidropCore.initialize(appDataDir, sink)
		refreshStatus()
		_state.update { it.copy(isInitialized = true, error = null) }
	}

	suspend fun sharePath(path: String, transferName: String, senderName: String) = runCore {
		val active = requireCore()
		val result = active.shareFiles(
			sources = listOf(
				ShareSource(
					kind = SourceKind.PATH,
					value = path,
					displayName = path.substringAfterLast('/').ifBlank { null },
					isDirectory = false,
				),
			),
			metadata = ShareMetadataInput(
				transferId = nextTransferId(),
				transferName = transferName.ifBlank { null },
				senderName = senderName.ifBlank { null },
			),
		)
		_state.update { it.copy(lastShare = result, error = null) }
		refreshStatus()
	}

	suspend fun inspectTicket(ticket: String) = runCore {
		val inspection = requireCore().inspectTicket(ticket)
		_state.update { it.copy(lastInspection = inspection, error = null) }
	}

	suspend fun receive(ticket: String, outputDir: String, receiverName: String) = runCore {
		requireCore().receive(ticket, outputDir, receiverName.ifBlank { null })
		refreshStatus()
	}

	suspend fun cancel(transferId: ULong) = runCore {
		requireCore().cancelTransfer(transferId)
		refreshStatus()
	}

	private suspend fun runCore(block: suspend () -> Unit) {
		withContext(dispatcher) {
			try {
				block()
			} catch (error: Throwable) {
				_state.update { it.copy(error = error.message ?: error.toString()) }
			}
		}
	}

	private fun requireCore(): VnidropCore =
		core ?: error("Initialize the core first.")

	private fun refreshStatus() {
		val status = core?.status()
		_state.update {
			it.copy(
				status = status?.let { value ->
					"Endpoint ${value.endpointId.take(12)}... | active=${value.activeTransfers} shares=${value.activeShares}"
				} ?: "Not initialized",
			)
		}
	}

	private fun nextTransferId(): ULong =
		Random.nextLong(1, Long.MAX_VALUE).toULong()
}
