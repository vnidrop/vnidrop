package com.vnidrop.app.platform

import androidx.compose.runtime.Composable
import java.awt.Frame

@Composable
actual fun PlatformSystemAppearance(isDarkTheme: Boolean) = Unit

object DesktopAppearanceBridge {
	fun isLinux(): Boolean = isLinux(System.getProperty("os.name"))

	internal fun isLinux(osName: String): Boolean =
		osName.startsWith("Linux", ignoreCase = true)

	internal fun toggledWindowState(currentState: Int): Int =
		if (currentState and Frame.MAXIMIZED_BOTH == Frame.MAXIMIZED_BOTH) {
			Frame.NORMAL
		} else {
			Frame.MAXIMIZED_BOTH
		}
}
