package com.vnidrop.app.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import java.awt.Color
import java.awt.EventQueue
import java.awt.Frame
import java.awt.Window
import javax.swing.JFrame
import javax.swing.JRootPane

@Composable
actual fun PlatformSystemAppearance(isDarkTheme: Boolean) {
	SideEffect {
		DesktopSystemAppearance.apply(isDarkTheme)
	}
}

internal object DesktopSystemAppearance {
	private const val MAC_APPEARANCE_PROPERTY = "apple.awt.application.appearance"
	private const val FULL_WINDOW_CONTENT_PROPERTY = "apple.awt.fullWindowContent"
	private const val TRANSPARENT_TITLE_BAR_PROPERTY = "apple.awt.transparentTitleBar"
	private const val WINDOW_TITLE_VISIBLE_PROPERTY = "apple.awt.windowTitleVisible"

	fun apply(isDarkTheme: Boolean) {
		if (!DesktopAppearanceBridge.isMacOs()) return
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
	internal fun usesFullWindowContent(): Boolean = true
	internal fun showsNativeWindowTitle(): Boolean = false

	internal fun titlebarBackground(isDarkTheme: Boolean): Color =
		if (isDarkTheme) Color(0x21, 0x21, 0x21) else Color(0xF3, 0xF3, 0xF3)

	private fun applyWindowChrome(window: Window, isDarkTheme: Boolean) {
		val background = titlebarBackground(isDarkTheme)
		window.background = background
		(window as? JFrame)?.rootPane?.let { rootPane -> applyRootPaneChrome(rootPane, background) }
	}

	internal fun applyRootPaneChrome(rootPane: JRootPane, background: Color) {
		// Extending the Compose surface beneath the native titlebar lets the
		// window chrome and sidebar share one uninterrupted background.
		rootPane.putClientProperty(FULL_WINDOW_CONTENT_PROPERTY, usesFullWindowContent())
		rootPane.putClientProperty(TRANSPARENT_TITLE_BAR_PROPERTY, usesTransparentTitlebar())
		rootPane.putClientProperty(WINDOW_TITLE_VISIBLE_PROPERTY, showsNativeWindowTitle())
		rootPane.background = background
		rootPane.contentPane.background = background
	}
}

object DesktopAppearanceBridge {
	@Volatile
	var applyNativeAppearance: ((Boolean) -> Unit)? = null

	fun isMacOs(): Boolean = isMacOs(System.getProperty("os.name"))

	fun toggleMaximized(window: Window) {
		if (!isMacOs()) return
		val frame = window as? Frame ?: return
		EventQueue.invokeLater {
			frame.extendedState = toggledWindowState(frame.extendedState)
		}
	}

	internal fun isMacOs(osName: String): Boolean =
		osName.startsWith("Mac", ignoreCase = true)

	internal fun toggledWindowState(currentState: Int): Int =
		if (currentState and Frame.MAXIMIZED_BOTH == Frame.MAXIMIZED_BOTH) {
			Frame.NORMAL
		} else {
			Frame.MAXIMIZED_BOTH
		}
}
