package com.vnidrop.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.vnidrop.app.core.CoreUiState
import com.vnidrop.app.logging.LogFileInfo
import com.vnidrop.app.ui.components.AppCard
import com.vnidrop.app.ui.components.Field
import com.vnidrop.app.ui.components.MetadataRow
import com.vnidrop.app.ui.components.PrimaryButton
import com.vnidrop.app.ui.components.SecondaryButton
import com.vnidrop.app.ui.theme.ThemeMode
import org.jetbrains.compose.resources.stringResource
import vnidrop.shared.generated.resources.Res
import vnidrop.shared.generated.resources.appearance_title
import vnidrop.shared.generated.resources.button_hide_event_log
import vnidrop.shared.generated.resources.button_initialize_core
import vnidrop.shared.generated.resources.button_refresh_logs
import vnidrop.shared.generated.resources.button_show_event_log
import vnidrop.shared.generated.resources.diagnostics_hint
import vnidrop.shared.generated.resources.diagnostics_title
import vnidrop.shared.generated.resources.field_core_data_directory
import vnidrop.shared.generated.resources.metadata_log_directory
import vnidrop.shared.generated.resources.metadata_platform
import vnidrop.shared.generated.resources.metadata_status
import vnidrop.shared.generated.resources.no_logs
import vnidrop.shared.generated.resources.node_title
import vnidrop.shared.generated.resources.not_initialized
import vnidrop.shared.generated.resources.settings_subtitle
import vnidrop.shared.generated.resources.settings_title

@Composable
fun SettingsScreen(
	platformName: String,
	appDataDir: String,
	onAppDataDirChange: (String) -> Unit,
	coreState: CoreUiState,
	themeMode: ThemeMode,
	onThemeModeChange: (ThemeMode) -> Unit,
	diagnosticsVisible: Boolean,
	onDiagnosticsVisibleChange: (Boolean) -> Unit,
	logDirectory: String?,
	logFiles: List<LogFileInfo>,
	onRefreshLogs: () -> Unit,
	onInitialize: () -> Unit,
) {
	Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
		ScreenHeader(stringResource(Res.string.settings_title), stringResource(Res.string.settings_subtitle))
		ErrorSection(coreState)
		AppCard(title = stringResource(Res.string.node_title)) {
			MetadataRow(stringResource(Res.string.metadata_platform), platformName)
			MetadataRow(stringResource(Res.string.metadata_status), coreState.status)
			Field(value = appDataDir, onValueChange = onAppDataDirChange, label = stringResource(Res.string.field_core_data_directory))
			PrimaryButton(stringResource(Res.string.button_initialize_core), onClick = onInitialize)
		}
		AppCard(title = stringResource(Res.string.appearance_title)) {
			ThemeMode.entries.forEach { mode ->
				ThemeModeRow(
					mode = mode,
					selected = themeMode == mode,
					onSelected = { onThemeModeChange(mode) },
				)
			}
		}
		AppCard(title = stringResource(Res.string.diagnostics_title)) {
			SecondaryButton(
				text = if (diagnosticsVisible) stringResource(Res.string.button_hide_event_log) else stringResource(Res.string.button_show_event_log),
				onClick = { onDiagnosticsVisibleChange(!diagnosticsVisible) },
			)
			SecondaryButton(text = stringResource(Res.string.button_refresh_logs), onClick = onRefreshLogs)
			MetadataRow(stringResource(Res.string.metadata_log_directory), logDirectory ?: stringResource(Res.string.not_initialized))
			if (logFiles.isEmpty()) {
				EmptyText(stringResource(Res.string.no_logs))
			} else {
				logFiles.forEach { file ->
					MetadataRow(file.name, "${file.sizeBytes} bytes")
				}
			}
			EmptyText(stringResource(Res.string.diagnostics_hint))
		}
		if (diagnosticsVisible) {
			DiagnosticsPanel(events = coreState.events)
		}
	}
}

@Composable
private fun ThemeModeRow(
	mode: ThemeMode,
	selected: Boolean,
	onSelected: () -> Unit,
) {
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.clip(RoundedCornerShape(8.dp))
			.selectable(selected = selected, onClick = onSelected)
			.padding(vertical = 8.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		RadioButton(selected = selected, onClick = onSelected)
		Text(mode.name)
	}
}
