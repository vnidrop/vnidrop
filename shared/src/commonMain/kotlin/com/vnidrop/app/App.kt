package com.vnidrop.app

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vnidrop.app.feature.app.AppViewModel
import com.vnidrop.app.feature.app.AppGraphViewModel
import com.vnidrop.app.feature.approvals.ApprovalBannerHost
import com.vnidrop.app.feature.receive.ReceiveRoute
import com.vnidrop.app.feature.receive.ReceiveViewModel
import com.vnidrop.app.feature.send.SendRoute
import com.vnidrop.app.feature.send.SendViewModel
import com.vnidrop.app.feature.settings.SettingsRoute
import com.vnidrop.app.feature.settings.SettingsViewModel
import com.vnidrop.app.platform.PlatformSystemAppearance
import com.vnidrop.app.ui.feedback.VniDropSnackbarHost
import com.vnidrop.app.ui.navigation.AppDestination
import com.vnidrop.app.ui.shell.AppShell
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
		SendViewModel(graph.coreRepository, dependencies.fileSystemService, graph.preferencesRepository, graph.messages)
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
			Column(Modifier.fillMaxSize()) {
				ApprovalBannerHost(
					state = approvalState,
					onAccept = graph.approvalCoordinator::accept,
					onRefuse = graph.approvalCoordinator::refuse,
					modifier = Modifier.align(Alignment.CenterHorizontally),
				)
				AppShell(
					modifier = Modifier.fillMaxSize().weight(1f),
					selectedDestination = appState.destination,
					windowClass = windowClass,
					onDestinationSelected = appViewModel::selectDestination,
					overlay = {
						VniDropSnackbarHost(graph.messages, Modifier.align(Alignment.BottomCenter))
					},
				) {
					when (appState.destination) {
						AppDestination.Send -> SendRoute(sendViewModel, windowClass) { ticket ->
							receiveViewModel.setTicket(ticket)
							appViewModel.selectDestination(AppDestination.Receive)
						}
						AppDestination.Receive -> ReceiveRoute(receiveViewModel)
						AppDestination.Settings -> SettingsRoute(settingsViewModel, windowClass)
					}
				}
			}
		}
	}
}
