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
import uniffi.vnidrop.ReceiverRequest
import uniffi.vnidrop.ReceiveOutputSink
import uniffi.vnidrop.ShareMetadataInput
import uniffi.vnidrop.ShareResult
import uniffi.vnidrop.ShareSource
import uniffi.vnidrop.SourceKind
import uniffi.vnidrop.StoredTransfer
import uniffi.vnidrop.TicketInspection
import uniffi.vnidrop.VnidropCore

data class CoreUiState(
	val isInitialized: Boolean = false,
	val status: String = "Not initialized",
	val events: List<CoreEvent> = emptyList(),
	val transfers: List<StoredTransfer> = emptyList(),
	val lastShare: ShareResult? = null,
	val lastInspection: TicketInspection? = null,
	val receiverRequests: List<ReceiverRequest> = emptyList(),
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
		loadTransfers()
		loadEvents()
		_state.update { it.copy(isInitialized = true, error = null) }
	}

	suspend fun sharePath(path: String, transferName: String, senderName: String) = runCore {
		shareSources(
			sources = listOf(
				ShareSource(
					kind = SourceKind.PATH,
					value = path,
					displayName = path.substringAfterLast('/').ifBlank { null },
					isDirectory = false,
				),
			),
			transferName = transferName,
			senderName = senderName,
		)
	}

	suspend fun shareFileDescriptor(
		fd: Int,
		displayName: String,
		transferName: String,
		senderName: String,
	) = runCore {
		// The fd is borrowed from platform code. Rust duplicates it before
		// starting the import, so Android may close the ParcelFileDescriptor
		// once this suspend call returns.
		shareSources(
			sources = listOf(
				ShareSource(
					kind = SourceKind.FILE_DESCRIPTOR,
					value = fd.toString(),
					displayName = displayName.ifBlank { "transfer" },
					isDirectory = false,
				),
			),
			transferName = transferName,
			senderName = senderName,
		)
	}

	suspend fun shareSecurityScopedFileUrl(
		fileUrl: String,
		displayName: String,
		transferName: String,
		senderName: String,
	) = runCore {
		// The iOS actual for withPlatformPathAccess starts and stops the
		// security-scoped URL lease around this entire shareFiles call.
		shareSources(
			sources = listOf(
				ShareSource(
					kind = SourceKind.IOS_SECURITY_SCOPED_URL,
					value = fileUrl,
					displayName = displayName.ifBlank { fileUrl.substringAfterLast('/').ifBlank { "transfer" } },
					isDirectory = false,
				),
			),
			transferName = transferName,
			senderName = senderName,
		)
	}

	suspend fun inspectTicket(ticket: String) = runCore {
		val inspection = requireCore().inspectTicket(ticket)
		_state.update { it.copy(lastInspection = inspection, error = null) }
	}

	suspend fun receive(ticket: String, outputDir: String, receiverName: String) = runCore {
		requireCore().receive(ticket, outputDir, receiverName.ifBlank { null })
		refreshStatus()
		loadTransfers()
	}

	suspend fun receiveWithOutputSink(ticket: String, outputSink: ReceiveOutputSink, receiverName: String) = runCore {
		requireCore().receiveWithOutputSink(ticket, outputSink, receiverName.ifBlank { null })
		refreshStatus()
		loadTransfers()
	}

	suspend fun receiveIntoSecurityScopedDirectory(
		ticket: String,
		outputDirectoryUrl: String,
		receiverName: String,
	) = runCore {
		withPlatformPathAccess(SourceKind.IOS_SECURITY_SCOPED_URL, outputDirectoryUrl) {
			requireCore().receive(ticket, outputDirectoryUrl, receiverName.ifBlank { null })
		}
		refreshStatus()
		loadTransfers()
	}

	suspend fun cancel(transferId: ULong) = runCore {
		requireCore().cancelTransfer(transferId)
		refreshStatus()
		loadTransfers()
	}

	suspend fun refreshReceiverRequests(transferId: ULong) = runCore {
		val requests = requireCore().listReceiverRequests(transferId)
		_state.update { it.copy(receiverRequests = requests, error = null) }
	}

	suspend fun respondReceiverRequest(requestId: String, accepted: Boolean, reason: String? = null) = runCore {
		requireCore().respondReceiverRequest(requestId, accepted, reason)
		state.value.lastShare?.let { share ->
			val requests = requireCore().listReceiverRequests(share.transferId)
			_state.update { it.copy(receiverRequests = requests, error = null) }
		}
	}

	suspend fun refreshTransfers() = runCore {
		loadTransfers()
	}

	suspend fun refreshEvents() = runCore {
		loadEvents()
	}

	suspend fun setError(message: String) {
		_state.update { it.copy(error = message) }
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

	private suspend fun shareSources(
		sources: List<ShareSource>,
		transferName: String,
		senderName: String,
	) {
		withPlatformPathAccess(sources) {
			val result = requireCore().shareFiles(
				sources = sources,
				metadata = ShareMetadataInput(
					transferId = nextTransferId(),
					transferName = transferName.ifBlank { null },
					senderName = senderName.ifBlank { null },
				),
			)
			_state.update { it.copy(lastShare = result, receiverRequests = emptyList(), error = null) }
		}
		refreshStatus()
		loadTransfers()
	}

	private suspend fun <T> withPlatformPathAccess(
		sources: List<ShareSource>,
		index: Int = 0,
		block: suspend () -> T,
	): T {
		if (index >= sources.size) {
			return block()
		}
		val source = sources[index]
		return withPlatformPathAccess(source.kind, source.value) {
			withPlatformPathAccess(sources, index + 1, block)
		}
	}

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

	private fun loadTransfers() {
		val transfers = core?.listTransfers().orEmpty()
		_state.update { it.copy(transfers = transfers, error = null) }
	}

	private fun loadEvents() {
		val events = core?.listEvents(null).orEmpty()
		_state.update { it.copy(events = events.take(200), error = null) }
	}

	private fun nextTransferId(): ULong =
		Random.nextLong(1, Long.MAX_VALUE).toULong()
}
