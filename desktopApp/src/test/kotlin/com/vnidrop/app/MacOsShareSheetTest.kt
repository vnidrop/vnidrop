package com.vnidrop.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MacOsShareSheetTest {
	@Test
	fun nativeAnchorRectHasTheCocoaLayout() {
		assertEquals(32, MacOsShareSheet.validateNativeRectMapping())
	}

	@Test
	fun nativeMainDispatchQueueCanBeResolved() {
		assertTrue(MacOsShareSheet.hasNativeMainQueue())
	}
}
