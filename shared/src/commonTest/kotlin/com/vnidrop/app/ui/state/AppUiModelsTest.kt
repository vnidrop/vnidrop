package com.vnidrop.app.ui.state

import com.vnidrop.app.core.FolderAccessStatus
import com.vnidrop.app.core.ReceiveFolder
import com.vnidrop.app.core.ReceiveFolderKind
import com.vnidrop.app.ui.theme.ThemeMode
import com.vnidrop.app.ui.theme.resolveDarkTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import uniffi.vnidrop.StoredTransfer

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

	@Test
	fun sendStateExposesShareEligibility() {
		val ready = SendUiState(selectedSource = "/tmp/payload.txt")

		assertTrue(ready.canCreateShare(isCoreInitialized = true))
		assertFalse(ready.canCreateShare(isCoreInitialized = false))
		assertFalse(SendUiState().canCreateShare(isCoreInitialized = true))
		assertFalse(ready.copy(isSharing = true).canCreateShare(isCoreInitialized = true))
	}

	@Test
	fun receiveStateExposesInspectAndReceiveEligibility() {
		val ready = ReceiveUiState(ticket = "ticket", outputDirectory = "/tmp/out")

		assertTrue(ready.canInspect(isCoreInitialized = true))
		assertTrue(ready.canReceive(isCoreInitialized = true))
		assertFalse(ready.canInspect(isCoreInitialized = false))
		assertFalse(ready.copy(ticket = "").canReceive(isCoreInitialized = true))
		assertFalse(ready.copy(outputDirectory = "").canReceive(isCoreInitialized = true))
		assertFalse(ready.copy(isReceiving = true).canReceive(isCoreInitialized = true))
	}

	@Test
	fun preferencesStateExposesReceiveFolderEligibility() {
		val folder = ReceiveFolder(
			kind = ReceiveFolderKind.FileSystemPath,
			value = "/tmp/downloads",
			displayName = "Downloads",
		)

		assertTrue(PreferencesUiState(receiveFolder = folder, folderAccessStatus = FolderAccessStatus.Writable).canReceiveIntoFolder)
		assertFalse(PreferencesUiState(receiveFolder = folder, folderAccessStatus = FolderAccessStatus.PermissionRequired).canReceiveIntoFolder)
		assertFalse(PreferencesUiState(receiveFolder = folder, folderAccessStatus = FolderAccessStatus.Unavailable).canReceiveIntoFolder)
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

	private fun storedTransfer(status: String): StoredTransfer =
		StoredTransfer(
			localId = "local-1",
			transferId = 1UL,
			peerId = null,
			direction = "send",
			status = status,
			transferName = "Demo",
			contentHash = null,
			ticket = null,
			fileCount = 1UL,
			totalSize = 128UL,
			createdAt = 1L,
			updatedAt = 1L,
		)
}
