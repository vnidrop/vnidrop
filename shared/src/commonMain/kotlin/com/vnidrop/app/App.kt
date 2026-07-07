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
import com.vnidrop.app.core.rememberShareFilePicker
import com.vnidrop.app.logging.AppLogger
import com.vnidrop.app.platform.PlatformSystemAppearance
import com.vnidrop.app.ui.navigation.AppDestination
import com.vnidrop.app.ui.screens.ReceiveScreen
import com.vnidrop.app.ui.screens.SendScreen
import com.vnidrop.app.ui.screens.SettingsScreen
import com.vnidrop.app.ui.shell.AppShell
import com.vnidrop.app.ui.state.windowClassFor
import com.vnidrop.app.ui.theme.VniDropTheme
import com.vnidrop.app.ui.theme.rememberResolvedDarkTheme

@Composable
@Preview
fun App() {
	val platform = remember { getPlatform() }
	val viewModel = viewModel {
		VniDropAppViewModel(
			appDataDir = platform.defaultCoreDataDir,
			defaultReceiveDir = platform.defaultReceiveDir,
			platformName = platform.name,
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

	LaunchedEffect(viewModel) {
		viewModel.effectFlow.collect { effect ->
			when (effect) {
				VniDropAppEffect.OpenShareFilePicker -> {
					AppLogger.info("file-picker", "open share file picker")
					picker.pickFile()
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
						onEvent = viewModel::onEvent,
					)
					AppDestination.Receive -> ReceiveScreen(
						coreState = coreState,
						receiveState = state.receive,
						onEvent = viewModel::onEvent,
					)
					AppDestination.Settings -> SettingsScreen(
						deviceInfo = platform.deviceInfo,
						coreState = coreState,
						themeMode = state.app.themeMode,
						windowClass = windowClass,
						onThemeModeChange = { viewModel.onEvent(VniDropAppEvent.ThemeModeChanged(it)) },
					)
				}
			}
		}
	}
}
