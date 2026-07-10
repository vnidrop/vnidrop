package com.vnidrop.app.ui.state

import com.vnidrop.app.feature.receive.ReceiveState
import com.vnidrop.app.feature.send.SendState
import com.vnidrop.app.core.Transfer
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
	fun byteFormattingKeepsTransferCardsReadable() {
		assertEquals("58 B", formatBytes(58UL))
		assertEquals("1.5 KB", formatBytes(1536UL))
	}

	@Test
	fun sendStateExposesShareEligibility() {
		val ready = SendState(selectedSource = "/tmp/payload.txt")

		assertTrue(ready.canCreateShare(coreInitialized = true))
		assertFalse(ready.canCreateShare(coreInitialized = false))
		assertFalse(SendState().canCreateShare(coreInitialized = true))
		assertFalse(ready.copy(isSharing = true).canCreateShare(coreInitialized = true))
	}

	@Test
	fun receiveStateExposesInspectAndReceiveEligibility() {
		val ready = ReceiveState(
			ticket = "ticket",
			outputDirectory = "/tmp/out",
			folderAccessStatus = com.vnidrop.app.core.FolderAccessStatus.Writable,
		)

		assertTrue(ready.canInspect(coreInitialized = true))
		assertTrue(ready.canReceive(coreInitialized = true))
		assertFalse(ready.canInspect(coreInitialized = false))
		assertFalse(ready.copy(ticket = "").canReceive(coreInitialized = true))
		assertFalse(ready.copy(outputDirectory = "").canReceive(coreInitialized = true))
		assertFalse(ready.copy(isReceiving = true).canReceive(coreInitialized = true))
	}

	@Test
	fun transferActivityOnlyIncludesRunningStatuses() {
		assertTrue(storedTransfer(status = "importing").isActiveTransfer())
		assertTrue(storedTransfer(status = "sharing").isActiveTransfer())
		assertTrue(storedTransfer(status = "receiving").isActiveTransfer())
		assertFalse(storedTransfer(status = "done").isActiveTransfer())
		assertFalse(storedTransfer(status = "failed").isActiveTransfer())
		assertFalse(storedTransfer(status = "cancelled").isActiveTransfer())
	}

	private fun storedTransfer(status: String): Transfer =
		Transfer(
			localId = "local-1",
			transferId = 1UL,
			peerId = null,
			direction = "send",
			status = status,
			transferName = "Demo",
			ticket = null,
			fileCount = 1UL,
			totalSize = 128UL,
		)
}
