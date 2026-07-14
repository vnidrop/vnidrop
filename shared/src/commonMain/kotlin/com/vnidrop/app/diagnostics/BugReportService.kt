package com.vnidrop.app.diagnostics

import com.vnidrop.app.DeviceInfo
import com.vnidrop.app.logging.AppLogger
import com.vnidrop.app.logging.platformNowMillis
import com.vnidrop.app.preferences.PreferencesRepository
import com.vnidrop.app.util.randomUuidString

data class BugReportDraft(
	val whatHappened: String,
	val expected: String,
	val steps: String = "",
	val contact: String = "",
	val includeLogs: Boolean = true,
)

/**
 * User-initiated bug reports. Always allowed regardless of diagnostics opt-in.
 */
class BugReportService(
	private val preferencesRepository: PreferencesRepository,
	private val transport: DiagnosticsTransport,
	private val breadcrumbs: BreadcrumbBuffer,
	private val appVersion: String,
	private val platform: String,
	private val logReader: () -> String = {
		LogRedactor.redact(AppLogger.readLatestLogs(AppLogger.DefaultBugReportLogBytes))
	},
) {
	fun assemble(
		draft: BugReportDraft,
		deviceInfo: DeviceInfo?,
		installId: String,
	): BugReport {
		val logs = if (draft.includeLogs) logReader() else ""
		return BugReport(
			id = randomUuidString(),
			timestampMillis = platformNowMillis(),
			installId = installId,
			appVersion = appVersion,
			platform = platform,
			whatHappened = draft.whatHappened.trim().take(MaxFieldLength),
			expected = draft.expected.trim().take(MaxFieldLength),
			steps = draft.steps.trim().take(MaxFieldLength),
			contact = draft.contact.trim().take(MaxContactLength),
			includeLogs = draft.includeLogs,
			logs = logs,
			device = DeviceSnapshot(
				deviceName = deviceInfo?.deviceName,
				deviceModel = deviceInfo?.deviceModel,
				operatingSystem = deviceInfo?.operatingSystem ?: platform,
				network = deviceInfo?.network,
				batteryLevel = deviceInfo?.batteryLevel,
			),
			breadcrumbs = breadcrumbs.snapshot(),
		)
	}

	suspend fun submit(draft: BugReportDraft, deviceInfo: DeviceInfo?): Result<BugReport> {
		val what = draft.whatHappened.trim()
		val expected = draft.expected.trim()
		if (what.isEmpty()) {
			return Result.failure(IllegalArgumentException("Describe what happened."))
		}
		if (expected.isEmpty()) {
			return Result.failure(IllegalArgumentException("Describe what you expected."))
		}
		val installId = preferencesRepository.ensureDiagnosticsInstallId()
		val report = assemble(draft, deviceInfo, installId)
		val send = transport.sendBugReport(report)
		return send.fold(
			onSuccess = {
				AppLogger.info("bug_report", "submitted", mapOf("id" to report.id))
				Result.success(report)
			},
			onFailure = { error ->
				AppLogger.error("bug_report", "submit failed", error)
				Result.failure(error)
			},
		)
	}

	fun previewLogBytes(): Int = logReader().encodeToByteArray().size

	companion object {
		private const val MaxFieldLength = 4_000
		private const val MaxContactLength = 320
	}
}
