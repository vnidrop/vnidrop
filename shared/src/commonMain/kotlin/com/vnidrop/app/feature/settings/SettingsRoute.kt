package com.vnidrop.app.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vnidrop.app.core.rememberReceiveFolderPicker
import com.vnidrop.app.ui.state.WindowClass

@Composable
fun SettingsRoute(viewModel: SettingsViewModel, windowClass: WindowClass) {
	val state by viewModel.state.collectAsStateWithLifecycle()
	val picker = rememberReceiveFolderPicker(viewModel::onReceiveFolderPicked, viewModel::onReceiveFolderPickFailed)
	LaunchedEffect(viewModel) {
		viewModel.effectFlow.collect { effect ->
			when (effect) {
				SettingsEffect.OpenReceiveFolderPicker -> picker.pickFolder()
			}
		}
	}
	SettingsScreen(
		state = state,
		windowClass = windowClass,
		onSectionSelected = viewModel::selectSection,
		onUsernameChanged = viewModel::setUsername,
		onThemeModeChanged = viewModel::setThemeMode,
		onChooseFolder = viewModel::chooseReceiveFolder,
		onResetFolder = viewModel::resetReceiveFolder,
		onNotificationsChanged = viewModel::setNotificationsEnabled,
		onOpenNotificationSettings = viewModel::openNotificationSettings,
		onRelayModeChanged = viewModel::setRelayMode,
		onRelayCustomUrlsChanged = viewModel::setRelayCustomUrlsDraft,
		onApplyRelayCustomUrls = viewModel::commitRelayCustomUrls,
		onDiagnosticsChanged = viewModel::setDiagnosticsEnabled,
		onBugWhatChanged = viewModel::setBugWhatHappened,
		onBugExpectedChanged = viewModel::setBugExpected,
		onBugStepsChanged = viewModel::setBugSteps,
		onBugContactChanged = viewModel::setBugContact,
		onBugIncludeLogsChanged = viewModel::setBugIncludeLogs,
		onSubmitBugReport = viewModel::submitBugReport,
	)
}
