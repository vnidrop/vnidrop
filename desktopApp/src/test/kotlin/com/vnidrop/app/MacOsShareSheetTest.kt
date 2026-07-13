package com.vnidrop.app

import com.vnidrop.app.platform.DesktopAppearanceBridge
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Assume.assumeTrue

class MacOsShareSheetTest {
	@Test
	fun nativeAnchorRectHasTheCocoaLayout() {
		assertEquals(32, MacOsShareSheet.validateNativeRectMapping())
	}

	@Test
	fun nativeMainDispatchQueueCanBeResolved() {
		assumeTrue(DesktopAppearanceBridge.isMacOs())
		assertTrue(MacOsShareSheet.hasNativeMainQueue())
	}
}
