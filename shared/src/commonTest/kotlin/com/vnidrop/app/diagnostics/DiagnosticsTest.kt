package com.vnidrop.app.diagnostics

import com.vnidrop.app.DeviceInfo
import com.vnidrop.app.preferences.AppPreferences
import com.vnidrop.app.core.ReceiveFolder
import com.vnidrop.app.core.ReceiveFolderKind
import com.vnidrop.app.support.FakePreferencesRepository
import com.vnidrop.app.ui.theme.ThemeMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DiagnosticsTest {
	@Test
	fun diagnosticsBuildConfigDefaultsToIncluded() {
		// Production default is true; builds can override with -Pvnidrop.diagnostics.included=false.
		assertTrue(DiagnosticsBuildConfig.INCLUDED)
	}

	@Test
	fun logRedactorScrubsTicketsPathsAndEndpointIds() {
		val input = """
			ticket=abcdefghijklmnopqrstuvwxyz012345
			endpoint_id=peerABCDEFGHIJKLMNOP
			path=/Users/me/secret/file.bin
			uri=content://com.android.providers/downloads/1
			ok=value
		""".trimIndent()
		val redacted = LogRedactor.redact(input)
		assertFalse(redacted.contains("abcdefghijklmnopqrstuvwxyz012345"))
		assertFalse(redacted.contains("peerABCDEFGHIJKLMNOP"))
		assertFalse(redacted.contains("/users/me/secret/file.bin", ignoreCase = true))
		assertFalse(redacted.contains("content://"))
		assertTrue(redacted.contains("ok=value"))
		assertTrue(redacted.contains("[redacted-ticket]"))
		assertTrue(redacted.contains("[redacted-endpoint]"))
	}

	@Test
	fun breadcrumbBufferKeepsOnlyLatestEntries() {
		val buffer = BreadcrumbBuffer(capacity = 3)
		buffer.add("a")
		buffer.add("b")
		buffer.add("c")
		buffer.add("d")
		assertEquals(listOf("b", "c", "d"), buffer.snapshot().map { it.name })
	}

	@Test
	fun telemetryIgnoresEventsWhenDiagnosticsDisabled() = runTest {
		val preferences = fakePrefs(diagnosticsEnabled = false)
		val transport = RecordingDiagnosticsTransport()
		val breadcrumbs = BreadcrumbBuffer()
		val recorder = TelemetryRecorder(
			preferencesRepository = preferences,
			transport = transport,
			breadcrumbs = breadcrumbs,
			scope = TestScope(UnconfinedTestDispatcher(testScheduler)),
			flushThreshold = 1,
		)
		advanceUntilIdle()
		recorder.record("app_open")
		advanceUntilIdle()
		assertEquals(0, transport.events.size)
		assertEquals(1, breadcrumbs.snapshot().size)
	}

	@Test
	fun telemetryBuffersAndFlushesWhenEnabled() = runTest {
		val preferences = fakePrefs(diagnosticsEnabled = true)
		val transport = RecordingDiagnosticsTransport()
		val recorder = TelemetryRecorder(
			preferencesRepository = preferences,
			transport = transport,
			breadcrumbs = BreadcrumbBuffer(),
			scope = TestScope(UnconfinedTestDispatcher(testScheduler)),
			flushThreshold = 2,
			maxBufferSize = 10,
		)
		advanceUntilIdle()
		recorder.record("one")
		recorder.record("two")
		advanceUntilIdle()
		assertEquals(1, transport.events.size)
		assertEquals(listOf("one", "two"), transport.events.single().map { it.name })
	}

	@Test
	fun telemetryClearsBufferWhenOptedOut() = runTest {
		val preferences = fakePrefs(diagnosticsEnabled = true)
		val transport = RecordingDiagnosticsTransport()
		val recorder = TelemetryRecorder(
			preferencesRepository = preferences,
			transport = transport,
			breadcrumbs = BreadcrumbBuffer(),
			scope = TestScope(UnconfinedTestDispatcher(testScheduler)),
			flushThreshold = 100,
		)
		advanceUntilIdle()
		recorder.record("queued")
		assertEquals(1, recorder.pendingCount())
		preferences.setDiagnosticsEnabled(false)
		advanceUntilIdle()
		assertEquals(0, recorder.pendingCount())
	}

	@Test
	fun crashCodecRoundTrips() {
		val original = CrashReport(
			id = "crash-1",
			timestampMillis = 42L,
			installId = "install",
			appVersion = "1.0",
			platform = "Test",
			exceptionType = "IllegalStateException",
			exceptionMessage = "boom\nline",
			stackTrace = "stack\ntrace",
			breadcrumbs = listOf(
				Breadcrumb("open", 1L, mapOf("screen" to "send")),
			),
			diagnosticsEnabledAtCapture = true,
		)
		val decoded = CrashReportCodec.decode(CrashReportCodec.encode(original))
		assertEquals(original, decoded)
	}

	@Test
	fun crashReporterPersistsAndFlushesOnlyOptedInCrashes() = runTest {
		val preferences = fakePrefs(diagnosticsEnabled = true)
		val transport = RecordingDiagnosticsTransport()
		val store = InMemoryPendingCrashStore()
		val reporter = CrashReporter(
			store = store,
			preferencesRepository = preferences,
			transport = transport,
			breadcrumbs = BreadcrumbBuffer(),
			appVersion = "1.0",
			platform = "Test",
			scope = TestScope(UnconfinedTestDispatcher(testScheduler)),
		)
		reporter.startObservingPreferences()
		advanceUntilIdle()

		val optedIn = reporter.capture(RuntimeException("a"), diagnosticsEnabledOverride = true)
		reporter.capture(RuntimeException("b"), diagnosticsEnabledOverride = false)
		assertEquals(2, store.list().size)

		reporter.flushPending()
		assertEquals(1, transport.crashes.size)
		assertEquals(optedIn.id, transport.crashes.single().id)
		assertEquals(1, store.list().size)
		assertFalse(store.list().single().diagnosticsEnabledAtCapture)
	}

	@Test
	fun bugReportRequiresWhatAndExpected() = runTest {
		val preferences = fakePrefs()
		val service = BugReportService(
			preferencesRepository = preferences,
			transport = RecordingDiagnosticsTransport(),
			breadcrumbs = BreadcrumbBuffer(),
			appVersion = "1.0",
			platform = "Test",
			logReader = { "logs" },
		)
		assertTrue(service.submit(BugReportDraft("", "expected"), device()).isFailure)
		assertTrue(service.submit(BugReportDraft("what", ""), device()).isFailure)
	}

	@Test
	fun bugReportSubmitsWithRedactedLogsRegardlessOfDiagnostics() = runTest {
		val preferences = fakePrefs(diagnosticsEnabled = false)
		val transport = RecordingDiagnosticsTransport()
		val service = BugReportService(
			preferencesRepository = preferences,
			transport = transport,
			breadcrumbs = BreadcrumbBuffer(),
			appVersion = "1.0",
			platform = "Test",
			logReader = { LogRedactor.redact("ticket=abcdefghijklmnopqrstuvwxyz012345 plain") },
		)
		val result = service.submit(
			BugReportDraft(
				whatHappened = "crash on receive",
				expected = "receive succeeds",
				steps = "open ticket",
				contact = "user@example.com",
				includeLogs = true,
			),
			device(),
		)
		assertTrue(result.isSuccess)
		assertEquals(1, transport.bugReports.size)
		val report = transport.bugReports.single()
		assertEquals("crash on receive", report.whatHappened)
		assertTrue(report.logs.contains("[redacted-ticket]"))
		assertFalse(report.logs.contains("abcdefghijklmnopqrstuvwxyz012345"))
		assertEquals("test-install", report.installId)
	}

	private fun fakePrefs(diagnosticsEnabled: Boolean = false) = FakePreferencesRepository(
		AppPreferences(
			username = "User",
			receiveFolder = ReceiveFolder(ReceiveFolderKind.FileSystemPath, "/tmp", "tmp"),
			themeMode = ThemeMode.System,
			notificationsEnabled = false,
			diagnosticsEnabled = diagnosticsEnabled,
			diagnosticsInstallId = "test-install",
		),
	)

	private fun device() = DeviceInfo("Phone", "Pixel", "Android 15", "Wi-Fi", "90%")
}

private class InMemoryPendingCrashStore : PendingCrashStore {
	private val items = linkedMapOf<String, CrashReport>()
	override fun write(report: CrashReport) {
		items[report.id] = report
	}
	override fun list(): List<CrashReport> = items.values.sortedByDescending { it.timestampMillis }
	override fun delete(id: String) {
		items.remove(id)
	}
}
