package com.vnidrop.app.platform

import java.awt.Color
import java.awt.Frame
import javax.swing.JRootPane
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopSystemAppearanceTest {
	@Test
	fun customWindowChromeIsMacOsOnly() {
		assertTrue(DesktopAppearanceBridge.isMacOs("Mac OS X"))
		assertFalse(DesktopAppearanceBridge.isMacOs("Windows 11"))
		assertFalse(DesktopAppearanceBridge.isMacOs("Linux"))
	}

	@Test
	fun titlebarDoubleClickTogglesMaximizedWindowState() {
		assertEquals(Frame.MAXIMIZED_BOTH, DesktopAppearanceBridge.toggledWindowState(Frame.NORMAL))
		assertEquals(Frame.NORMAL, DesktopAppearanceBridge.toggledWindowState(Frame.MAXIMIZED_BOTH))
	}

	@Test
	fun macOsAppearanceNamesMatchResolvedTheme() {
		assertEquals("NSAppearanceNameDarkAqua", DesktopSystemAppearance.macOsAppearanceName(isDarkTheme = true))
		assertEquals("NSAppearanceNameAqua", DesktopSystemAppearance.macOsAppearanceName(isDarkTheme = false))
	}

	@Test
	fun titlebarBackgroundMatchesSidebarSurface() {
		assertEquals(0x212121, DesktopSystemAppearance.titlebarBackground(isDarkTheme = true).rgb and 0xFFFFFF)
		assertEquals(0xF3F3F3, DesktopSystemAppearance.titlebarBackground(isDarkTheme = false).rgb and 0xFFFFFF)
	}

	@Test
	fun transparentTitlebarIsAlwaysUsedWithAppKitAppearance() {
		assertEquals(true, DesktopSystemAppearance.usesTransparentTitlebar())
	}

	@Test
	fun composeContentExtendsUnderMacOsTitlebar() {
		val rootPane = JRootPane()
		val background = Color(0x21, 0x21, 0x21)

		DesktopSystemAppearance.applyRootPaneChrome(rootPane, background)

		assertEquals(true, rootPane.getClientProperty("apple.awt.fullWindowContent"))
		assertEquals(true, rootPane.getClientProperty("apple.awt.transparentTitleBar"))
		assertEquals(false, rootPane.getClientProperty("apple.awt.windowTitleVisible"))
		assertEquals(background, rootPane.background)
		assertEquals(background, rootPane.contentPane.background)
	}

	@Test
	fun runtimeAppearanceCallIsFailSoft() {
		DesktopSystemAppearance.apply(isDarkTheme = true)
		DesktopSystemAppearance.apply(isDarkTheme = false)
	}
}
