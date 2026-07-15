package com.vnidrop.app.diagnostics

import com.vnidrop.app.DeviceInfo
import com.vnidrop.app.core.ReceiveFolder
import com.vnidrop.app.core.ReceiveFolderKind
import com.vnidrop.app.preferences.AppPreferences
import com.vnidrop.app.preferences.PreferencesRepository
import com.vnidrop.app.support.FakePreferencesRepository
import com.vnidrop.app.ui.theme.ThemeMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DiagnosticsTest {
	@Test
	fun diagnosticsJsonEscapesAndShapesPayloads() {
		val eventsJson = DiagnosticsJson.eventsBody(
			batchId = "batch-1",
			installId = "inst-1",
			appVersion = "1.0",
			platform = "Test",
			events = listOf(
				TelemetryEvent("app_open", 10L, mapOf("a" to "quote\"here")),
			),
		)
		assertTrue(eventsJson.contains("\"batchId\":\"batch-1\""))
		assertTrue(eventsJson.contains("\"installId\":\"inst-1\""))
		assertTrue(eventsJson.contains("\"name\":\"app_open\""))
		assertTrue(eventsJson.contains("quote\\\"here"))

		val crashJson = DiagnosticsJson.crashBody(
			CrashReport(
				id = "c1",
				timestampMillis = 1L,
				installId = "inst",
				appVersion = "1.0",
				platform = "Test",
				exceptionType = "E",
				exceptionMessage = "line\nbreak",
				stackTrace = "stack",
				breadcrumbs = emptyList(),
				diagnosticsEnabledAtCapture = true,
			),
		)
		assertTrue(crashJson.contains("line\\nbreak"))
		assertTrue(crashJson.contains("\"diagnosticsEnabledAtCapture\":true"))
	}

	@Test
	fun diagnosticsJsonKeepsEscapedBugPayloadWithinWorkerLimit() {
		val report = BugReport(
			id = "b",
			timestampMillis = 1L,
			installId = "i",
			appVersion = "1.0",
			platform = "Test",
			whatHappened = "w",
			expected = "e",
			steps = "",
			contact = "",
			includeLogs = true,
			logs = "\n".repeat(BugReportService.MaxLogBytes),
			device = DeviceSnapshot(null, null, "OS", null, null),
			breadcrumbs = emptyList(),
		)

		val body = DiagnosticsJson.bugBody(report)

		assertTrue(body.encodeToByteArray().size <= DiagnosticsJson.MaxRequestBytes)
		assertTrue(body.contains("\"includeLogs\":true"))
		assertTrue(body.endsWith("}"))
	}

	@Test
	fun httpTransportPostsExpectedPaths() = runTest {
		val calls = mutableListOf<Pair<String, String>>()
		val acknowledgementIds = ArrayDeque(listOf("batch-1", "c", "b"))
		val transport = HttpDiagnosticsTransport(
			baseUrl = "https://diag.example",
			ingestKey = "secret",
			appVersion = "1.0",
			platform = "Test",
			installIdProvider = { "install-x" },
			post = { url, headers, body ->
				assertEquals("secret", headers["X-VniDrop-Key"])
				calls += url to body
				PlatformHttpResponse(
					202,
					"""{"ok":true,"id":"${acknowledgementIds.removeFirst()}","stored":1}""",
				)
			},
		)
		val eventResult = transport.sendEvents(
			TelemetryBatch("batch-1", listOf(TelemetryEvent("nav", 1L))),
		)
		assertTrue(eventResult.isSuccess)
		assertEquals("https://diag.example/v1/events", calls[0].first)
		assertTrue(calls[0].second.contains("install-x"))

		val crashResult = transport.sendCrash(
			CrashReport(
				id = "c",
				timestampMillis = 1L,
				installId = "i",
				appVersion = "1.0",
				platform = "Test",
				exceptionType = "E",
				exceptionMessage = "m",
				stackTrace = "s",
				breadcrumbs = emptyList(),
				diagnosticsEnabledAtCapture = true,
			),
		)
		assertTrue(crashResult.isSuccess)
		assertEquals("https://diag.example/v1/crashes", calls[1].first)

		val bugResult = transport.sendBugReport(
			BugReport(
				id = "b",
				timestampMillis = 1L,
				installId = "i",
				appVersion = "1.0",
				platform = "Test",
				whatHappened = "w",
				expected = "e",
				steps = "",
				contact = "",
				includeLogs = false,
				logs = "",
				device = DeviceSnapshot(null, null, "OS", null, null),
				breadcrumbs = emptyList(),
			),
		)
		assertTrue(bugResult.isSuccess)
		assertEquals("https://diag.example/v1/bugs", calls[2].first)
	}

	@Test
	fun httpTransportParsesEscapedAcknowledgementWithNestedUnknownFields() = runTest {
		val transport = HttpDiagnosticsTransport(
			baseUrl = "https://diag.example",
			ingestKey = "secret",
			post = { _, _, _ ->
				PlatformHttpResponse(
					202,
					"""{"\u006f\u006b":true,"id":"batch-\u0031","metadata":{"stored":1,"flags":[true,null]}}""",
				)
			},
		)

		assertTrue(transport.sendEvents(TelemetryBatch("batch-1", listOf(TelemetryEvent("x", 1L)))).isSuccess)
	}

	@Test
	fun httpTransportFailsOnHttpError() = runTest {
		val transport = HttpDiagnosticsTransport(
			baseUrl = "https://diag.example",
			ingestKey = "secret",
			post = { _, _, _ -> PlatformHttpResponse(401, """{"error":"unauthorized"}""") },
		)
		assertTrue(
			transport.sendEvents(TelemetryBatch("batch-1", listOf(TelemetryEvent("x", 1L)))).isFailure,
		)
	}

	@Test
	fun httpTransportPropagatesCancellation() = runTest {
		val transport = HttpDiagnosticsTransport(
			baseUrl = "https://diag.example",
			ingestKey = "secret",
			post = { _, _, _ -> throw CancellationException("cancelled") },
		)

		assertFailsWith<CancellationException> {
			transport.sendEvents(TelemetryBatch("batch-1", listOf(TelemetryEvent("x", 1))))
		}
	}

	@Test
	fun httpTransportRejectsMissingOrNegativeAcknowledgement() = runTest {
		val responses = ArrayDeque(
			listOf(
				PlatformHttpResponse(202, ""),
				PlatformHttpResponse(202, """{"ok":false}"""),
				PlatformHttpResponse(202, """{,"ok":true}"""),
				PlatformHttpResponse(202, """{"ok":true,"id":"different"}"""),
				PlatformHttpResponse(202, """{"ok":true,"id":"batch-4","id":"batch-4"}"""),
			),
		)
		val transport = HttpDiagnosticsTransport(
			baseUrl = "https://diag.example",
			ingestKey = "secret",
			post = { _, _, _ -> responses.removeFirst() },
		)

		repeat(5) { index ->
			val result = transport.sendEvents(
				TelemetryBatch("batch-$index", listOf(TelemetryEvent("x", 1L))),
			)
			assertIs<DiagnosticsProtocolException>(result.exceptionOrNull())
		}
	}

	@Test
	fun httpTransportRejectsAmbiguousOrMalformedJsonAcknowledgement() = runTest {
		val responses = ArrayDeque(
			listOf(
				PlatformHttpResponse(202, """{"ok":true,"\u006f\u006b":true,"id":"batch-0"}"""),
				PlatformHttpResponse(202, """{"ok":true,"id":true}"""),
				PlatformHttpResponse(202, """{"ok":true,"id":"batch-2","stored":01}"""),
				PlatformHttpResponse(202, """{"ok":true,"id":"batch-3",}"""),
				PlatformHttpResponse(202, """{"ok":true,"id":"batch-4"} trailing"""),
			),
		)
		val transport = HttpDiagnosticsTransport(
			baseUrl = "https://diag.example",
			ingestKey = "secret",
			post = { _, _, _ -> responses.removeFirst() },
		)

		repeat(5) { index ->
			val result = transport.sendEvents(
				TelemetryBatch("batch-$index", listOf(TelemetryEvent("x", 1L))),
			)
			assertIs<DiagnosticsProtocolException>(result.exceptionOrNull())
		}
	}

	@Test
	fun httpTransportRequiresHttpsExceptForLoopbackDevelopment() {
		assertFailsWith<IllegalArgumentException> {
			HttpDiagnosticsTransport("http://diag.example", "secret")
		}
		assertFailsWith<IllegalArgumentException> {
			HttpDiagnosticsTransport("http://[::1].example", "secret")
		}
		HttpDiagnosticsTransport("http://localhost:8787", "secret")
		HttpDiagnosticsTransport("http://127.0.0.1:8787", "secret")
		HttpDiagnosticsTransport("http://[::1]:8787", "secret")
		assertFailsWith<IllegalArgumentException> {
			HttpDiagnosticsTransport("https://diag.example", "")
		}
	}

	@Test
	fun createTransportIsNoOpForExplicitEmptyConfiguration() {
		val transport = buildDiagnosticsTransport(
			endpoint = "",
			ingestKey = "",
			appVersion = "1.0",
			platform = "Test",
			installIdProvider = { "id" },
		)
		assertIs<NoOpDiagnosticsTransport>(transport)
	}

	@Test
	fun noOpTransportReportsUnavailableDelivery() = runTest {
		val result = NoOpDiagnosticsTransport().sendEvents(
			TelemetryBatch("batch-1", listOf(TelemetryEvent("x", 1L))),
		)
		assertIs<DiagnosticsUnavailableException>(result.exceptionOrNull())
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
	fun telemetryRetainsColdStartEventsUntilConsentLoads() = runTest {
		val backing = fakePrefs(diagnosticsEnabled = true)
		val preferenceGate = CompletableDeferred<Unit>()
		val delayedPreferences = object : PreferencesRepository by backing {
			override val preferences = flow {
				preferenceGate.await()
				emitAll(backing.preferences)
			}
		}
		val transport = RecordingDiagnosticsTransport()
		val recorder = TelemetryRecorder(
			preferencesRepository = delayedPreferences,
			transport = transport,
			breadcrumbs = BreadcrumbBuffer(),
			scope = TestScope(UnconfinedTestDispatcher(testScheduler)),
			flushThreshold = 20,
		)
		runCurrent()

		recorder.record("app_open")
		assertEquals(1, recorder.pendingCount())
		preferenceGate.complete(Unit)
		runCurrent()
		assertTrue(recorder.flush().isSuccess)

		assertEquals(listOf("app_open"), transport.events.single().map { it.name })
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
	fun telemetryFlushesSparseEventsAfterTheInterval() = runTest {
		val transport = RecordingDiagnosticsTransport()
		val recorder = TelemetryRecorder(
			preferencesRepository = fakePrefs(diagnosticsEnabled = true),
			transport = transport,
			breadcrumbs = BreadcrumbBuffer(),
			scope = TestScope(UnconfinedTestDispatcher(testScheduler)),
			flushThreshold = 20,
			flushIntervalMillis = 1_000,
		)
		advanceUntilIdle()
		recorder.record("sparse")

		advanceTimeBy(999)
		runCurrent()
		assertTrue(transport.eventBatches.isEmpty())
		advanceTimeBy(1)
		runCurrent()
		assertEquals(listOf("sparse"), transport.events.single().map { it.name })
	}

	@Test
	fun telemetryCoalescesAutomaticRetriesWithBackoff() = runTest {
		val transport = RecordingDiagnosticsTransport().apply {
			eventsResult = Result.failure(IllegalStateException("offline"))
		}
		val recorder = TelemetryRecorder(
			preferencesRepository = fakePrefs(diagnosticsEnabled = true),
			transport = transport,
			breadcrumbs = BreadcrumbBuffer(),
			scope = TestScope(UnconfinedTestDispatcher(testScheduler)),
			flushThreshold = 1,
			flushIntervalMillis = 10_000,
			retryBackoffMillis = 1_000,
			automaticRetryCount = 1,
		)
		advanceUntilIdle()
		recorder.record("retry")
		runCurrent()
		assertEquals(1, transport.eventBatches.size)

		advanceTimeBy(999)
		runCurrent()
		assertEquals(1, transport.eventBatches.size)
		advanceTimeBy(1)
		runCurrent()
		assertEquals(2, transport.eventBatches.size)
		advanceTimeBy(10_000)
		runCurrent()
		assertEquals(2, transport.eventBatches.size)
	}

	@Test
	fun telemetryFlushesAtMostFiftyEventsPerBatch() = runTest {
		val preferences = fakePrefs(diagnosticsEnabled = true)
		val transport = RecordingDiagnosticsTransport()
		val recorder = TelemetryRecorder(
			preferencesRepository = preferences,
			transport = transport,
			breadcrumbs = BreadcrumbBuffer(),
			scope = TestScope(UnconfinedTestDispatcher(testScheduler)),
			flushThreshold = 101,
			maxBufferSize = 100,
		)
		advanceUntilIdle()
		repeat(75) { recorder.record("event-$it") }

		assertTrue(recorder.flush().isSuccess)
		assertEquals(listOf(50, 25), transport.eventBatches.map { it.events.size })
		assertTrue(transport.eventBatches.all { it.events.size <= TelemetryRecorder.MaxEventsPerBatch })
	}

	@Test
	fun telemetrySplitsBatchesByEscapedRequestBytes() = runTest {
		val preferences = fakePrefs(diagnosticsEnabled = true)
		val requestBodies = mutableListOf<String>()
		val transport = HttpDiagnosticsTransport(
			baseUrl = "https://diag.example",
			ingestKey = "secret",
			appVersion = "1.0",
			platform = "Test",
			installIdProvider = { "test-install" },
			post = { _, _, body ->
				requestBodies += body
				val id = Regex(""""batchId":"([^"]+)"""").find(body)?.groupValues?.get(1)
				PlatformHttpResponse(202, """{"ok":true,"id":"$id"}""")
			},
		)
		val recorder = TelemetryRecorder(
			preferencesRepository = preferences,
			transport = transport,
			breadcrumbs = BreadcrumbBuffer(),
			scope = TestScope(UnconfinedTestDispatcher(testScheduler)),
			flushThreshold = 101,
			maxBufferSize = 100,
		)
		advanceUntilIdle()
		val properties = LinkedHashMap<String, String>().apply {
			repeat(MaxDiagnosticProperties) { index ->
				put("key-$index-${"\u0001".repeat(40)}", "\u0001".repeat(MaxDiagnosticPropertyValueBytes))
			}
		}
		repeat(50) { index ->
			recorder.record("event-$index-${"\u0001".repeat(64)}", properties)
		}

		assertTrue(recorder.flush().isSuccess)
		assertTrue(requestBodies.size > 1)
		assertTrue(requestBodies.all { it.encodeToByteArray().size <= DiagnosticsJson.MaxRequestBytes })
		assertEquals(50, requestBodies.sumOf { body -> "\"schemaVersion\"".toRegex().findAll(body).count() })
	}

	@Test
	fun telemetrySanitizesNamesAndPropertiesToServerByteLimits() = runTest {
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
		val properties = LinkedHashMap<String, String>().apply {
			repeat(20) { index -> put("key-$index-${"🙂".repeat(20)}", "🙂".repeat(100)) }
		}

		recorder.record("🙂".repeat(100), properties)
		assertTrue(recorder.flush().isSuccess)

		val event = transport.eventBatches.single().events.single()
		assertTrue(event.name.encodeToByteArray().size <= MaxDiagnosticNameBytes)
		assertEquals(MaxDiagnosticProperties, event.properties.size)
		assertTrue(event.properties.keys.all { it.encodeToByteArray().size <= MaxDiagnosticPropertyKeyBytes })
		assertTrue(event.properties.values.all { it.encodeToByteArray().size <= MaxDiagnosticPropertyValueBytes })
	}

	@Test
	fun telemetryKeepsConcurrentRecordsWithoutExceedingItsBuffer() = runTest {
		val preferences = fakePrefs(diagnosticsEnabled = true)
		val transport = RecordingDiagnosticsTransport()
		val recorder = TelemetryRecorder(
			preferencesRepository = preferences,
			transport = transport,
			breadcrumbs = BreadcrumbBuffer(),
			scope = TestScope(UnconfinedTestDispatcher(testScheduler)),
			flushThreshold = 101,
			maxBufferSize = 100,
		)
		advanceUntilIdle()

		coroutineScope {
			repeat(100) { index ->
				launch(Dispatchers.Default) { recorder.record("event-$index") }
			}
		}

		assertEquals(
			100,
			recorder.pendingCount() + transport.eventBatches.sumOf { it.events.size },
		)
		assertTrue(recorder.flush().isSuccess)
		assertEquals(100, transport.eventBatches.sumOf { it.events.size })
	}

	@Test
	fun telemetryRetryReusesBatchIdAndEvents() = runTest {
		val preferences = fakePrefs(diagnosticsEnabled = true)
		val transport = RecordingDiagnosticsTransport().apply {
			eventsResult = Result.failure(IllegalStateException("offline"))
		}
		val recorder = TelemetryRecorder(
			preferencesRepository = preferences,
			transport = transport,
			breadcrumbs = BreadcrumbBuffer(),
			scope = TestScope(UnconfinedTestDispatcher(testScheduler)),
			flushThreshold = 10,
		)
		advanceUntilIdle()
		recorder.record("one")
		recorder.record("two")

		assertTrue(recorder.flush().isFailure)
		val firstAttempt = transport.eventBatches.single()
		transport.eventsResult = Result.success(Unit)
		assertTrue(recorder.flush().isSuccess)

		assertEquals(listOf(firstAttempt, firstAttempt), transport.eventBatches)
		assertEquals(0, recorder.pendingCount())
	}

	@Test
	fun telemetryDoesNotRequeuePermanentlyRejectedPayload() = runTest {
		val preferences = fakePrefs(diagnosticsEnabled = true)
		val transport = RecordingDiagnosticsTransport().apply {
			eventsResult = Result.failure(DiagnosticsHttpException(400, "/v1/events"))
		}
		val recorder = TelemetryRecorder(
			preferencesRepository = preferences,
			transport = transport,
			breadcrumbs = BreadcrumbBuffer(),
			scope = TestScope(UnconfinedTestDispatcher(testScheduler)),
			flushThreshold = 10,
		)
		advanceUntilIdle()
		recorder.record("invalid")

		assertTrue(recorder.flush().isFailure)
		assertEquals(0, recorder.pendingCount())
		transport.eventsResult = Result.success(Unit)
		assertTrue(recorder.flush().isSuccess)
		assertEquals(1, transport.eventBatches.size)
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
			id = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
			timestampMillis = 42L,
			installId = "install",
			appVersion = "1.0",
			platform = "Test",
			exceptionType = "IllegalStateException",
			exceptionMessage = "boom\nline\\nliteral\u001fseparator",
			stackTrace = "stack\ntrace\u001erecord",
			breadcrumbs = listOf(
				Breadcrumb("open|send", 1L, mapOf("screen:key" to "send,value|next")),
			),
			diagnosticsEnabledAtCapture = true,
		)
		val decoded = CrashReportCodec.decode(CrashReportCodec.encode(original))
		assertEquals(original, decoded)
		assertNull(CrashReportCodec.decode(CrashReportCodec.encode(original.copy(id = "../../escape"))))
	}

	@Test
	fun crashCodecMigratesLegacyV1Envelope() {
		val raw = listOf(
			"id=bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb",
			"ts=42",
			"install=legacy-install",
			"app=0.9",
			"platform=Desktop",
			"type=IllegalStateException",
			"message=first\\nsecond",
			"stack=frame one\\nframe two",
			"diag=1",
			"schema=1",
			"crumbs=1|opened|screen:send",
		).joinToString("\u001f")

		val report = requireNotNull(CrashReportCodec.decode(raw))

		assertEquals("first\nsecond", report.exceptionMessage)
		assertEquals("frame one\nframe two", report.stackTrace)
		assertEquals(true, report.diagnosticsEnabledAtCapture)
		assertEquals(
			listOf(Breadcrumb("opened", 1, mapOf("screen" to "send"))),
			report.breadcrumbs,
		)
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
		assertEquals(1, store.list().size)

		reporter.flushPending()
		assertEquals(1, transport.crashes.size)
		assertEquals(optedIn.id, transport.crashes.single().id)
		assertTrue(store.list().isEmpty())
	}

	@Test
	fun crashReporterRetainsCrashWhenDeliveryIsUnavailable() = runTest {
		val preferences = fakePrefs(diagnosticsEnabled = true)
		val store = InMemoryPendingCrashStore()
		val reporter = CrashReporter(
			store = store,
			preferencesRepository = preferences,
			transport = NoOpDiagnosticsTransport(),
			breadcrumbs = BreadcrumbBuffer(),
			appVersion = "1.0",
			platform = "Test",
			scope = TestScope(UnconfinedTestDispatcher(testScheduler)),
		)
		reporter.capture(RuntimeException("boom"), diagnosticsEnabledOverride = true)

		reporter.flushPending()
		assertEquals(1, store.list().size)
	}

	@Test
	fun crashReporterDropsPermanentPayloadFailuresAndStopsAfterTransientFailures() = runTest {
		val preferences = fakePrefs(diagnosticsEnabled = true)
		val permanentTransport = RecordingDiagnosticsTransport().apply {
			crashResult = Result.failure(DiagnosticsHttpException(400, "/v1/crashes"))
		}
		val permanentStore = InMemoryPendingCrashStore()
		val permanentReporter = CrashReporter(
			store = permanentStore,
			preferencesRepository = preferences,
			transport = permanentTransport,
			breadcrumbs = BreadcrumbBuffer(),
			appVersion = "1.0",
			platform = "Test",
			scope = TestScope(UnconfinedTestDispatcher(testScheduler)),
		)
		permanentReporter.capture(RuntimeException("invalid"), diagnosticsEnabledOverride = true)
		permanentReporter.flushPending()
		assertTrue(permanentStore.list().isEmpty())

		val transientTransport = RecordingDiagnosticsTransport().apply {
			crashResult = Result.failure(IllegalStateException("offline"))
		}
		val transientStore = InMemoryPendingCrashStore()
		val transientReporter = CrashReporter(
			store = transientStore,
			preferencesRepository = preferences,
			transport = transientTransport,
			breadcrumbs = BreadcrumbBuffer(),
			appVersion = "1.0",
			platform = "Test",
			scope = TestScope(UnconfinedTestDispatcher(testScheduler)),
		)
		repeat(2) {
			transientReporter.capture(RuntimeException("offline-$it"), diagnosticsEnabledOverride = true)
		}
		transientReporter.flushPending()
		assertEquals(1, transientTransport.crashes.size)
		assertEquals(2, transientStore.list().size)
	}

	@Test
	fun crashReporterResolvesStartupConsentBeforeUploading() = runTest {
		val transport = RecordingDiagnosticsTransport()
		val store = InMemoryPendingCrashStore()
		val reporter = CrashReporter(
			store = store,
			preferencesRepository = fakePrefs(diagnosticsEnabled = true),
			transport = transport,
			breadcrumbs = BreadcrumbBuffer(),
			appVersion = "1.0",
			platform = "Test",
			scope = TestScope(UnconfinedTestDispatcher(testScheduler)),
		)
		val startupCrash = reporter.capture(RuntimeException("startup"))
		assertNull(startupCrash.diagnosticsEnabledAtCapture)

		reporter.flushPending()

		assertEquals(true, transport.crashes.single().diagnosticsEnabledAtCapture)
		assertEquals("test-install", transport.crashes.single().installId)
		assertTrue(store.list().isEmpty())
	}

	@Test
	fun crashReporterDoesNotRetroactivelyUploadAnOptedOutStartupCrash() = runTest {
		val preferences = fakePrefs(diagnosticsEnabled = false)
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
		reporter.capture(RuntimeException("startup"))

		reporter.flushPending()
		assertTrue(store.list().isEmpty())
		preferences.setDiagnosticsEnabled(true)
		reporter.flushPending()

		assertTrue(transport.crashes.isEmpty())
		assertTrue(store.list().isEmpty())
	}

	@Test
	fun crashReporterStopsAFlushWhenTheUserOptsOut() = runTest {
		val preferences = fakePrefs(diagnosticsEnabled = true)
		val firstSendStarted = CompletableDeferred<Unit>()
		val releaseFirstSend = CompletableDeferred<Unit>()
		val sentIds = mutableListOf<String>()
		val transport = object : DiagnosticsTransport {
			override suspend fun sendEvents(batch: TelemetryBatch) = Result.success(Unit)
			override suspend fun sendBugReport(report: BugReport) = Result.success(Unit)
			override suspend fun sendCrash(report: CrashReport): Result<Unit> {
				sentIds += report.id
				if (sentIds.size == 1) {
					firstSendStarted.complete(Unit)
					releaseFirstSend.await()
				}
				return Result.success(Unit)
			}
		}
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
		repeat(2) {
			reporter.capture(RuntimeException("crash-$it"), diagnosticsEnabledOverride = true)
		}

		val flush = launch { reporter.flushPending() }
		firstSendStarted.await()
		preferences.setDiagnosticsEnabled(false)
		runCurrent()
		releaseFirstSend.complete(Unit)
		flush.join()

		assertEquals(1, sentIds.size)
		assertTrue(store.list().isEmpty())
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
	fun bugReportConvertsTransportExceptionsToFailure() = runTest {
		val throwingTransport = object : DiagnosticsTransport {
			override suspend fun sendEvents(batch: TelemetryBatch) = Result.success(Unit)
			override suspend fun sendCrash(report: CrashReport) = Result.success(Unit)
			override suspend fun sendBugReport(report: BugReport): Result<Unit> {
				throw IllegalStateException("offline")
			}
		}
		val service = BugReportService(
			preferencesRepository = fakePrefs(),
			transport = throwingTransport,
			breadcrumbs = BreadcrumbBuffer(),
			appVersion = "1.0",
			platform = "Test",
		)

		val result = service.submit(BugReportDraft("what", "expected"), device())

		assertTrue(result.isFailure)
		assertEquals("offline", result.exceptionOrNull()?.message)
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

	@Test
	fun bugReportRedactsLogsBeforeApplyingUtf8Limit() {
		val secret = "abcdefghijklmnopqrstuvwxyz012345"
		val rawLogs = "ticket=$secret\n".repeat(6_000) + "tail-marker"
		val service = BugReportService(
			preferencesRepository = fakePrefs(),
			transport = RecordingDiagnosticsTransport(),
			breadcrumbs = BreadcrumbBuffer(),
			appVersion = "1.0",
			platform = "Test",
			logReader = { rawLogs },
		)

		val logs = service.assemble(BugReportDraft("what", "expected"), device(), "install").logs

		assertFalse(logs.contains(secret))
		assertTrue(logs.endsWith("tail-marker"))
		assertTrue(logs.encodeToByteArray().size <= BugReportService.MaxLogBytes)
	}

	@Test
	fun bugReportLogLimitCountsUtf8Bytes() {
		val service = BugReportService(
			preferencesRepository = fakePrefs(),
			transport = RecordingDiagnosticsTransport(),
			breadcrumbs = BreadcrumbBuffer(),
			appVersion = "1.0",
			platform = "Test",
			logReader = { "🙂".repeat(60_000) },
		)

		val logs = service.assemble(BugReportDraft("what", "expected"), device(), "install").logs

		assertEquals(BugReportService.MaxLogBytes, logs.encodeToByteArray().size)
		assertEquals(BugReportService.MaxLogBytes, service.previewLogBytes())
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
	override fun prune(olderThanTimestampMillis: Long, maxCount: Int) {
		items.values
			.sortedByDescending { it.timestampMillis }
			.drop(maxCount)
			.map(CrashReport::id)
			.forEach(items::remove)
		items.values
			.filter { it.timestampMillis < olderThanTimestampMillis }
			.map(CrashReport::id)
			.forEach(items::remove)
	}
}
