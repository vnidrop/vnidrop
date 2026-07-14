package com.vnidrop.app.diagnostics

import com.vnidrop.app.preferences.PreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Owns diagnostics services for the app process: telemetry, crashes, bug reports.
 *
 * When [DiagnosticsBuildConfig.INCLUDED] is false (compile-time), telemetry and
 * crash auto-reporting are never started; [bugReports] still works for support.
 */
class DiagnosticsCoordinator(
	val preferencesRepository: PreferencesRepository,
	val transport: DiagnosticsTransport,
	val breadcrumbs: BreadcrumbBuffer,
	val telemetry: TelemetryRecorder,
	val crashReporter: CrashReporter,
	val bugReports: BugReportService,
	private val scope: CoroutineScope,
) {
	fun start() {
		// Install id is useful for bug-report correlation even without telemetry.
		scope.launch {
			preferencesRepository.ensureDiagnosticsInstallId()
		}
		if (!DiagnosticsBuildConfig.INCLUDED) return
		crashReporter.startObservingPreferences()
		crashReporter.installUnhandledExceptionHandler()
		scope.launch {
			crashReporter.flushPending()
		}
	}

	fun record(name: String, properties: Map<String, String> = emptyMap()) {
		if (!DiagnosticsBuildConfig.INCLUDED) return
		telemetry.record(name, properties)
	}

	companion object {
		fun create(
			appDataDir: String,
			appVersion: String,
			platform: String,
			preferencesRepository: PreferencesRepository,
			scope: CoroutineScope,
			transport: DiagnosticsTransport = NoOpDiagnosticsTransport(),
		): DiagnosticsCoordinator {
			val breadcrumbs = BreadcrumbBuffer()
			val crashStore = createPendingCrashStore(appDataDir)
			val telemetry = TelemetryRecorder(
				preferencesRepository = preferencesRepository,
				transport = transport,
				breadcrumbs = breadcrumbs,
				scope = scope,
			)
			val crashReporter = CrashReporter(
				store = crashStore,
				preferencesRepository = preferencesRepository,
				transport = transport,
				breadcrumbs = breadcrumbs,
				appVersion = appVersion,
				platform = platform,
				scope = scope,
			)
			val bugReports = BugReportService(
				preferencesRepository = preferencesRepository,
				transport = transport,
				breadcrumbs = breadcrumbs,
				appVersion = appVersion,
				platform = platform,
			)
			return DiagnosticsCoordinator(
				preferencesRepository = preferencesRepository,
				transport = transport,
				breadcrumbs = breadcrumbs,
				telemetry = telemetry,
				crashReporter = crashReporter,
				bugReports = bugReports,
				scope = scope,
			)
		}
	}
}
