package com.vnidrop.app

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertTrue
import uniffi.vnidrop.CoreEvent
import uniffi.vnidrop.CoreEventSink
import uniffi.vnidrop.VnidropCore

class CoreNativeLoadTest {
	@Test
	fun generatedBindingsCanInitializeRustCore() {
		val coreDir = Files.createTempDirectory("vnidrop-jvm-test")
		val core = VnidropCore.initialize(
			appDataDir = coreDir.toString(),
			eventSink = object : CoreEventSink {
				override fun onEvent(event: CoreEvent) = Unit
			},
		)

		try {
			assertTrue(core.status().endpointId.isNotBlank())
		} finally {
			core.shutdown()
			coreDir.toFile().deleteRecursively()
		}
	}
}
