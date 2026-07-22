package com.vnidrop.app.ui.platform

import androidx.compose.runtime.staticCompositionLocalOf
import com.vnidrop.app.UiPlatform
import com.vnidrop.app.isDesktop
import com.vnidrop.app.ui.state.WindowClass
import com.vnidrop.app.ui.state.windowClassFor

const val DesktopNavigationWidthDp = 220f

val LocalUiPlatform = staticCompositionLocalOf { UiPlatform.Android }

fun usesMobilePresentation(uiPlatform: UiPlatform, windowClass: WindowClass): Boolean =
	uiPlatform == UiPlatform.Android && windowClass == WindowClass.Phone

fun contentWindowClassFor(uiPlatform: UiPlatform, widthDp: Float): WindowClass {
	val navigationWidth = if (uiPlatform.isDesktop) DesktopNavigationWidthDp else 0f
	return windowClassFor((widthDp - navigationWidth).coerceAtLeast(0f))
}
