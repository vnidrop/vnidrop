package com.vnidrop.app.diagnostics

import com.vnidrop.app.logging.platformNowMillis
import com.vnidrop.app.preferences.PreferencesRepository
import com.vnidrop.app.util.randomUuidString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Product telemetry: sparse events, gated by diagnostics opt-in.
 * Events are buffered and flushed in batches when transport is available.
 */
@OptIn(ExperimentalAtomicApi::class)
class TelemetryRecorder(
	private val preferencesRepository: PreferencesRepository,
	private val transport: DiagnosticsTransport,
	private val breadcrumbs: BreadcrumbBuffer,
	private val scope: CoroutineScope,
	private val maxBufferSize: Int = DefaultMaxBuffer,
	private val flushThreshold: Int = DefaultFlushThreshold,
	private val flushIntervalMillis: Long = DefaultFlushIntervalMillis,
	private val retryBackoffMillis: Long = DefaultRetryBackoffMillis,
	private val automaticRetryCount: Int = DefaultAutomaticRetryCount,
) {
	private val bufferMutex = Mutex()
	private val state = AtomicReference(TelemetryState())
	private val flushSignals = Channel<Unit>(Channel.CONFLATED)

	init {
		require(maxBufferSize > 0) { "maxBufferSize must be positive" }
		require(flushThreshold > 0) { "flushThreshold must be positive" }
		require(flushIntervalMillis > 0) { "flushIntervalMillis must be positive" }
		require(retryBackoffMillis > 0) { "retryBackoffMillis must be positive" }
		require(automaticRetryCount >= 0) { "automaticRetryCount must not be negative" }
		scope.launch {
			preferencesRepository.preferences
				.map { it.diagnosticsEnabled }
				.distinctUntilChanged()
				.collect { isEnabled ->
					updateState { current ->
						if (isEnabled) current.copy(enabled = true) else TelemetryState(enabled = false)
					}
					flushSignals.trySend(Unit)
				}
		}
		scope.launch { runAutomaticFlushes() }
	}

	fun record(name: String, properties: Map<String, String> = emptyMap()) {
		if (!DiagnosticsBuildConfig.INCLUDED) return
		val sanitizedName = sanitizeDiagnosticName(name)
		if (sanitizedName.isBlank()) return
		val sanitizedProperties = sanitizeDiagnosticProperties(properties)
		breadcrumbs.add(sanitizedName, sanitizedProperties)
		val event = TelemetryEvent(
			name = sanitizedName,
			timestampMillis = platformNowMillis(),
			properties = sanitizedProperties,
		)
		while (true) {
			val current = state.load()
			if (current.enabled == false) return
			val remainingCapacity =
				(maxBufferSize - current.retryBatch?.events.orEmpty().size).coerceAtLeast(0)
			val nextBuffer = (current.buffer + event).takeLast(remainingCapacity)
			if (state.compareAndSet(current, current.copy(buffer = nextBuffer))) {
				flushSignals.trySend(Unit)
				return
			}
		}
	}

	suspend fun flush(): Result<Unit> {
		return bufferMutex.withLock {
			var discardedFailure: Throwable? = null
			var outcome: Result<Unit>? = null
			while (outcome == null) {
				val current = state.load()
				if (current.enabled != true) return@withLock Result.success(Unit)
				val pendingRetry = current.retryBatch
				val events = if (pendingRetry == null) nextBatchEvents(current.buffer) else emptyList()
				if (pendingRetry == null && events.isEmpty()) {
					outcome = discardedFailure?.let { Result.failure(it) } ?: Result.success(Unit)
					continue
				}
				val batch: TelemetryBatch
				if (pendingRetry != null) {
					batch = pendingRetry
				} else {
					val prepared = TelemetryBatch(id = randomUuidString(), events = events)
					val next = current.copy(
						buffer = current.buffer.drop(events.size),
						retryBatch = prepared,
					)
					if (!state.compareAndSet(current, next)) continue
					batch = prepared
				}
				if (state.load().retryBatch != batch) continue
				val result = try {
					transport.sendEvents(batch)
				} catch (cancelled: CancellationException) {
					throw cancelled
				} catch (error: Throwable) {
					Result.failure(error)
				}
				if (result.isSuccess) {
					clearRetryBatch(batch)
					continue
				}
				val error = result.exceptionOrNull() ?: IllegalStateException("diagnostics event delivery failed")
				if (error.isPermanentDiagnosticsPayloadRejection()) {
					clearRetryBatch(batch)
					discardedFailure = discardedFailure ?: error
					continue
				}
				outcome = result
			}
			checkNotNull(outcome)
		}
	}

	private fun nextBatchEvents(events: List<TelemetryEvent>): List<TelemetryEvent> {
		if (events.isEmpty()) return emptyList()
		var minimum = 1
		var maximum = minOf(events.size, MaxEventsPerBatch)
		var accepted = 1
		while (minimum <= maximum) {
			val candidateSize = minimum + (maximum - minimum) / 2
			if (DiagnosticsJson.eventBatchFitsRequest(events.take(candidateSize))) {
				accepted = candidateSize
				minimum = candidateSize + 1
			} else {
				maximum = candidateSize - 1
			}
		}
		return events.take(accepted)
	}

	fun pendingCount(): Int {
		val current = state.load()
		return current.retryBatch?.events.orEmpty().size + current.buffer.size
	}

	private suspend fun runAutomaticFlushes() {
		while (true) {
			flushSignals.receive()
			var retries = 0
			while (true) {
				val current = state.load()
				if (current.enabled != true || pendingCount() == 0) break
				if (current.retryBatch == null && pendingCount() < flushThreshold) {
					val signalled = withTimeoutOrNull(flushIntervalMillis) {
						flushSignals.receive()
						true
					} ?: false
					if (signalled) continue
				}

				val result = flush()
				if (result.isSuccess || pendingCount() == 0) {
					retries = 0
					continue
				}
				val error = result.exceptionOrNull()
				if (error?.isPermanentDiagnosticsPayloadRejection() == true || retries >= automaticRetryCount) {
					break
				}
				retries += 1
				delay(retryBackoffMillis)
			}
		}
	}

	private fun clearRetryBatch(batch: TelemetryBatch) {
		while (true) {
			val current = state.load()
			if (current.retryBatch != batch) return
			if (state.compareAndSet(current, current.copy(retryBatch = null))) return
		}
	}

	private fun updateState(update: (TelemetryState) -> TelemetryState) {
		while (true) {
			val current = state.load()
			if (state.compareAndSet(current, update(current))) return
		}
	}

	companion object {
		const val DefaultMaxBuffer = 100
		const val DefaultFlushThreshold = 20
		const val MaxEventsPerBatch = 50
		const val DefaultFlushIntervalMillis = 30_000L
		const val DefaultRetryBackoffMillis = 30_000L
		const val DefaultAutomaticRetryCount = 3
	}
}

private data class TelemetryState(
	val enabled: Boolean? = null,
	val buffer: List<TelemetryEvent> = emptyList(),
	val retryBatch: TelemetryBatch? = null,
)
