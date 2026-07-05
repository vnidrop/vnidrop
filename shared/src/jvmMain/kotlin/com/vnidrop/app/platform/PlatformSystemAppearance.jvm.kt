package com.vnidrop.app.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import java.awt.Color
import java.awt.EventQueue
import java.awt.Window
import javax.swing.JFrame

@Composable
actual fun PlatformSystemAppearance(isDarkTheme: Boolean) {
	SideEffect {
		DesktopSystemAppearance.apply(isDarkTheme)
	}
}

internal object DesktopSystemAppearance {
	private const val MAC_APPEARANCE_PROPERTY = "apple.awt.application.appearance"
	private const val TRANSPARENT_TITLE_BAR_PROPERTY = "apple.awt.transparentTitleBar"

	fun apply(isDarkTheme: Boolean) {
		if (!isMacOs()) return
		System.setProperty(MAC_APPEARANCE_PROPERTY, macOsAppearanceName(isDarkTheme))
		EventQueue.invokeLater {
			DesktopAppearanceBridge.applyNativeAppearance?.invoke(isDarkTheme)
			Window.getWindows().forEach { window ->
				applyWindowChrome(window, isDarkTheme)
			}
		}
	}

	internal fun macOsAppearanceName(isDarkTheme: Boolean): String =
		if (isDarkTheme) "NSAppearanceNameDarkAqua" else "NSAppearanceNameAqua"

	internal fun usesTransparentTitlebar(): Boolean = true

	internal fun titlebarBackground(isDarkTheme: Boolean): Color =
		if (isDarkTheme) Color(0x12, 0x12, 0x12) else Color(0xF8, 0xF8, 0xF8)

	private fun applyWindowChrome(window: Window, isDarkTheme: Boolean) {
		val background = titlebarBackground(isDarkTheme)
		window.background = background
		(window as? JFrame)?.rootPane?.let { rootPane ->
			// The titlebar stays native, but AppKit receives the resolved app
			// appearance so title text and controls switch contrast at runtime.
			rootPane.putClientProperty(TRANSPARENT_TITLE_BAR_PROPERTY, usesTransparentTitlebar())
			rootPane.background = background
			rootPane.contentPane.background = background
		}
	}

	private fun isMacOs(): Boolean =
		System.getProperty("os.name").startsWith("Mac", ignoreCase = true)
}

object DesktopAppearanceBridge {
	@Volatile
	var applyNativeAppearance: ((Boolean) -> Unit)? = null
}
