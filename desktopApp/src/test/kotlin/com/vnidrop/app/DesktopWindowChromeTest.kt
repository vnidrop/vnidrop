package com.vnidrop.app

import androidx.compose.ui.window.WindowPlacement
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopWindowChromeTest {
	@Test
	fun maximizeControlTogglesBetweenFloatingAndMaximized() {
		assertEquals(WindowPlacement.Maximized, toggledWindowPlacement(WindowPlacement.Floating))
		assertEquals(WindowPlacement.Floating, toggledWindowPlacement(WindowPlacement.Maximized))
	}
}
