package com.vnidrop.app.feature

import com.vnidrop.app.DeviceInfo
import com.vnidrop.app.PlatformEnvironment
import com.vnidrop.app.core.CoreState
import com.vnidrop.app.core.ReceiveFolder
import com.vnidrop.app.core.ReceiveFolderKind
import com.vnidrop.app.core.Share
import com.vnidrop.app.core.ShareAccessPolicy
import com.vnidrop.app.feature.app.AppViewModel
import com.vnidrop.app.feature.receive.ReceiveViewModel
import com.vnidrop.app.feature.send.SendViewModel
import com.vnidrop.app.feature.settings.SettingsViewModel
import com.vnidrop.app.notifications.NotificationPermission
import com.vnidrop.app.preferences.AppPreferences
import com.vnidrop.app.support.FakeCoreGateway
import com.vnidrop.app.support.FakeFileSystemService
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
		val viewModel = SendViewModel(FakeCoreGateway(), FakeFileSystemService(folder), preferences(), UiMessageController())
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
		val viewModel = SendViewModel(core, FakeFileSystemService(folder), preferences(), UiMessageController())
		advanceUntilIdle()
		viewModel.openComposer()
		viewModel.onFilePicked(com.vnidrop.app.core.PickedShareFile("/tmp/photo.jpg", "photo.jpg", 42UL))
		viewModel.setAccessPolicy(ShareAccessPolicy.AnyoneWithTransfer)
		viewModel.createShare()
		advanceUntilIdle()

		assertFalse(viewModel.state.value.isComposerOpen)
		assertEquals(null, viewModel.state.value.selectedFile)
		assertEquals(ShareAccessPolicy.AnyoneWithTransfer, core.lastShareAccessPolicy)
		assertEquals(7UL, core.state.value.transfers.first().transferId)
	}

	@Test
	fun sendComposerStaysOpenWhenShareCreationFails() = runTest {
		Dispatchers.setMain(StandardTestDispatcher(testScheduler))
		val core = FakeCoreGateway().apply { mutableState.value = CoreState(isInitialized = true) }
		val viewModel = SendViewModel(core, FakeFileSystemService(folder), preferences(), UiMessageController())
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
	fun receiveViewModelBuildsStateFromPreferences() = runTest {
		Dispatchers.setMain(StandardTestDispatcher(testScheduler))
		val core = FakeCoreGateway().apply { mutableState.value = mutableState.value.copy(isInitialized = true) }
		val viewModel = ReceiveViewModel(core, FakeFileSystemService(folder), preferences(), UiMessageController())
		advanceUntilIdle()
		viewModel.setTicket("ticket")
		assertTrue(viewModel.state.value.canReceive(coreInitialized = true))
		assertEquals("Receiver", viewModel.state.value.receiverName)
	}

	private fun preferences() = FakePreferencesRepository(
		AppPreferences("Receiver", folder, ThemeMode.System, notificationsEnabled = false),
	)

	private fun environment() = PlatformEnvironment("Test", "1.0", "/tmp/vnidrop")

	private companion object {
		val folder = ReceiveFolder(ReceiveFolderKind.FileSystemPath, "/tmp", "Downloads")
	}
}
