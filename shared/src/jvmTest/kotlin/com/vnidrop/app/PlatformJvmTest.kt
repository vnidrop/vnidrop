package com.vnidrop.app

import kotlin.test.Test
import kotlin.test.assertEquals

class PlatformJvmTest {
	@Test
	fun detectsSupportedDesktopPlatforms() {
		assertEquals(UiPlatform.Windows, uiPlatformForJvm("Windows 11"))
		assertEquals(UiPlatform.Linux, uiPlatformForJvm("Linux"))
		assertEquals(UiPlatform.Desktop, uiPlatformForJvm("Mac OS X"))
	}
}
