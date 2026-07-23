package com.vnidrop.app.ui.platform

import com.vnidrop.app.UiPlatform
import com.vnidrop.app.ui.state.WindowClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlatformUiTest {
	@Test
	fun onlyCompactAndroidUsesMobilePresentation() {
		assertTrue(usesMobilePresentation(UiPlatform.Android, WindowClass.Phone))
		assertFalse(usesMobilePresentation(UiPlatform.Android, WindowClass.Tablet))
		assertFalse(usesMobilePresentation(UiPlatform.Windows, WindowClass.Phone))
		assertFalse(usesMobilePresentation(UiPlatform.Linux, WindowClass.Phone))
	}

	@Test
	fun desktopWindowClassAccountsForPersistentNavigation() {
		assertEquals(WindowClass.Tablet, contentWindowClassFor(UiPlatform.Android, 800f))
		assertEquals(WindowClass.Phone, contentWindowClassFor(UiPlatform.Windows, 800f))
		assertEquals(WindowClass.Desktop, contentWindowClassFor(UiPlatform.Windows, 1_200f))
	}
}
