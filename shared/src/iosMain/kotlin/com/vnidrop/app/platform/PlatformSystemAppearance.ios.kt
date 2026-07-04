package com.vnidrop.app.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import platform.Foundation.NSNotificationCenter

@Composable
actual fun PlatformSystemAppearance(isDarkTheme: Boolean) {
	SideEffect {
		// The Swift host owns the actual UIKit status bar style. Compose publishes
		// the resolved theme here so the wrapper can update without coupling common
		// UI code to iOS-specific view controller APIs.
		NSNotificationCenter.defaultCenter.postNotificationName(
			aName = "VniDropThemeChanged",
			`object` = null,
			userInfo = mapOf("isDark" to if (isDarkTheme) "true" else "false"),
		)
	}
}
