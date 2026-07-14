package com.vnidrop.app.diagnostics

/**
 * Client-side diagnostics payloads. Transport to Cloudflare (or elsewhere) is
 * intentionally abstracted; nothing here assumes a network backend.
 */

data class TelemetryEvent(
	val name: String,
	val timestampMillis: Long,
	val properties: Map<String, String> = emptyMap(),
	val schemaVersion: Int = DiagnosticsSchemaVersion,
)

data class Breadcrumb(
	val name: String,
	val timestampMillis: Long,
	val properties: Map<String, String> = emptyMap(),
)

data class CrashReport(
	val id: String,
	val timestampMillis: Long,
	val installId: String,
	val appVersion: String,
	val platform: String,
	val exceptionType: String,
	val exceptionMessage: String,
	val stackTrace: String,
	val breadcrumbs: List<Breadcrumb>,
	/** Whether diagnostics was opted in when the crash was captured. */
	val diagnosticsEnabledAtCapture: Boolean,
	val schemaVersion: Int = DiagnosticsSchemaVersion,
)

data class DeviceSnapshot(
	val deviceName: String?,
	val deviceModel: String?,
	val operatingSystem: String,
	val network: String?,
	val batteryLevel: String?,
)

data class BugReport(
	val id: String,
	val timestampMillis: Long,
	val installId: String,
	val appVersion: String,
	val platform: String,
	val whatHappened: String,
	val expected: String,
	val steps: String,
	val contact: String,
	val includeLogs: Boolean,
	val logs: String,
	val device: DeviceSnapshot,
	val breadcrumbs: List<Breadcrumb>,
	val schemaVersion: Int = DiagnosticsSchemaVersion,
)

const val DiagnosticsSchemaVersion: Int = 1
