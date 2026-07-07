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

	private fun repositoryForTest(): AppPreferencesRepository {
		val directory = Files.createTempDirectory("vnidrop-preferences-test").toString()
		return AppPreferencesRepository(
			dataStore = createAppPreferencesDataStore(directory),
			defaults = AppPreferencesDefaults(
				username = "Device Name",
				receiveFolder = defaultFolder,
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
