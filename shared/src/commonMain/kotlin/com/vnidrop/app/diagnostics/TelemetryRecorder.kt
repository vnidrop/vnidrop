package com.vnidrop.app.diagnostics

import com.vnidrop.app.logging.platformNowMillis
import com.vnidrop.app.preferences.PreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Product telemetry: sparse events, gated by diagnostics opt-in.
 * Events are buffered and flushed in batches when transport is available.
 */
class TelemetryRecorder(
	private val preferencesRepository: PreferencesRepository,
	private val transport: DiagnosticsTransport,
	private val breadcrumbs: BreadcrumbBuffer,
	private val scope: CoroutineScope,
	private val maxBufferSize: Int = DefaultMaxBuffer,
	private val flushThreshold: Int = DefaultFlushThreshold,
) {
	private val bufferMutex = Mutex()
	@Volatile private var buffer: List<TelemetryEvent> = emptyList()
	@Volatile private var enabled: Boolean = false

	init {
		scope.launch {
			preferencesRepository.preferences
				.map { it.diagnosticsEnabled }
				.distinctUntilChanged()
				.collect { isEnabled ->
					enabled = isEnabled
					if (!isEnabled) {
						buffer = emptyList()
					}
				}
		}
	}

	fun record(name: String, properties: Map<String, String> = emptyMap()) {
		if (!DiagnosticsBuildConfig.INCLUDED) return
		val redacted = LogRedactor.redactMap(properties)
		breadcrumbs.add(name, redacted)
		if (!enabled) return
		val event = TelemetryEvent(
			name = name.take(MaxNameLength),
			timestampMillis = platformNowMillis(),
			properties = redacted.mapValues { it.value.take(MaxPropertyValueLength) },
		)
		val next = (buffer + event).takeLast(maxBufferSize)
		buffer = next
		if (next.size >= flushThreshold) {
			scope.launch { flush() }
		}
	}

	suspend fun flush(): Result<Unit> = bufferMutex.withLock {
		if (!enabled) {
			buffer = emptyList()
			return Result.success(Unit)
		}
		val batch = buffer
		if (batch.isEmpty()) return Result.success(Unit)
		buffer = emptyList()
		val result = transport.sendEvents(batch)
		if (result.isFailure) {
			// Re-queue on failure so a future transport can retry once online.
			buffer = (batch + buffer).takeLast(maxBufferSize)
		}
		return result
	}

	fun pendingCount(): Int = buffer.size

	companion object {
		const val DefaultMaxBuffer = 100
		const val DefaultFlushThreshold = 20
		private const val MaxNameLength = 64
		private const val MaxPropertyValueLength = 128
	}
}
