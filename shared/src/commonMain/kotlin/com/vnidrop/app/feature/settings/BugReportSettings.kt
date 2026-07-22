package com.vnidrop.app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vnidrop.app.ui.components.Field
import com.vnidrop.app.ui.components.PrimaryButton
import com.vnidrop.app.ui.icons.AppIcon
import com.vnidrop.app.ui.theme.LocalVniDropColors
import org.jetbrains.compose.resources.stringResource
import vnidrop.shared.generated.resources.Res
import vnidrop.shared.generated.resources.about_bug_report
import vnidrop.shared.generated.resources.bug_report_contact_label
import vnidrop.shared.generated.resources.bug_report_description
import vnidrop.shared.generated.resources.bug_report_device_section
import vnidrop.shared.generated.resources.bug_report_expected_label
import vnidrop.shared.generated.resources.bug_report_include_logs
import vnidrop.shared.generated.resources.bug_report_include_logs_description
import vnidrop.shared.generated.resources.bug_report_logs_size
import vnidrop.shared.generated.resources.bug_report_steps_label
import vnidrop.shared.generated.resources.bug_report_submit
import vnidrop.shared.generated.resources.bug_report_submitting
import vnidrop.shared.generated.resources.bug_report_what_label
import vnidrop.shared.generated.resources.device_model_title
import vnidrop.shared.generated.resources.device_name_title
import vnidrop.shared.generated.resources.os_version_title
import vnidrop.shared.generated.resources.value_unavailable
import vnidrop.shared.generated.resources.version_title

@Composable
internal fun BugReportSettings(
	state: SettingsState,
	onWhatChanged: (String) -> Unit,
	onExpectedChanged: (String) -> Unit,
	onStepsChanged: (String) -> Unit,
	onContactChanged: (String) -> Unit,
	onIncludeLogsChanged: (Boolean) -> Unit,
	onSubmit: () -> Unit,
	onBack: () -> Unit,
	showBack: Boolean,
) {
	val unavailable = stringResource(Res.string.value_unavailable)
	val info = state.deviceInfo
	Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
		SettingsTopBar(stringResource(Res.string.about_bug_report), onBack, showBack)
		Text(
			stringResource(Res.string.bug_report_description),
			color = LocalVniDropColors.current.foregroundLighter,
			style = MaterialTheme.typography.bodyMedium,
		)
		Field(
			value = state.bugWhatHappened,
			onValueChange = onWhatChanged,
			label = stringResource(Res.string.bug_report_what_label),
			minLines = 3,
			enabled = !state.isSubmittingBugReport,
		)
		Field(
			value = state.bugExpected,
			onValueChange = onExpectedChanged,
			label = stringResource(Res.string.bug_report_expected_label),
			minLines = 2,
			enabled = !state.isSubmittingBugReport,
		)
		Field(
			value = state.bugSteps,
			onValueChange = onStepsChanged,
			label = stringResource(Res.string.bug_report_steps_label),
			minLines = 2,
			enabled = !state.isSubmittingBugReport,
		)
		Field(
			value = state.bugContact,
			onValueChange = onContactChanged,
			label = stringResource(Res.string.bug_report_contact_label),
			enabled = !state.isSubmittingBugReport,
		)
		SettingsGroup {
			SettingsToggleRow(
				icon = AppIcon.Document,
				title = stringResource(Res.string.bug_report_include_logs),
				description = stringResource(Res.string.bug_report_include_logs_description),
				checked = state.bugIncludeLogs,
				enabled = !state.isSubmittingBugReport,
				onCheckedChange = onIncludeLogsChanged,
			)
			if (state.bugIncludeLogs && state.bugLogPreviewBytes > 0) {
				SettingsDivider()
				InfoItem(
					stringResource(Res.string.bug_report_logs_size),
					formatByteSize(state.bugLogPreviewBytes),
				)
			}
		}
		Text(
			stringResource(Res.string.bug_report_device_section),
			style = MaterialTheme.typography.titleSmall,
		)
		SettingsGroup {
			InfoItem(stringResource(Res.string.version_title), state.appVersion)
			SettingsDivider(startPadding = 16.dp)
			InfoItem(stringResource(Res.string.device_name_title), info?.deviceName.orUnavailable(unavailable))
			SettingsDivider(startPadding = 16.dp)
			InfoItem(stringResource(Res.string.device_model_title), info?.deviceModel.orUnavailable(unavailable))
			SettingsDivider(startPadding = 16.dp)
			InfoItem(stringResource(Res.string.os_version_title), info?.operatingSystem ?: unavailable)
		}
		Row(modifier = Modifier.fillMaxWidth()) {
			PrimaryButton(
				text = if (state.isSubmittingBugReport) {
					stringResource(Res.string.bug_report_submitting)
				} else {
					stringResource(Res.string.bug_report_submit)
				},
				onClick = onSubmit,
				enabled = !state.isSubmittingBugReport,
				modifier = Modifier.fillMaxWidth(),
			)
		}
	}
}

private fun String?.orUnavailable(fallback: String): String =
	this?.takeIf(String::isNotBlank) ?: fallback

private fun formatByteSize(bytes: Int): String = when {
	bytes < 1024 -> "$bytes B"
	bytes < 1024 * 1024 -> "${bytes / 1024} KB"
	else -> "${bytes / (1024 * 1024)} MB"
}
