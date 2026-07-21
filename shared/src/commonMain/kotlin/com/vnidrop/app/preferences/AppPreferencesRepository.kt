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
import com.vnidrop.app.util.randomUuidString
import uniffi.vnidrop.RelayMode as CoreRelayMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import okio.Path.Companion.toPath

/**
 * Relay transport choice. Mirrors the core's `RelayMode` without the URLs, which
 * are stored alongside so a user can switch modes without retyping them.
 *
 * [Disabled] is local-network only: without relays the endpoint never discovers a
 * public address, so peers can only be reached on the same network.
 */
enum class RelayModeSetting {
	Standard,
	Custom,
	Disabled,
}

data class RelaySettings(
	val mode: RelayModeSetting = RelayModeSetting.Standard,
	/** Retained across mode changes; only meaningful when [mode] is [RelayModeSetting.Custom]. */
	val customUrls: List<String> = emptyList(),
)

/**
 * Translates the persisted setting into the core's relay configuration.
 *
 * Custom with no usable URL would leave the endpoint with an empty relay map —
 * silently local-network only — so it falls back to the default relays.
 */
fun RelaySettings.toCoreRelayMode(): CoreRelayMode = when (mode) {
	RelayModeSetting.Standard -> CoreRelayMode.Default
	RelayModeSetting.Disabled -> CoreRelayMode.Disabled
	RelayModeSetting.Custom -> {
		val urls = customUrls.map { it.trim() }.filter { it.isNotEmpty() }
		if (urls.isEmpty()) CoreRelayMode.Default else CoreRelayMode.Custom(urls)
	}
}

data class AppPreferences(
	val username: String,
	val receiveFolder: ReceiveFolder,
	val themeMode: ThemeMode,
	val notificationsEnabled: Boolean,
	/** Master opt-in for automatic telemetry + crash upload. Bug reports remain available always. */
	val diagnosticsEnabled: Boolean = false,
	/** Stable anonymous install id; never an account or advertising id. */
	val diagnosticsInstallId: String = "",
	val relay: RelaySettings = RelaySettings(),
)

class AppPreferencesDefaults(
	val username: String,
	val receiveFolder: ReceiveFolder,
	val themeMode: ThemeMode,
	val notificationsEnabled: Boolean = false,
	val diagnosticsEnabled: Boolean = false,
)

interface PreferencesRepository {
	val preferences: Flow<AppPreferences>
	suspend fun setUsername(username: String)
	suspend fun setReceiveFolder(folder: ReceiveFolder)
	suspend fun resetReceiveFolder()
	suspend fun setThemeMode(mode: ThemeMode)
	suspend fun setNotificationsEnabled(enabled: Boolean)
	suspend fun setDiagnosticsEnabled(enabled: Boolean)

	/**
	 * Relay changes only take effect when the core is next initialized, because
	 * the endpoint is built once at startup.
	 */
	suspend fun setRelay(relay: RelaySettings)
	/** Ensures a durable install id exists and returns it. */
	suspend fun ensureDiagnosticsInstallId(): String
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
				receiveFolder = resolveReceiveFolder(prefs, defaults.receiveFolder),
				themeMode = prefs[PreferenceKeys.ThemeMode]?.let { themeModeOrNull(it) } ?: defaults.themeMode,
				notificationsEnabled = prefs[PreferenceKeys.NotificationsEnabled] ?: defaults.notificationsEnabled,
				diagnosticsEnabled = prefs[PreferenceKeys.DiagnosticsEnabled] ?: defaults.diagnosticsEnabled,
				diagnosticsInstallId = prefs[PreferenceKeys.DiagnosticsInstallId].orEmpty(),
					relay = resolveRelay(prefs),
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

	override suspend fun setRelay(relay: RelaySettings) {
		dataStore.edit { prefs ->
			prefs[PreferenceKeys.RelayMode] = relay.mode.name
			// Stored as newline-joined text rather than a preference set so the
			// order the user entered survives a round trip.
			prefs[PreferenceKeys.RelayCustomUrls] = relay.customUrls.joinToString("\n")
		}
	}

	override suspend fun setDiagnosticsEnabled(enabled: Boolean) {
		dataStore.edit { prefs ->
			prefs[PreferenceKeys.DiagnosticsEnabled] = enabled
		}
	}

	override suspend fun ensureDiagnosticsInstallId(): String {
		val existing = preferences.first().diagnosticsInstallId
		if (existing.isNotBlank()) return existing
		val created = randomUuidString()
		dataStore.edit { prefs ->
			if (prefs[PreferenceKeys.DiagnosticsInstallId].isNullOrBlank()) {
				prefs[PreferenceKeys.DiagnosticsInstallId] = created
			}
		}
		return preferences.first().diagnosticsInstallId.ifBlank { created }
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
	val DiagnosticsEnabled = booleanPreferencesKey("diagnostics_enabled")
	val DiagnosticsInstallId = stringPreferencesKey("diagnostics_install_id")
	val RelayMode = stringPreferencesKey("relay_mode")
	val RelayCustomUrls = stringPreferencesKey("relay_custom_urls")
}

private fun resolveRelay(prefs: Preferences): RelaySettings {
	// An unknown persisted mode falls back to the shipped default rather than
	// failing initialization; the URLs stay so the user can switch back.
	val mode = prefs[PreferenceKeys.RelayMode]?.let { relayModeOrNull(it) } ?: RelayModeSetting.Standard
	val urls = prefs[PreferenceKeys.RelayCustomUrls]
		?.split('\n')
		?.map { it.trim() }
		?.filter { it.isNotEmpty() }
		.orEmpty()
	return RelaySettings(mode = mode, customUrls = urls)
}

private fun relayModeOrNull(raw: String): RelayModeSetting? =
	runCatching { RelayModeSetting.valueOf(raw) }.getOrNull()

private fun resolveReceiveFolder(prefs: Preferences, defaults: ReceiveFolder): ReceiveFolder {
	val kind = prefs[PreferenceKeys.ReceiveFolderKind]?.let { receiveFolderKindOrNull(it) }
		?: defaults.kind
	val value = prefs[PreferenceKeys.ReceiveFolderValue]?.takeIf { it.isNotBlank() }
		?: defaults.value
	val displayName = prefs[PreferenceKeys.ReceiveFolderDisplayName]?.takeIf { it.isNotBlank() }
		?: defaults.displayName
	// Older Android builds defaulted to app-private "Downloads" paths that are
	// invisible in the system Downloads UI. Promote those back to the shared
	// public Downloads default so receive matches desktop expectations.
	if (kind == ReceiveFolderKind.FileSystemPath && isLegacyAndroidAppDownloadsPath(value)) {
		return defaults
	}
	return ReceiveFolder(kind = kind, value = value, displayName = displayName)
}

private fun isLegacyAndroidAppDownloadsPath(path: String): Boolean {
	// Typical: /storage/emulated/0/Android/data/<pkg>/files/Download[s]
	val normalized = path.replace('\\', '/')
	return normalized.contains("/Android/data/") &&
		(normalized.endsWith("/files/Download") ||
			normalized.endsWith("/files/Downloads") ||
			normalized.contains("/files/Download/") ||
			normalized.contains("/files/Downloads/"))
}

private fun receiveFolderKindOrNull(raw: String): ReceiveFolderKind? =
	runCatching { ReceiveFolderKind.valueOf(raw) }.getOrNull()

private fun themeModeOrNull(raw: String): ThemeMode? =
	runCatching { ThemeMode.valueOf(raw) }.getOrNull()

private const val AppPreferencesFileName = "app_preferences.preferences_pb"
