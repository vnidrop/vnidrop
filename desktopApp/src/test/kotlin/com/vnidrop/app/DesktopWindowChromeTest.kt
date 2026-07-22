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

	@Test
	fun closeControlUsesDestructiveStateWhenHoveredOrPressed() {
		assertEquals(
			LinuxWindowControlVisualState.DestructiveActive,
			linuxWindowControlVisualState(isClose = true, isHovered = true, isPressed = false),
		)
		assertEquals(
			LinuxWindowControlVisualState.DestructiveActive,
			linuxWindowControlVisualState(isClose = true, isHovered = false, isPressed = true),
		)
	}

	@Test
	fun standardControlsKeepNeutralInteractionState() {
		assertEquals(
			LinuxWindowControlVisualState.NeutralActive,
			linuxWindowControlVisualState(isClose = false, isHovered = true, isPressed = false),
		)
		assertEquals(
			LinuxWindowControlVisualState.Default,
			linuxWindowControlVisualState(isClose = false, isHovered = false, isPressed = false),
		)
	}
}
