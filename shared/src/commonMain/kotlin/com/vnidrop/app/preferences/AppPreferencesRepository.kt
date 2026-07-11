package com.vnidrop.app.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import com.vnidrop.app.core.ReceiveFolder
import com.vnidrop.app.core.ReceiveFolderKind
import com.vnidrop.app.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import okio.Path.Companion.toPath

data class AppPreferences(
	val username: String,
	val receiveFolder: ReceiveFolder,
	val themeMode: ThemeMode,
	val notificationsEnabled: Boolean,
)

class AppPreferencesDefaults(
	val username: String,
	val receiveFolder: ReceiveFolder,
	val themeMode: ThemeMode,
	val notificationsEnabled: Boolean = false,
)

interface PreferencesRepository {
	val preferences: Flow<AppPreferences>
	suspend fun setUsername(username: String)
	suspend fun setReceiveFolder(folder: ReceiveFolder)
	suspend fun resetReceiveFolder()
	suspend fun setThemeMode(mode: ThemeMode)
	suspend fun setNotificationsEnabled(enabled: Boolean)
}

class AppPreferencesRepository(
	private val dataStore: DataStore<Preferences>,
	private val defaults: AppPreferencesDefaults,
) : PreferencesRepository {
	override val preferences: Flow<AppPreferences> = dataStore.data
		.catch { emit(emptyPreferences()) }
		.map { prefs ->
			AppPreferences(
				username = prefs[PreferenceKeys.Username]?.takeIf { it.isNotBlank() } ?: defaults.username,
				receiveFolder = ReceiveFolder(
					kind = prefs[PreferenceKeys.ReceiveFolderKind]?.let { receiveFolderKindOrNull(it) }
						?: defaults.receiveFolder.kind,
					value = prefs[PreferenceKeys.ReceiveFolderValue]?.takeIf { it.isNotBlank() }
						?: defaults.receiveFolder.value,
					displayName = prefs[PreferenceKeys.ReceiveFolderDisplayName]?.takeIf { it.isNotBlank() }
						?: defaults.receiveFolder.displayName,
				),
				themeMode = prefs[PreferenceKeys.ThemeMode]?.let { themeModeOrNull(it) } ?: defaults.themeMode,
				notificationsEnabled = prefs[PreferenceKeys.NotificationsEnabled] ?: defaults.notificationsEnabled,
			)
		}

	override suspend fun setUsername(username: String) {
		dataStore.edit { prefs ->
			prefs[PreferenceKeys.Username] = username.trim()
		}
	}

	override suspend fun setReceiveFolder(folder: ReceiveFolder) {
		dataStore.edit { prefs ->
			prefs[PreferenceKeys.ReceiveFolderKind] = folder.kind.name
			prefs[PreferenceKeys.ReceiveFolderValue] = folder.value
			prefs[PreferenceKeys.ReceiveFolderDisplayName] = folder.displayName
		}
	}

	override suspend fun resetReceiveFolder() {
		setReceiveFolder(defaults.receiveFolder)
	}

	override suspend fun setThemeMode(mode: ThemeMode) {
		dataStore.edit { prefs ->
			prefs[PreferenceKeys.ThemeMode] = mode.name
		}
	}

	override suspend fun setNotificationsEnabled(enabled: Boolean) {
		dataStore.edit { prefs ->
			prefs[PreferenceKeys.NotificationsEnabled] = enabled
		}
	}
}

fun createAppPreferencesDataStore(appDataDir: String): DataStore<Preferences> =
	PreferenceDataStoreFactory.createWithPath(
		produceFile = { "$appDataDir/$AppPreferencesFileName".toPath() },
	)

private object PreferenceKeys {
	val Username = stringPreferencesKey("username")
	val ReceiveFolderKind = stringPreferencesKey("receive_folder_kind")
	val ReceiveFolderValue = stringPreferencesKey("receive_folder_value")
	val ReceiveFolderDisplayName = stringPreferencesKey("receive_folder_display_name")
	val ThemeMode = stringPreferencesKey("theme_mode")
	val NotificationsEnabled = booleanPreferencesKey("notifications_enabled")
}

private fun receiveFolderKindOrNull(raw: String): ReceiveFolderKind? =
	runCatching { ReceiveFolderKind.valueOf(raw) }.getOrNull()

private fun themeModeOrNull(raw: String): ThemeMode? =
	runCatching { ThemeMode.valueOf(raw) }.getOrNull()

private const val AppPreferencesFileName = "app_preferences.preferences_pb"
