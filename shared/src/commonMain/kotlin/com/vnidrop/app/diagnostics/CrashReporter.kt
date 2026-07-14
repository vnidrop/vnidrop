package com.vnidrop.app.diagnostics

import com.vnidrop.app.logging.AppLogger
import com.vnidrop.app.logging.platformNowMillis
import com.vnidrop.app.preferences.PreferencesRepository
import com.vnidrop.app.util.randomUuidString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Captures uncaught exceptions to disk, then uploads on a later launch when
 * diagnostics is enabled (and when a real [DiagnosticsTransport] is wired).
 */
@OptIn(ExperimentalAtomicApi::class)
class CrashReporter(
	private val store: PendingCrashStore,
	private val preferencesRepository: PreferencesRepository,
	private val transport: DiagnosticsTransport,
	private val breadcrumbs: BreadcrumbBuffer,
	private val appVersion: String,
	private val platform: String,
	private val scope: CoroutineScope,
) {
	private val installed = AtomicBoolean(false)
	private val observingPreferences = AtomicBoolean(false)
	private val capturePolicy = AtomicReference(CrashCapturePolicy())

	fun startObservingPreferences() {
		if (!observingPreferences.compareAndSet(false, true)) return
		scope.launch {
			preferencesRepository.preferences.collect { prefs ->
				capturePolicy.store(
					CrashCapturePolicy(
						installId = prefs.diagnosticsInstallId,
						diagnosticsEnabled = prefs.diagnosticsEnabled,
					),
				)
				if (!prefs.diagnosticsEnabled) {
					runCatching(::deleteAllPending)
				}
			}
		}
	}

	fun installUnhandledExceptionHandler() {
		if (!DiagnosticsBuildConfig.INCLUDED) return
		if (!installed.compareAndSet(false, true)) return
		installPlatformCrashHook { throwable ->
			capture(throwable)
		}
	}

	fun capture(throwable: Throwable, diagnosticsEnabledOverride: Boolean? = null): CrashReport {
		val policy = capturePolicy.load()
		val report = CrashReport(
			id = randomUuidString(),
			timestampMillis = platformNowMillis(),
			installId = sanitizeDiagnosticsInstallId(policy.installId),
			appVersion = appVersion.takeUtf8Bytes(DiagnosticsJson.MaxAppVersionBytes),
			platform = platform.takeUtf8Bytes(DiagnosticsJson.MaxPlatformBytes),
			exceptionType = throwable::class.simpleName ?: "Throwable",
			exceptionMessage = LogRedactor.redact(throwable.message.orEmpty()).takeUtf8Bytes(MaxMessageBytes),
			stackTrace = LogRedactor.redact(throwable.stackTraceToString()).takeUtf8Bytes(MaxStackBytes),
			breadcrumbs = breadcrumbs.snapshot(),
			diagnosticsEnabledAtCapture = diagnosticsEnabledOverride ?: policy.diagnosticsEnabled,
		)
		if (report.diagnosticsEnabledAtCapture != false) {
			runCatching { store.write(report) }
			runCatching {
				store.prune(
					olderThanTimestampMillis = platformNowMillis() - LocalRetentionMillis,
					maxCount = MaxLocalCrashCount,
				)
			}
		}
		AppLogger.error("crash", "captured crash ${report.id}", throwable)
		return report
	}

	/**
	 * Uploads pending crashes that were captured with diagnostics enabled.
	 * Local files are deleted after successful delivery or bounded by local retention.
	 */
	suspend fun flushPending() {
		store.prune(
			olderThanTimestampMillis = platformNowMillis() - LocalRetentionMillis,
			maxCount = MaxLocalCrashCount,
		)
		for (report in store.list()) {
			val preferences = preferencesRepository.preferences.first()
			if (!preferences.diagnosticsEnabled || capturePolicy.load().diagnosticsEnabled == false) {
				deleteAllPending()
				return
			}
			if (report.diagnosticsEnabledAtCapture == false) {
				store.delete(report.id)
				continue
			}
			val installId = sanitizeDiagnosticsInstallId(
				preferences.diagnosticsInstallId.ifBlank {
					preferencesRepository.ensureDiagnosticsInstallId()
				},
			)
			val resolved = report.copy(
				installId = report.installId.ifBlank { installId },
				diagnosticsEnabledAtCapture = true,
			)
			if (resolved != report) store.write(resolved)
			if (
				!preferencesRepository.preferences.first().diagnosticsEnabled ||
				capturePolicy.load().diagnosticsEnabled == false
			) {
				deleteAllPending()
				return
			}
			val result = try {
				transport.sendCrash(resolved)
			} catch (cancelled: CancellationException) {
				throw cancelled
			} catch (error: Throwable) {
				Result.failure(error)
			}
			if (result.isSuccess) {
				store.delete(resolved.id)
				continue
			}
			val error = result.exceptionOrNull()
			if (error?.isPermanentDiagnosticsPayloadRejection() == true) {
				store.delete(resolved.id)
				continue
			}
			break
		}
	}

	private fun deleteAllPending() {
		store.list().forEach { report -> store.delete(report.id) }
	}

	companion object {
		private const val MaxMessageBytes = 2_000
		private const val MaxStackBytes = 32_000
		private const val MaxLocalCrashCount = 20
		private const val LocalRetentionMillis = 30L * 86_400_000L
	}
}

private data class CrashCapturePolicy(
	val installId: String = "",
	val diagnosticsEnabled: Boolean? = null,
)

expect fun installPlatformCrashHook(onCrash: (Throwable) -> Unit)
