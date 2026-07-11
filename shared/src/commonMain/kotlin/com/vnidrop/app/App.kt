package com.vnidrop.app

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vnidrop.app.feature.app.AppViewModel
import com.vnidrop.app.feature.app.AppGraphViewModel
import com.vnidrop.app.feature.approvals.ApprovalModalHost
import com.vnidrop.app.feature.receive.ReceiveRoute
import com.vnidrop.app.feature.receive.ReceiveViewModel
import com.vnidrop.app.feature.send.SendRoute
import com.vnidrop.app.feature.send.SendFloatingAction
import com.vnidrop.app.feature.send.SendViewModel
import com.vnidrop.app.feature.settings.SettingsRoute
import com.vnidrop.app.feature.settings.SettingsViewModel
import com.vnidrop.app.platform.PlatformSystemAppearance
import com.vnidrop.app.ui.feedback.VniDropSnackbarHost
import com.vnidrop.app.ui.navigation.AppDestination
import com.vnidrop.app.ui.shell.AppShell
import com.vnidrop.app.ui.shell.ScreenScrollContainer
import com.vnidrop.app.core.TransferDirection
import com.vnidrop.app.ui.state.WindowClass
import com.vnidrop.app.ui.state.windowClassFor
import com.vnidrop.app.ui.theme.VniDropTheme
import com.vnidrop.app.ui.theme.rememberResolvedDarkTheme

@Composable
fun App(dependencies: AppDependencies) {
	val graphHolder = viewModel { AppGraphViewModel(dependencies) }
	val graph = graphHolder.graph

	val appViewModel = viewModel {
		AppViewModel(dependencies.environment, graph.coreRepository, graph.preferencesRepository, graph.messages)
	}
	val sendViewModel = viewModel {
		SendViewModel(
			graph.coreRepository,
			dependencies.fileSystemService,
			graph.preferencesRepository,
			graph.filePreviewRepository,
			graph.messages,
		)
	}
	val receiveViewModel = viewModel {
		ReceiveViewModel(graph.coreRepository, dependencies.fileSystemService, graph.preferencesRepository, graph.messages)
	}
	val settingsViewModel = viewModel {
		SettingsViewModel(
			dependencies.environment,
			dependencies.deviceInfoProvider,
			dependencies.fileSystemService,
			graph.preferencesRepository,
			dependencies.localNotificationService,
			graph.messages,
		)
	}
	val appState by appViewModel.state.collectAsStateWithLifecycle()
	val sendState by sendViewModel.state.collectAsStateWithLifecycle()
	val sendCoreState by sendViewModel.coreState.collectAsStateWithLifecycle()
	val approvalState by graph.approvalCoordinator.state.collectAsStateWithLifecycle()
	val lifecycleOwner = LocalLifecycleOwner.current
	DisposableEffect(lifecycleOwner, graph, settingsViewModel) {
		val observer = LifecycleEventObserver { _, event ->
			when (event) {
				Lifecycle.Event.ON_START -> {
					graph.visibility.setForeground(true)
					settingsViewModel.refreshNotificationPermission()
				}
				Lifecycle.Event.ON_STOP -> graph.visibility.setForeground(false)
				else -> Unit
			}
		}
		lifecycleOwner.lifecycle.addObserver(observer)
		onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
	}

	val darkTheme = rememberResolvedDarkTheme(appState.themeMode)
	PlatformSystemAppearance(darkTheme)
	VniDropTheme(isDarkTheme = darkTheme) {
		BoxWithConstraints {
			val windowClass = windowClassFor(maxWidth.value)
			val showSendAction = appState.destination == AppDestination.Send &&
				windowClass == WindowClass.Phone &&
				sendState.selectedTransferId?.let { selectedId ->
					sendCoreState.transfers.any { it.transferId == selectedId }
				} != true &&
				sendCoreState.transfers.any { it.direction == TransferDirection.Send }
			AppShell(
				modifier = Modifier.fillMaxSize(),
				selectedDestination = appState.destination,
				windowClass = windowClass,
				onDestinationSelected = appViewModel::selectDestination,
				overlay = {
					VniDropSnackbarHost(graph.messages, Modifier.align(Alignment.BottomCenter))
				},
				floatingAction = if (showSendAction) {
					{
						SendFloatingAction(
							onClick = sendViewModel::openComposer,
							modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
						)
					}
				} else {
					null
				},
			) {
				when (appState.destination) {
					AppDestination.Send -> SendRoute(sendViewModel, windowClass)
					AppDestination.Receive -> ScreenScrollContainer { ReceiveRoute(receiveViewModel) }
					AppDestination.Settings -> ScreenScrollContainer { SettingsRoute(settingsViewModel, windowClass) }
				}
			}
			ApprovalModalHost(
				state = approvalState,
				onAccept = graph.approvalCoordinator::accept,
				onRefuse = graph.approvalCoordinator::refuse,
			)
		}
	}
}
