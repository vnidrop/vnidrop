package com.vnidrop.app.ui.state

import com.vnidrop.app.ui.theme.ThemeMode
import com.vnidrop.app.ui.theme.resolveDarkTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppUiModelsTest {
	@Test
	fun windowClassUsesPhoneTabletDesktopBreakpoints() {
		assertEquals(WindowClass.Phone, windowClassFor(390f))
		assertEquals(WindowClass.Tablet, windowClassFor(700f))
		assertEquals(WindowClass.Desktop, windowClassFor(1200f))
	}

	@Test
	fun bottomNavigationIsReservedForPhoneWidth() {
		assertTrue(useBottomNavigation(WindowClass.Phone))
		assertFalse(useBottomNavigation(WindowClass.Tablet))
		assertFalse(useBottomNavigation(WindowClass.Desktop))
	}

	@Test
	fun themeModeResolvesAgainstSystemOnlyWhenRequested() {
		assertTrue(resolveDarkTheme(ThemeMode.System, systemDark = true))
		assertFalse(resolveDarkTheme(ThemeMode.System, systemDark = false))
		assertFalse(resolveDarkTheme(ThemeMode.Light, systemDark = true))
		assertTrue(resolveDarkTheme(ThemeMode.Dark, systemDark = false))
	}

	@Test
	fun coreErrorsBecomeStableUserMessages() {
		assertEquals(
			"The ticket could not be read. Check that the full ticket was copied.",
			friendlyCoreError("reason=failed to parse transfer ticket"),
		)
		assertEquals(
			"The transfer is waiting for approval or was refused by the sender.",
			friendlyCoreError("permission denied by sender"),
		)
	}

	@Test
	fun byteFormattingKeepsTransferCardsReadable() {
		assertEquals("58 B", formatBytes(58UL))
		assertEquals("1.5 KB", formatBytes(1536UL))
	}
}
