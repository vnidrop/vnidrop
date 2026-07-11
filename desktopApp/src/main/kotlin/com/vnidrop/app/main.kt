package com.vnidrop.app

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.vnidrop.app.platform.DesktopAppearanceBridge
import com.vnidrop.app.feature.send.DesktopShareBridge

fun main() {
	configureMacOsNativeAppearance()
	DesktopAppearanceBridge.applyNativeAppearance = MacOsAppKitAppearance::apply
	if (System.getProperty("os.name").startsWith("Mac", ignoreCase = true)) {
		DesktopShareBridge.shareFile = MacOsShareSheet::share
	}
	application {
		Window(
			onCloseRequest = ::exitApplication,
			title = "vnidrop",
		) {
			App(rememberJvmAppDependencies())
		}
	}
}

private fun configureMacOsNativeAppearance() {
	if (!System.getProperty("os.name").startsWith("Mac", ignoreCase = true)) return
	// AWT reads this before creating the first native window. Runtime theme
	// changes are handled in the JVM platform appearance hook.
	System.setProperty("apple.awt.application.appearance", "system")
}
