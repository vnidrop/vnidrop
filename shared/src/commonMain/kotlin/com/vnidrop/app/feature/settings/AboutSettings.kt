package com.vnidrop.app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import vnidrop.shared.generated.resources.Res
import vnidrop.shared.generated.resources.about_bug_report
import vnidrop.shared.generated.resources.about_privacy
import vnidrop.shared.generated.resources.about_title
import vnidrop.shared.generated.resources.battery_level_title
import vnidrop.shared.generated.resources.device_model_title
import vnidrop.shared.generated.resources.device_name_title
import vnidrop.shared.generated.resources.network_title
import vnidrop.shared.generated.resources.os_version_title
import vnidrop.shared.generated.resources.value_unavailable
import vnidrop.shared.generated.resources.version_title

@Composable
internal fun AboutSettings(state: SettingsState, onBack: () -> Unit, showBack: Boolean) {
	val unavailable = stringResource(Res.string.value_unavailable)
	val info = state.deviceInfo
	Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
		SettingsTopBar(stringResource(Res.string.about_title), onBack, showBack)
		SettingsGroup {
			SettingsRow(
				icon = SettingsIcons.Document,
				title = stringResource(Res.string.about_privacy),
				iconTone = SettingsIconTone.Neutral,
			)
			SettingsDivider()
			SettingsRow(
				icon = SettingsIcons.Bug,
				title = stringResource(Res.string.about_bug_report),
				iconTone = SettingsIconTone.Neutral,
			)
		}
		SettingsGroup {
			InfoItem(stringResource(Res.string.version_title), state.appVersion)
			SettingsDivider(startPadding = 16.dp)
			InfoItem(stringResource(Res.string.device_name_title), info?.deviceName.orUnavailable(unavailable))
			SettingsDivider(startPadding = 16.dp)
			InfoItem(stringResource(Res.string.device_model_title), info?.deviceModel.orUnavailable(unavailable))
			SettingsDivider(startPadding = 16.dp)
			InfoItem(stringResource(Res.string.os_version_title), info?.operatingSystem ?: unavailable)
			SettingsDivider(startPadding = 16.dp)
			InfoItem(stringResource(Res.string.network_title), info?.network.orUnavailable(unavailable))
			SettingsDivider(startPadding = 16.dp)
			InfoItem(stringResource(Res.string.battery_level_title), info?.batteryLevel.orUnavailable(unavailable))
		}
	}
}

private fun String?.orUnavailable(fallback: String): String = this?.takeIf(String::isNotBlank) ?: fallback
