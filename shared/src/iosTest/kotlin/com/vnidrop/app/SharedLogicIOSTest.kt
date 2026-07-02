package com.vnidrop.app

import platform.Foundation.NSTemporaryDirectory
import kotlin.test.Test
import kotlin.test.assertTrue
import uniffi.vnidrop_core.CoreEvent
import uniffi.vnidrop_core.CoreEventSink
import uniffi.vnidrop_core.VnidropCore

class SharedLogicIOSTest {

	@Test
	fun generatedBindingsCanInitializeRustCore() {
		val core = VnidropCore.initialize(
			appDataDir = NSTemporaryDirectory() + "vnidrop-ios-test",
			eventSink = object : CoreEventSink {
				override fun onEvent(event: CoreEvent) = Unit
			},
		)

		try {
			assertTrue(core.status().endpointId.isNotBlank())
		} finally {
			core.shutdown()
		}
	}
}
