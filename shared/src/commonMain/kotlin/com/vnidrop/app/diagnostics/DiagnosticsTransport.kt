package com.vnidrop.app.diagnostics

/**
 * Network boundary for diagnostics. Production will swap [NoOpDiagnosticsTransport]
 * for a Cloudflare Worker client; keep batching/validation client-side.
 */
interface DiagnosticsTransport {
	suspend fun sendEvents(events: List<TelemetryEvent>): Result<Unit>
	suspend fun sendCrash(report: CrashReport): Result<Unit>
	suspend fun sendBugReport(report: BugReport): Result<Unit>
}

/** Accepts payloads without leaving the device. Used until Cloudflare is wired. */
class NoOpDiagnosticsTransport : DiagnosticsTransport {
	override suspend fun sendEvents(events: List<TelemetryEvent>): Result<Unit> = Result.success(Unit)
	override suspend fun sendCrash(report: CrashReport): Result<Unit> = Result.success(Unit)
	override suspend fun sendBugReport(report: BugReport): Result<Unit> = Result.success(Unit)
}

/**
 * Test double that records calls and can fail on demand.
 */
class RecordingDiagnosticsTransport : DiagnosticsTransport {
	val events = mutableListOf<List<TelemetryEvent>>()
	val crashes = mutableListOf<CrashReport>()
	val bugReports = mutableListOf<BugReport>()
	var eventsResult: Result<Unit> = Result.success(Unit)
	var crashResult: Result<Unit> = Result.success(Unit)
	var bugResult: Result<Unit> = Result.success(Unit)

	override suspend fun sendEvents(events: List<TelemetryEvent>): Result<Unit> {
		this.events += events
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
