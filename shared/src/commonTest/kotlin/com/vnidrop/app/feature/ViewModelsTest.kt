package com.vnidrop.app.feature

import com.vnidrop.app.DeviceInfo
import com.vnidrop.app.PlatformEnvironment
import com.vnidrop.app.core.CoreState
import com.vnidrop.app.core.ReceiveFolder
import com.vnidrop.app.core.ReceiveFolderKind
import com.vnidrop.app.core.Share
import com.vnidrop.app.core.ShareAccessPolicy
import com.vnidrop.app.core.Transfer
import com.vnidrop.app.core.TransferDirection
import com.vnidrop.app.core.TransferStatus
import com.vnidrop.app.feature.app.AppViewModel
import com.vnidrop.app.feature.receive.ReceiveHistoryDeleteTarget
import com.vnidrop.app.feature.receive.ReceiveViewModel
import com.vnidrop.app.feature.send.SendViewModel
import com.vnidrop.app.feature.settings.SettingsViewModel
import com.vnidrop.app.notifications.NotificationPermission
import com.vnidrop.app.preferences.AppPreferences
import com.vnidrop.app.support.FakeCoreGateway
import com.vnidrop.app.support.FakeFileSystemService
import com.vnidrop.app.support.FakeFilePreviewRepository
import com.vnidrop.app.support.FakeNotificationService
import com.vnidrop.app.support.FakePreferencesRepository
import com.vnidrop.app.ui.feedback.UiMessageController
import com.vnidrop.app.ui.navigation.AppDestination
import com.vnidrop.app.ui.theme.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ViewModelsTest {
	@AfterTest
	fun resetDispatcher() {
		Dispatchers.resetMain()
	}

	@Test
	fun appViewModelInitializesCoreAndOwnsNavigation() = runTest {
		Dispatchers.setMain(StandardTestDispatcher(testScheduler))
		val core = FakeCoreGateway()
		val viewModel = AppViewModel(environment(), core, preferences(), UiMessageController())
		advanceUntilIdle()
		assertTrue(core.state.value.isInitialized)
		viewModel.selectDestination(AppDestination.Settings)
		assertEquals(AppDestination.Settings, viewModel.state.value.destination)
	}

	@Test
	fun settingsEnablesNotificationsOnlyAfterPermission() = runTest {
		Dispatchers.setMain(StandardTestDispatcher(testScheduler))
		val preferences = preferences()
		val notifications = FakeNotificationService(NotificationPermission.Granted)
		val viewModel = SettingsViewModel(
			environment(),
			{ DeviceInfo("Device", "Model", "OS", "Wi-Fi", "80%") },
			FakeFileSystemService(folder),
			preferences,
			notifications,
			UiMessageController(),
		)
		advanceUntilIdle()
		viewModel.setNotificationsEnabled(true)
		advanceUntilIdle()
		assertTrue(preferences.mutablePreferences.value.notificationsEnabled)
		viewModel.setNotificationsEnabled(false)
		advanceUntilIdle()
		assertFalse(preferences.mutablePreferences.value.notificationsEnabled)
		assertEquals(1, notifications.cancelAllCount)
	}

	@Test
	fun settingsKeepsNotificationsDisabledWhenPermissionIsDenied() = runTest {
		Dispatchers.setMain(StandardTestDispatcher(testScheduler))
		val preferences = preferences()
		val viewModel = SettingsViewModel(
			environment(),
			{ DeviceInfo("Device", null, "OS", null, null) },
			FakeFileSystemService(folder),
			preferences,
			FakeNotificationService(NotificationPermission.Denied),
			UiMessageController(),
		)
		advanceUntilIdle()
		viewModel.setNotificationsEnabled(true)
		advanceUntilIdle()
		assertFalse(preferences.mutablePreferences.value.notificationsEnabled)
	}

	@Test
	fun settingsCompletesNotificationOptInAfterSystemSettingsGrant() = runTest {
		Dispatchers.setMain(StandardTestDispatcher(testScheduler))
		val preferences = preferences()
		val notifications = FakeNotificationService(NotificationPermission.Denied)
		val viewModel = SettingsViewModel(
			environment(),
			{ DeviceInfo("Device", null, "OS", null, null) },
			FakeFileSystemService(folder),
			preferences,
			notifications,
			UiMessageController(),
		)
		advanceUntilIdle()

		viewModel.openNotificationSettings()
		advanceUntilIdle()
		assertEquals(1, notifications.openSettingsCount)

		notifications.mutablePermission.value = NotificationPermission.Granted
		viewModel.refreshNotificationPermission()
		advanceUntilIdle()

		assertTrue(preferences.mutablePreferences.value.notificationsEnabled)
		assertEquals(NotificationPermission.Granted, viewModel.state.value.notificationPermission)
	}

	@Test
	fun settingsReportsUnsupportedNotificationPlatforms() = runTest {
		Dispatchers.setMain(StandardTestDispatcher(testScheduler))
		val preferences = preferences()
		val viewModel = SettingsViewModel(
			environment(),
			{ DeviceInfo("Device", null, "OS", null, null) },
			FakeFileSystemService(folder),
			preferences,
			FakeNotificationService(NotificationPermission.Unsupported),
			UiMessageController(),
		)
		advanceUntilIdle()
		viewModel.setNotificationsEnabled(true)
		advanceUntilIdle()
		assertFalse(preferences.mutablePreferences.value.notificationsEnabled)
		assertEquals(NotificationPermission.Unsupported, viewModel.state.value.notificationPermission)
	}

	@Test
	fun sendViewModelOwnsSelectedFileState() = runTest {
		Dispatchers.setMain(StandardTestDispatcher(testScheduler))
		val viewModel = SendViewModel(FakeCoreGateway(), FakeFileSystemService(folder), preferences(), FakeFilePreviewRepository(), UiMessageController())
		viewModel.openComposer()
		viewModel.onFilePicked(com.vnidrop.app.core.PickedShareFile("/tmp/photo.jpg", "photo.jpg", 42UL))
		assertEquals("photo.jpg", viewModel.state.value.transferName)
		assertEquals(42UL, viewModel.state.value.selectedFile?.sizeBytes)
		viewModel.clearSelectedSource()
		assertEquals(null, viewModel.state.value.selectedFile)
	}

	@Test
	fun sendComposerClosesAfterSuccessfulAtomicShareCreation() = runTest {
		Dispatchers.setMain(StandardTestDispatcher(testScheduler))
		val core = FakeCoreGateway().apply {
			mutableState.value = CoreState(isInitialized = true)
			shareResult = Result.success(Share(7UL, "ticket", "photo.jpg", "hash", 1UL, 42UL))
		}
		val previews = FakeFilePreviewRepository()
		val viewModel = SendViewModel(core, FakeFileSystemService(folder), preferences(), previews, UiMessageController())
		advanceUntilIdle()
		viewModel.openComposer()
		val thumbnail = ByteArray(12).also {
			it[0] = 0x89.toByte(); it[1] = 'P'.code.toByte(); it[2] = 'N'.code.toByte(); it[3] = 'G'.code.toByte()
		}
		viewModel.onFilePicked(com.vnidrop.app.core.PickedShareFile("/tmp/photo.jpg", "photo.jpg", 42UL, thumbnail))
		viewModel.setAccessPolicy(ShareAccessPolicy.AnyoneWithTransfer)
		viewModel.createShare()
		advanceUntilIdle()

		assertFalse(viewModel.state.value.isComposerOpen)
		assertEquals(null, viewModel.state.value.selectedFile)
		assertEquals(ShareAccessPolicy.AnyoneWithTransfer, core.lastShareAccessPolicy)
		assertEquals(7UL, core.state.value.transfers.first().transferId)
		assertContentEquals(thumbnail, previews.previews.value.getValue(7UL))
	}

	@Test
	fun sendComposerStaysOpenWhenShareCreationFails() = runTest {
		Dispatchers.setMain(StandardTestDispatcher(testScheduler))
		val core = FakeCoreGateway().apply { mutableState.value = CoreState(isInitialized = true) }
		val viewModel = SendViewModel(core, FakeFileSystemService(folder), preferences(), FakeFilePreviewRepository(), UiMessageController())
		advanceUntilIdle()
		viewModel.openComposer()
		viewModel.onFilePicked(com.vnidrop.app.core.PickedShareFile("/tmp/photo.jpg", "photo.jpg", 42UL))
		viewModel.createShare()
		advanceUntilIdle()

		assertTrue(viewModel.state.value.isComposerOpen)
		assertEquals("photo.jpg", viewModel.state.value.selectedFile?.displayName)
		assertFalse(viewModel.state.value.isSharing)
	}

	@Test
	fun sendDeletionRemovesCoreTransferAndOwnedPreview() = runTest {
		Dispatchers.setMain(StandardTestDispatcher(testScheduler))
		val core = FakeCoreGateway().apply {
			mutableState.value = CoreState(isInitialized = true, transfers = listOf(
				com.vnidrop.app.core.Transfer(
					localId = "send-7", transferId = 7UL,
					direction = com.vnidrop.app.core.TransferDirection.Send,
					status = com.vnidrop.app.core.TransferStatus.Sharing,
					peerId = null, transferName = "Photo", contentHash = "hash",
					fileCount = 1UL, totalSize = 42UL, ticket = "ticket",
					accessPolicy = ShareAccessPolicy.RequireApproval, createdAt = 1, updatedAt = 1,
				),
			))
		}
		val previews = FakeFilePreviewRepository()
		previews.save(7UL, byteArrayOf(1, 2, 3))
		val viewModel = SendViewModel(core, FakeFileSystemService(folder), preferences(), previews, UiMessageController())
		advanceUntilIdle()
		viewModel.openTransfer(7UL)
		viewModel.requestDeleteTransfer()
		viewModel.confirmDeleteTransfer()
		advanceUntilIdle()

		assertEquals(listOf(7UL), core.deletedTransfers)
		assertFalse(7UL in previews.previews.value)
		assertEquals(null, viewModel.state.value.selectedTransferId)
		assertFalse(viewModel.state.value.isDeleteConfirmationOpen)
	}

	@Test
	fun receiveViewModelBuildsStateFromPreferences() = runTest {
		Dispatchers.setMain(StandardTestDispatcher(testScheduler))
		val core = FakeCoreGateway().apply {
			mutableState.value = mutableState.value.copy(isInitialized = true)
			inspectionResult = Result.success(com.vnidrop.app.core.TicketInspectionModel(
				kind = "vnidrop",
				blobTicket = "blob",
				metadata = com.vnidrop.app.core.TransferMetadataModel(1UL, "Photo", null, "hash", 1UL, 42UL),
			))
		}
		val viewModel = ReceiveViewModel(core, FakeFileSystemService(folder), preferences(), UiMessageController())
		advanceUntilIdle()
		viewModel.onInvitationResult(com.vnidrop.app.feature.receive.ReceiveMethod.InvitationFile, Result.success("ticket"))
		advanceUntilIdle()
		assertTrue(viewModel.state.value.canReceive(coreInitialized = true))
		assertEquals("Receiver", viewModel.state.value.receiverName)
	}

	@Test
	fun receiveViewModelDeletesOneTerminalHistoryItem() = runTest {
		Dispatchers.setMain(StandardTestDispatcher(testScheduler))
		val core = FakeCoreGateway().apply {
			mutableState.value = CoreState(isInitialized = true, transfers = listOf(receivedTransfer(21UL, TransferStatus.Done)))
		}
		val viewModel = ReceiveViewModel(core, FakeFileSystemService(folder), preferences(), UiMessageController())
		advanceUntilIdle()

		viewModel.requestDeleteHistoryItem(21UL)
		viewModel.confirmHistoryDelete()
		advanceUntilIdle()

		assertEquals(listOf(21UL), core.deletedTransfers)
		assertEquals(null, viewModel.state.value.historyDeleteTarget)
	}

	@Test
	fun receiveViewModelClearHistoryUsesAtomicCoreOperationAndKeepsActiveReceive() = runTest {
		Dispatchers.setMain(StandardTestDispatcher(testScheduler))
		val core = FakeCoreGateway().apply {
			clearReceiveHistoryResult = Result.success(2UL)
			mutableState.value = CoreState(
				isInitialized = true,
				transfers = listOf(
					receivedTransfer(21UL, TransferStatus.Done),
					receivedTransfer(22UL, TransferStatus.Failed),
					receivedTransfer(23UL, TransferStatus.Receiving),
				),
			)
		}
		val viewModel = ReceiveViewModel(core, FakeFileSystemService(folder), preferences(), UiMessageController())
		advanceUntilIdle()

		viewModel.requestClearHistory()
		assertEquals(ReceiveHistoryDeleteTarget.All, viewModel.state.value.historyDeleteTarget)
		viewModel.confirmHistoryDelete()
		advanceUntilIdle()

		assertEquals(1, core.clearReceiveHistoryCount)
		assertEquals(listOf(23UL), core.state.value.transfers.map(Transfer::transferId))
	}

	@Test
	fun receiveViewModelKeepsDeleteConfirmationAfterFailure() = runTest {
		Dispatchers.setMain(StandardTestDispatcher(testScheduler))
		val core = FakeCoreGateway().apply {
			deleteResult = Result.failure(IllegalStateException("database busy"))
			mutableState.value = CoreState(isInitialized = true, transfers = listOf(receivedTransfer(21UL, TransferStatus.Done)))
		}
		val viewModel = ReceiveViewModel(core, FakeFileSystemService(folder), preferences(), UiMessageController())
		advanceUntilIdle()

		viewModel.requestDeleteHistoryItem(21UL)
		viewModel.confirmHistoryDelete()
		advanceUntilIdle()

		assertEquals(ReceiveHistoryDeleteTarget.Transfer(21UL), viewModel.state.value.historyDeleteTarget)
		assertFalse(viewModel.state.value.isDeletingHistory)
	}

	private fun preferences() = FakePreferencesRepository(
		AppPreferences("Receiver", folder, ThemeMode.System, notificationsEnabled = false),
	)

	private fun environment() = PlatformEnvironment("Test", "1.0", "/tmp/vnidrop")

	private fun receivedTransfer(id: ULong, status: TransferStatus) = Transfer(
		localId = "receive-$id",
		transferId = id,
		direction = TransferDirection.Receive,
		status = status,
		peerId = null,
		transferName = "Received $id",
		contentHash = "hash-$id",
		fileCount = 1UL,
		totalSize = 42UL,
		ticket = null,
		accessPolicy = ShareAccessPolicy.RequireApproval,
		createdAt = 1L,
		updatedAt = 1L,
	)

	private companion object {
		val folder = ReceiveFolder(ReceiveFolderKind.FileSystemPath, "/tmp", "Downloads")
	}
}
