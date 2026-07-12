package com.vnidrop.app.preferences

import com.vnidrop.app.core.ReceiveFolder
import com.vnidrop.app.core.ReceiveFolderKind
import com.vnidrop.app.ui.theme.ThemeMode
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class AppPreferencesRepositoryTest {
	@Test
	fun preferencesUseDefaultsWhenNothingIsStored() = runBlocking {
		val repository = repositoryForTest()

		val preferences = repository.preferences.first()

		assertEquals("Device Name", preferences.username)
		assertEquals(defaultFolder, preferences.receiveFolder)
		assertEquals(ThemeMode.System, preferences.themeMode)
	}

	@Test
	fun themeModeIsPersisted() = runBlocking {
		val repository = repositoryForTest()

		repository.setThemeMode(ThemeMode.Dark)

		assertEquals(ThemeMode.Dark, repository.preferences.first().themeMode)
	}

	@Test
	fun notificationOptInIsDisabledByDefaultAndPersisted() = runBlocking {
		val repository = repositoryForTest()

		assertEquals(false, repository.preferences.first().notificationsEnabled)
		repository.setNotificationsEnabled(true)
		assertEquals(true, repository.preferences.first().notificationsEnabled)
	}

	@Test
	fun legacyAndroidAppDownloadsPathIsPromotedToDefault() = runBlocking {
		val publicDefault = ReceiveFolder(
			kind = ReceiveFolderKind.AndroidPublicDownloads,
			value = "media-store:downloads",
			displayName = "Downloads",
		)
		val repository = repositoryForTest(default = publicDefault)
		repository.setReceiveFolder(
			ReceiveFolder(
				kind = ReceiveFolderKind.FileSystemPath,
				value = "/storage/emulated/0/Android/data/com.vnidrop.app/files/Downloads",
				displayName = "App downloads",
			),
		)

		assertEquals(publicDefault, repository.preferences.first().receiveFolder)
	}

	@Test
	fun customFileSystemFolderIsNotPromotedAway() = runBlocking {
		val repository = repositoryForTest()
		val custom = ReceiveFolder(
			kind = ReceiveFolderKind.FileSystemPath,
			value = "/tmp/custom-receive",
			displayName = "Custom",
		)
		repository.setReceiveFolder(custom)
		assertEquals(custom, repository.preferences.first().receiveFolder)
	}

	private fun repositoryForTest(
		default: ReceiveFolder = defaultFolder,
	): AppPreferencesRepository {
		val directory = Files.createTempDirectory("vnidrop-preferences-test").toString()
		return AppPreferencesRepository(
			dataStore = createAppPreferencesDataStore(directory),
			defaults = AppPreferencesDefaults(
				username = "Device Name",
				receiveFolder = default,
				themeMode = ThemeMode.System,
			),
		)
	}

	private companion object {
		val defaultFolder = ReceiveFolder(
			kind = ReceiveFolderKind.FileSystemPath,
			value = "/tmp/Downloads",
			displayName = "Downloads",
		)
	}
}
