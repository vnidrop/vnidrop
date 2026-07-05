package com.vnidrop.app.platform

import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopSystemAppearanceTest {
	@Test
	fun macOsAppearanceNamesMatchResolvedTheme() {
		assertEquals("NSAppearanceNameDarkAqua", DesktopSystemAppearance.macOsAppearanceName(isDarkTheme = true))
		assertEquals("NSAppearanceNameAqua", DesktopSystemAppearance.macOsAppearanceName(isDarkTheme = false))
	}

	@Test
	fun titlebarBackgroundUsesLightAndDarkSurfaces() {
		assertEquals(0x121212, DesktopSystemAppearance.titlebarBackground(isDarkTheme = true).rgb and 0xFFFFFF)
		assertEquals(0xF8F8F8, DesktopSystemAppearance.titlebarBackground(isDarkTheme = false).rgb and 0xFFFFFF)
	}

	@Test
	fun transparentTitlebarIsAlwaysUsedWithAppKitAppearance() {
		assertEquals(true, DesktopSystemAppearance.usesTransparentTitlebar())
	}

	@Test
	fun runtimeAppearanceCallIsFailSoft() {
		DesktopSystemAppearance.apply(isDarkTheme = true)
		DesktopSystemAppearance.apply(isDarkTheme = false)
	}
}
