package com.vnidrop.app.diagnostics

import com.vnidrop.app.logging.AppLogger
import com.vnidrop.app.logging.platformNowMillis
import com.vnidrop.app.preferences.PreferencesRepository
import com.vnidrop.app.util.randomUuidString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Captures uncaught exceptions to disk, then uploads on a later launch when
 * diagnostics is enabled (and when a real [DiagnosticsTransport] is wired).
 */
class CrashReporter(
	private val store: PendingCrashStore,
	private val preferencesRepository: PreferencesRepository,
	private val transport: DiagnosticsTransport,
	private val breadcrumbs: BreadcrumbBuffer,
	private val appVersion: String,
	private val platform: String,
	private val scope: CoroutineScope,
) {
	@Volatile private var installed = false
	@Volatile private var lastInstallId: String = ""
	@Volatile private var lastDiagnosticsEnabled: Boolean = false

	fun startObservingPreferences() {
		scope.launch {
			preferencesRepository.preferences.collect { prefs ->
				lastInstallId = prefs.diagnosticsInstallId
				lastDiagnosticsEnabled = prefs.diagnosticsEnabled
			}
		}
	}

	fun installUnhandledExceptionHandler() {
		if (!DiagnosticsBuildConfig.INCLUDED) return
		if (installed) return
		installed = true
		installPlatformCrashHook { throwable ->
			capture(throwable)
		}
	}

	fun capture(throwable: Throwable, diagnosticsEnabledOverride: Boolean? = null): CrashReport {
		val report = CrashReport(
			id = randomUuidString(),
			timestampMillis = platformNowMillis(),
			installId = lastInstallId,
			appVersion = appVersion,
			platform = platform,
			exceptionType = throwable::class.simpleName ?: "Throwable",
			exceptionMessage = LogRedactor.redact(throwable.message.orEmpty()).take(MaxMessageLength),
			stackTrace = LogRedactor.redact(throwable.stackTraceToString()).take(MaxStackLength),
			breadcrumbs = breadcrumbs.snapshot(),
			diagnosticsEnabledAtCapture = diagnosticsEnabledOverride ?: lastDiagnosticsEnabled,
		)
		runCatching { store.write(report) }
		AppLogger.error("crash", "captured crash ${report.id}", throwable)
		return report
	}

	/**
	 * Uploads pending crashes that were captured with diagnostics enabled.
	 * Local files are always kept for bug-report attachment until deleted after successful send.
	 */
	suspend fun flushPending() {
		val diagnosticsEnabled = preferencesRepository.preferences.first().diagnosticsEnabled
		if (!diagnosticsEnabled) return
		for (report in store.list()) {
			// Only auto-upload crashes captured while diagnostics was on.
			if (!report.diagnosticsEnabledAtCapture) continue
			val result = transport.sendCrash(report)
			if (result.isSuccess) {
				store.delete(report.id)
			}
		}
	}

	fun latestLocalCrash(): CrashReport? = store.list().maxByOrNull { it.timestampMillis }

	companion object {
		private const val MaxMessageLength = 2_000
		private const val MaxStackLength = 32_000
	}
}

expect fun installPlatformCrashHook(onCrash: (Throwable) -> Unit)
