package com.vnidrop.app.diagnostics

/** Network boundary for diagnostics; keep batching and validation client-side. */
interface DiagnosticsTransport {
	suspend fun sendEvents(batch: TelemetryBatch): Result<Unit>
	suspend fun sendCrash(report: CrashReport): Result<Unit>
	suspend fun sendBugReport(report: BugReport): Result<Unit>
}

internal class DiagnosticsUnavailableException : IllegalStateException("diagnostics delivery is not configured")

internal fun Throwable.isPermanentDiagnosticsPayloadRejection(): Boolean =
	this is DiagnosticsPayloadException ||
		(this is DiagnosticsHttpException && statusCode in setOf(400, 413, 415, 422))

/** Fails delivery without leaving the device. Used until a remote endpoint is configured. */
class NoOpDiagnosticsTransport : DiagnosticsTransport {
	override suspend fun sendEvents(batch: TelemetryBatch): Result<Unit> = unavailable()
	override suspend fun sendCrash(report: CrashReport): Result<Unit> = unavailable()
	override suspend fun sendBugReport(report: BugReport): Result<Unit> = unavailable()

	private fun unavailable(): Result<Unit> = Result.failure(DiagnosticsUnavailableException())
}

/**
 * Test double that records calls and can fail on demand.
 */
class RecordingDiagnosticsTransport : DiagnosticsTransport {
	val eventBatches = mutableListOf<TelemetryBatch>()
	val events: List<List<TelemetryEvent>>
		get() = eventBatches.map(TelemetryBatch::events)
	val crashes = mutableListOf<CrashReport>()
	val bugReports = mutableListOf<BugReport>()
	var eventsResult: Result<Unit> = Result.success(Unit)
	var crashResult: Result<Unit> = Result.success(Unit)
	var bugResult: Result<Unit> = Result.success(Unit)

	override suspend fun sendEvents(batch: TelemetryBatch): Result<Unit> {
		eventBatches += batch
		return eventsResult
	}

	override suspend fun sendCrash(report: CrashReport): Result<Unit> {
		crashes += report
		return crashResult
	}

	override suspend fun sendBugReport(report: BugReport): Result<Unit> {
		bugReports += report
		return bugResult
	}
}
