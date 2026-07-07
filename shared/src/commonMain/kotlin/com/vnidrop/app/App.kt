package com.vnidrop.app

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import com.vnidrop.app.core.CoreRepository
import com.vnidrop.app.core.PickedShareFile
import com.vnidrop.app.core.rememberShareFilePicker
import com.vnidrop.app.core.sharePickedFile
import com.vnidrop.app.logging.AppLogger
import com.vnidrop.app.platform.PlatformSystemAppearance
import com.vnidrop.app.ui.navigation.AppDestination
import com.vnidrop.app.ui.screens.ReceiveScreen
import com.vnidrop.app.ui.screens.SendScreen
import com.vnidrop.app.ui.screens.SettingsScreen
import com.vnidrop.app.ui.shell.AppShell
import com.vnidrop.app.ui.state.AppUiState
import com.vnidrop.app.ui.state.ReceiveUiState
import com.vnidrop.app.ui.state.SendUiState
import com.vnidrop.app.ui.state.windowClassFor
import com.vnidrop.app.ui.theme.VniDropTheme
import com.vnidrop.app.ui.theme.rememberResolvedDarkTheme
import kotlinx.coroutines.launch

@Composable
@Preview
fun App() {
	val platform = remember { getPlatform() }
	val repository = remember { CoreRepository() }
	val coreState by repository.state.collectAsState()
	var appState by remember { mutableStateOf(AppUiState()) }
	val appDataDir = platform.defaultCoreDataDir
	var sendState by remember { mutableStateOf(SendUiState()) }
	var receiveState by remember { mutableStateOf(ReceiveUiState(outputDirectory = platform.defaultReceiveDir)) }
	var selectedFile by remember { mutableStateOf<PickedShareFile?>(null) }
	val scope = rememberCoroutineScope()
	val clipboard = LocalClipboardManager.current
	val picker = rememberShareFilePicker(
		onFilePicked = { file ->
			AppLogger.info("file-picker", "file selected", mapOf("name" to file.displayName))
			selectedFile = file
			sendState = sendState.withSelectedFile(file)
		},
		onError = { error ->
			AppLogger.warn("file-picker", "file picker error", mapOf("reason" to error))
			scope.launch { repository.setError(error) }
		},
	)

	LaunchedEffect(Unit) {
		AppLogger.initialize(appDataDir)
		AppLogger.info("lifecycle", "app started", mapOf("platform" to platform.name))
		AppLogger.info("core", "automatic initialize requested", mapOf("appDataDir" to appDataDir))
		repository.initialize(appDataDir)
	}

	LaunchedEffect(coreState.lastShare?.transferId) {
		coreState.lastShare?.let { share -> repository.refreshReceiverRequests(share.transferId) }
	}

	val isDarkTheme = rememberResolvedDarkTheme(appState.themeMode)
	PlatformSystemAppearance(isDarkTheme)
	LaunchedEffect(isDarkTheme) {
		AppLogger.info("appearance", "system appearance synchronized", mapOf("dark" to isDarkTheme.toString()))
	}

	VniDropTheme(isDarkTheme = isDarkTheme) {
		BoxWithConstraints {
			val windowClass = windowClassFor(maxWidth.value)
			AppShell(
				selectedDestination = appState.destination,
				windowClass = windowClass,
				onDestinationSelected = { appState = appState.copy(destination = it) },
			) {
				when (appState.destination) {
					AppDestination.Send -> SendScreen(
						coreState = coreState,
						sendState = sendState,
						onSendStateChange = { sendState = it },
						onSelectFile = {
							AppLogger.info("file-picker", "open share file picker")
							picker.pickFile()
						},
						onCreateShare = {
							scope.launch {
								AppLogger.info("send", "create share requested", mapOf("source" to sendState.selectedSource))
								sendState = sendState.copy(isSharing = true)
								val file = selectedFile
								if (file == null) {
									repository.sharePath(sendState.selectedSource, sendState.transferName, sendState.senderName)
								} else {
									sharePickedFile(repository, file, sendState.transferName, sendState.senderName)
								}
								sendState = sendState.copy(isSharing = false)
							}
						},
						onCopyTicket = { ticket ->
							AppLogger.info("send", "ticket copied")
							clipboard.setText(AnnotatedString(ticket))
						},
						onUseLocally = { ticket ->
							receiveState = receiveState.copy(ticket = ticket)
							appState = appState.copy(destination = AppDestination.Receive)
						},
						onRefreshRequests = { transferId -> scope.launch { repository.refreshReceiverRequests(transferId) } },
						onRespondRequest = { requestId, accepted ->
							scope.launch {
								repository.respondReceiverRequest(
									requestId = requestId,
									accepted = accepted,
									reason = if (accepted) null else "sender-refused",
								)
							}
						},
					)
					AppDestination.Receive -> ReceiveScreen(
						coreState = coreState,
						receiveState = receiveState,
						onReceiveStateChange = { receiveState = it },
						onInspect = { scope.launch { repository.inspectTicket(receiveState.ticket) } },
						onReceive = {
							scope.launch {
								AppLogger.info("receive", "receive requested")
								receiveState = receiveState.copy(isReceiving = true)
								repository.receive(receiveState.ticket, receiveState.outputDirectory, receiveState.receiverName)
								receiveState = receiveState.copy(isReceiving = false)
							}
						},
					)
					AppDestination.Settings -> SettingsScreen(
						deviceInfo = platform.deviceInfo,
						coreState = coreState,
						themeMode = appState.themeMode,
						windowClass = windowClass,
						onThemeModeChange = {
							AppLogger.info("appearance", "theme mode changed", mapOf("mode" to it.name))
							appState = appState.copy(themeMode = it)
						},
					)
				}
			}
		}
	}
}

private fun SendUiState.withSelectedFile(file: PickedShareFile): SendUiState =
	copy(
		selectedSource = file.value,
		selectedDisplayName = file.displayName,
		transferName = if (transferName == "VniDrop transfer" || transferName.isBlank()) file.displayName else transferName,
	)
