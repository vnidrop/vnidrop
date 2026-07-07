package com.vnidrop.app

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vnidrop.app.core.rememberFileSystemService
import com.vnidrop.app.core.rememberReceiveFolderPicker
import com.vnidrop.app.core.rememberShareFilePicker
import com.vnidrop.app.logging.AppLogger
import com.vnidrop.app.preferences.AppPreferencesDefaults
import com.vnidrop.app.preferences.AppPreferencesRepository
import com.vnidrop.app.preferences.createAppPreferencesDataStore
import com.vnidrop.app.platform.PlatformSystemAppearance
import com.vnidrop.app.ui.navigation.AppDestination
import com.vnidrop.app.ui.screens.ReceiveScreen
import com.vnidrop.app.ui.screens.SendScreen
import com.vnidrop.app.ui.screens.SettingsScreen
import com.vnidrop.app.ui.shell.AppShell
import com.vnidrop.app.ui.state.windowClassFor
import com.vnidrop.app.ui.theme.ThemeMode
import com.vnidrop.app.ui.theme.VniDropTheme
import com.vnidrop.app.ui.theme.rememberResolvedDarkTheme

@Composable
@Preview
fun App() {
	val platform = remember { getPlatform() }
	val fileSystemService = rememberFileSystemService()
	val preferencesRepository = remember(platform.defaultCoreDataDir) {
		AppPreferencesRepository(
			dataStore = createAppPreferencesDataStore(platform.defaultCoreDataDir),
			defaults = AppPreferencesDefaults(
				username = platform.deviceInfo.deviceName?.takeIf { it.isNotBlank() } ?: "Receiver",
				receiveFolder = fileSystemService.defaultReceiveFolder(),
				themeMode = ThemeMode.System,
			),
		)
	}
	val viewModel = viewModel {
		VniDropAppViewModel(
			appDataDir = platform.defaultCoreDataDir,
			platformName = platform.name,
			preferencesRepository = preferencesRepository,
			fileSystemService = fileSystemService,
		)
	}
	val state by viewModel.state.collectAsStateWithLifecycle()
	val coreState by viewModel.coreState.collectAsStateWithLifecycle()
	val clipboard = LocalClipboardManager.current
	val picker = rememberShareFilePicker(
		onFilePicked = { file ->
			viewModel.onEvent(VniDropAppEvent.ShareFilePicked(file))
		},
		onError = { error ->
			viewModel.onEvent(VniDropAppEvent.ShareFilePickFailed(error))
		},
	)
	val receiveFolderPicker = rememberReceiveFolderPicker(
		onFolderPicked = { folder ->
			viewModel.onEvent(VniDropAppEvent.ReceiveFolderPicked(folder))
		},
		onError = { error ->
			viewModel.onEvent(VniDropAppEvent.ReceiveFolderPickFailed(error))
		},
	)

	LaunchedEffect(viewModel) {
		viewModel.effectFlow.collect { effect ->
			when (effect) {
				VniDropAppEffect.OpenShareFilePicker -> {
					AppLogger.info("file-picker", "open share file picker")
					picker.pickFile()
				}
				VniDropAppEffect.OpenReceiveFolderPicker -> {
					AppLogger.info("file-picker", "open receive folder picker")
					receiveFolderPicker.pickFolder()
				}
				is VniDropAppEffect.CopyTicket -> {
					AppLogger.info("send", "ticket copied")
					clipboard.setText(AnnotatedString(effect.ticket))
				}
			}
		}
	}

	val isDarkTheme = rememberResolvedDarkTheme(state.app.themeMode)
	PlatformSystemAppearance(isDarkTheme)
	LaunchedEffect(isDarkTheme) {
		AppLogger.info("appearance", "system appearance synchronized", mapOf("dark" to isDarkTheme.toString()))
	}

	VniDropTheme(isDarkTheme = isDarkTheme) {
		BoxWithConstraints {
			val windowClass = windowClassFor(maxWidth.value)
			AppShell(
				selectedDestination = state.app.destination,
				windowClass = windowClass,
				onDestinationSelected = { viewModel.onEvent(VniDropAppEvent.DestinationSelected(it)) },
			) {
				when (state.app.destination) {
					AppDestination.Send -> SendScreen(
						coreState = coreState,
						sendState = state.send,
						windowClass = windowClass,
						onEvent = viewModel::onEvent,
					)
					AppDestination.Receive -> ReceiveScreen(
						coreState = coreState,
						receiveState = state.receive,
						preferencesState = state.preferences,
						onEvent = viewModel::onEvent,
					)
					AppDestination.Settings -> SettingsScreen(
						deviceInfo = platform.deviceInfo,
						coreState = coreState,
						themeMode = state.app.themeMode,
						preferencesState = state.preferences,
						windowClass = windowClass,
						onEvent = viewModel::onEvent,
					)
				}
			}
		}
	}
}
