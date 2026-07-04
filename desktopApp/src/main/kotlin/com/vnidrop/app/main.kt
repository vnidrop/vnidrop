package com.vnidrop.app

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() {
	configureMacOsNativeAppearance()
	application {
		Window(
			onCloseRequest = ::exitApplication,
			title = "vnidrop",
		) {
			App()
		}
	}
}

private fun configureMacOsNativeAppearance() {
	if (!System.getProperty("os.name").startsWith("Mac", ignoreCase = true)) return
	// AWT reads this before creating the first native window. Runtime theme
	// changes are handled in the JVM platform appearance hook.
	System.setProperty("apple.awt.application.appearance", "system")
}
