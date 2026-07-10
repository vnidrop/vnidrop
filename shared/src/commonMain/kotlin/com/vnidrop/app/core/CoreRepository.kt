package com.vnidrop.app.core

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlin.random.Random
import uniffi.vnidrop.CoreEvent
import uniffi.vnidrop.CoreEventSink
import uniffi.vnidrop.ReceiveOutputSink
import uniffi.vnidrop.ReceiverRequest
import uniffi.vnidrop.ShareMetadataInput
import uniffi.vnidrop.ShareResult
import uniffi.vnidrop.ShareSource
import uniffi.vnidrop.SourceKind
import uniffi.vnidrop.StoredTransfer
import uniffi.vnidrop.TicketInspection
import uniffi.vnidrop.TransferMetadata
import uniffi.vnidrop.VnidropCore

class CoreRepository(
	private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : CoreGateway {
	private val _state = MutableStateFlow(CoreState())
	override val state: StateFlow<CoreState> = _state.asStateFlow()

	private val _signals = MutableSharedFlow<CoreSignal>(
		extraBufferCapacity = 64,
		onBufferOverflow = BufferOverflow.DROP_OLDEST,
	)
	override val signals: SharedFlow<CoreSignal> = _signals.asSharedFlow()

	private var core: VnidropCore? = null

	private val sink = object : CoreEventSink {
		override fun onEvent(event: CoreEvent) {
			val model = event.toModel()
			_state.update { current -> current.copy(events = (listOf(model) + current.events).take(MaxEvents)) }
			if (model.phase == "approval" && model.transferId != null) {
				_signals.tryEmit(CoreSignal.ApprovalChanged(model.transferId))
			}
		}
	}

	override suspend fun initialize(appDataDir: String): Result<Unit> = runCore {
		core?.shutdown()
		core = VnidropCore.initialize(appDataDir, sink)
		refreshSnapshot()
		_state.update { it.copy(isInitialized = true) }
	}

	override fun shutdown() {
		core?.shutdown()
		core = null
		_state.value = CoreState()
	}

	override suspend fun sharePath(path: String, transferName: String, senderName: String): Result<Share> =
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

	override suspend fun shareFileDescriptor(
		fd: Int,
		displayName: String,
		transferName: String,
		senderName: String,
	): Result<Share> =
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

	override suspend fun shareSecurityScopedFileUrl(
		fileUrl: String,
		displayName: String,
		transferName: String,
		senderName: String,
	): Result<Share> =
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

	override suspend fun inspectTicket(ticket: String): Result<TicketInspectionModel> = runCore {
		requireCore().inspectTicket(ticket).toModel().also { inspection ->
			_state.update { it.copy(lastInspection = inspection) }
		}
	}

	override suspend fun receive(ticket: String, outputDir: String, receiverName: String): Result<Unit> = runCore {
		requireCore().receive(ticket, outputDir, receiverName.ifBlank { null })
		refreshSnapshot()
	}

	override suspend fun receiveWithOutputSink(
		ticket: String,
		outputSink: ReceiveOutputSink,
		receiverName: String,
	): Result<Unit> = runCore {
		requireCore().receiveWithOutputSink(ticket, outputSink, receiverName.ifBlank { null })
		refreshSnapshot()
	}

	override suspend fun receiveIntoSecurityScopedDirectory(
		ticket: String,
		outputDirectoryUrl: String,
		receiverName: String,
	): Result<Unit> = runCore {
		withPlatformPathAccess(SourceKind.IOS_SECURITY_SCOPED_URL, outputDirectoryUrl) {
			requireCore().receive(ticket, outputDirectoryUrl, receiverName.ifBlank { null })
		}
		refreshSnapshot()
	}

	override suspend fun cancel(transferId: ULong): Result<Unit> = runCore {
		requireCore().cancelTransfer(transferId)
		refreshSnapshot()
	}

	override suspend fun receiverRequests(transferId: ULong): Result<List<ReceiverRequestModel>> = runCore {
		requireCore().listReceiverRequests(transferId).map(ReceiverRequest::toModel)
	}

	override suspend fun respondReceiverRequest(
		requestId: String,
		accepted: Boolean,
		reason: String?,
	): Result<Unit> = runCore {
		requireCore().respondReceiverRequest(requestId, accepted, reason)
	}

	override suspend fun refresh(): Result<Unit> = runCore { refreshSnapshot() }

	private suspend fun shareSources(
		sources: List<ShareSource>,
		transferName: String,
		senderName: String,
	): Result<Share> = runCore {
		withPlatformPathAccess(sources) {
			requireCore().shareFiles(
				sources = sources,
				metadata = ShareMetadataInput(
					transferId = nextTransferId(),
					transferName = transferName.ifBlank { null },
					senderName = senderName.ifBlank { null },
				),
			).toModel()
		}.also { share ->
			refreshSnapshot()
			_state.update { it.copy(lastShare = share) }
		}
	}

	private suspend fun <T> withPlatformPathAccess(
		sources: List<ShareSource>,
		index: Int = 0,
		block: suspend () -> T,
	): T {
		if (index >= sources.size) return block()
		val source = sources[index]
		return withPlatformPathAccess(source.kind, source.value) {
			withPlatformPathAccess(sources, index + 1, block)
		}
	}

	private fun refreshSnapshot() {
		val activeCore = requireCore()
		val status = activeCore.status()
		_state.update {
			it.copy(
				status = CoreStatus(status.endpointId, status.activeTransfers, status.activeShares),
				transfers = activeCore.listTransfers().map(StoredTransfer::toModel),
				events = activeCore.listEvents(null).map(CoreEvent::toModel).take(MaxEvents),
			)
		}
	}

	private suspend fun <T> runCore(block: suspend () -> T): Result<T> =
		withContext(dispatcher) {
			try {
				Result.success(block())
			} catch (error: Throwable) {
				if (error is CancellationException) throw error
				Result.failure(error)
			}
		}

	private fun requireCore(): VnidropCore = core ?: error("Initialize the core first.")

	private fun nextTransferId(): ULong = Random.nextLong(1, Long.MAX_VALUE).toULong()

	private companion object {
		const val MaxEvents = 200
	}
}

private fun CoreEvent.toModel(): CoreEventModel = CoreEventModel(
	id = id,
	timestamp = timestamp,
	scope = scope,
	transferId = transferId,
	direction = direction,
	phase = phase,
	kind = kind,
	dataJson = dataJson,
)

private fun StoredTransfer.toModel(): Transfer = Transfer(
	localId = localId,
	transferId = transferId,
	direction = direction,
	status = status,
	peerId = peerId,
	transferName = transferName,
	fileCount = fileCount,
	totalSize = totalSize,
	ticket = ticket,
)

private fun ShareResult.toModel(): Share = Share(
	transferId = transferId,
	ticket = ticket,
	transferName = transferName,
	contentHash = hash,
	fileCount = fileCount,
	totalSize = totalSize,
)

private fun TicketInspection.toModel(): TicketInspectionModel = TicketInspectionModel(
	kind = kind,
	blobTicket = blobTicket,
	metadata = metadata?.toModel(),
)

private fun TransferMetadata.toModel(): TransferMetadataModel = TransferMetadataModel(
	transferId = transferId,
	transferName = transferName,
	senderName = senderName,
	contentHash = contentHash,
	fileCount = fileCount,
	totalSize = totalSize,
)

private fun ReceiverRequest.toModel(): ReceiverRequestModel = ReceiverRequestModel(
	id = id,
	transferId = transferId,
	remoteEndpointId = remoteEndpointId,
	transferName = transferName,
	receiverName = receiverName,
	receiverDeviceName = receiverDeviceName,
	appVersion = appVersion,
	status = status,
	reason = reason,
	requestedAt = requestedAt,
	respondedAt = respondedAt,
)
