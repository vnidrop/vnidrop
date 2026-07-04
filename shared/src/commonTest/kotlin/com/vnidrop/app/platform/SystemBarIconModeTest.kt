package com.vnidrop.app.platform

import kotlin.test.Test
import kotlin.test.assertEquals

class SystemBarIconModeTest {
	@Test
	fun darkThemeUsesLightSystemBarIcons() {
		assertEquals(SystemBarIconMode.LightIcons, systemBarIconModeForTheme(isDarkTheme = true))
		assertEquals(SystemBarIconMode.DarkIcons, systemBarIconModeForTheme(isDarkTheme = false))
	}
}
