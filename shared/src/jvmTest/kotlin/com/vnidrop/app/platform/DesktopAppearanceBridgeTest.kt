package com.vnidrop.app.platform

import java.awt.Frame
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopAppearanceBridgeTest {
	@Test
	fun customWindowChromeIsLimitedToLinux() {
		assertFalse(DesktopAppearanceBridge.isLinux("Mac OS X"))
		assertTrue(DesktopAppearanceBridge.isLinux("Linux"))
		assertFalse(DesktopAppearanceBridge.isLinux("Windows 11"))
	}

	@Test
	fun nativeWindowsChromeIsLimitedToWindows() {
		assertFalse(DesktopAppearanceBridge.isWindows("Mac OS X"))
		assertFalse(DesktopAppearanceBridge.isWindows("Linux"))
		assertTrue(DesktopAppearanceBridge.isWindows("Windows 10"))
		assertTrue(DesktopAppearanceBridge.isWindows("Windows 11"))
	}

	@Test
	fun titlebarDoubleClickTogglesMaximizedWindowState() {
		assertEquals(Frame.MAXIMIZED_BOTH, DesktopAppearanceBridge.toggledWindowState(Frame.NORMAL))
		assertEquals(Frame.NORMAL, DesktopAppearanceBridge.toggledWindowState(Frame.MAXIMIZED_BOTH))
	}
}
