package com.vnidrop.app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vnidrop.app.preferences.RelayModeSetting
import com.vnidrop.app.ui.components.Field
import com.vnidrop.app.ui.components.PrimaryButton
import com.vnidrop.app.ui.feedback.UiText
import com.vnidrop.app.ui.icons.AppIcon
import com.vnidrop.app.ui.icons.PlatformIcon
import com.vnidrop.app.ui.theme.LocalVniDropColors
import org.jetbrains.compose.resources.stringResource
import vnidrop.shared.generated.resources.Res
import vnidrop.shared.generated.resources.relay_custom_apply
import vnidrop.shared.generated.resources.relay_custom_urls_help
import vnidrop.shared.generated.resources.relay_custom_urls_label
import vnidrop.shared.generated.resources.relay_mode_custom
import vnidrop.shared.generated.resources.relay_mode_custom_description
import vnidrop.shared.generated.resources.relay_mode_default
import vnidrop.shared.generated.resources.relay_mode_default_description
import vnidrop.shared.generated.resources.relay_mode_none
import vnidrop.shared.generated.resources.relay_mode_none_description
import vnidrop.shared.generated.resources.relay_none_warning
import vnidrop.shared.generated.resources.relay_restart_required
import vnidrop.shared.generated.resources.relay_title

@Composable
internal fun RelaySettings(
	state: SettingsState,
	onModeChanged: (RelayModeSetting) -> Unit,
	onCustomUrlsChanged: (String) -> Unit,
	onApplyCustomUrls: () -> Unit,
	onBack: () -> Unit,
	showBack: Boolean,
) {
	val colors = LocalVniDropColors.current
	Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
		SettingsTopBar(stringResource(Res.string.relay_title), onBack, showBack)
		SettingsGroup {
			RelayModeRow(
				icon = relayModeIcon(RelayModeSetting.Standard),
				title = stringResource(Res.string.relay_mode_default),
				selected = state.relay.mode == RelayModeSetting.Standard,
			) { onModeChanged(RelayModeSetting.Standard) }
			SettingsDivider()
			RelayModeRow(
				icon = relayModeIcon(RelayModeSetting.Custom),
				title = stringResource(Res.string.relay_mode_custom),
				selected = state.relay.mode == RelayModeSetting.Custom,
			) { onModeChanged(RelayModeSetting.Custom) }
			SettingsDivider()
			RelayModeRow(
				icon = relayModeIcon(RelayModeSetting.Disabled),
				title = stringResource(Res.string.relay_mode_none),
				selected = state.relay.mode == RelayModeSetting.Disabled,
			) { onModeChanged(RelayModeSetting.Disabled) }
		}

		Text(
			relayModeDescription(state.relay.mode),
			color = colors.foregroundLighter,
			style = MaterialTheme.typography.bodySmall,
		)

		if (state.relay.mode == RelayModeSetting.Disabled) {
			Text(
				stringResource(Res.string.relay_none_warning),
				color = colors.warningDefault,
				style = MaterialTheme.typography.bodySmall,
			)
		}

		if (state.relay.mode == RelayModeSetting.Custom) {
			Field(
				value = state.relayCustomUrlsDraft,
				onValueChange = onCustomUrlsChanged,
				label = stringResource(Res.string.relay_custom_urls_label),
				minLines = 3,
			)
			Text(
				stringResource(Res.string.relay_custom_urls_help),
				color = colors.foregroundLighter,
				style = MaterialTheme.typography.bodySmall,
			)
			state.relayValidationError?.let { error ->
				Text(
					error.resolveText(),
					color = colors.destructiveDefault,
					style = MaterialTheme.typography.bodySmall,
				)
			}
			PrimaryButton(stringResource(Res.string.relay_custom_apply), onClick = onApplyCustomUrls)
		}

		if (state.relayNeedsRestart) {
			Text(
				stringResource(Res.string.relay_restart_required),
				color = colors.foregroundLighter,
				style = MaterialTheme.typography.bodySmall,
			)
		}
	}
}

@Composable
private fun RelayModeRow(icon: AppIcon, title: String, selected: Boolean, onClick: () -> Unit) {
	SettingsRow(
		icon = icon,
		title = title,
		selected = selected,
		onClick = onClick,
		showsDisclosure = false,
		trailing = if (selected) {
			{
				PlatformIcon(
					AppIcon.Check,
					contentDescription = null,
					tint = LocalVniDropColors.current.brandLink,
					modifier = Modifier.size(20.dp),
				)
			}
		} else {
			null
		},
	)
}

internal fun relayModeIcon(mode: RelayModeSetting): AppIcon = when (mode) {
	RelayModeSetting.Standard -> AppIcon.Radio
	RelayModeSetting.Custom -> AppIcon.Globe
	RelayModeSetting.Disabled -> AppIcon.CloudOff
}

@Composable
internal fun relayModeLabel(mode: RelayModeSetting): String = when (mode) {
	RelayModeSetting.Standard -> stringResource(Res.string.relay_mode_default)
	RelayModeSetting.Custom -> stringResource(Res.string.relay_mode_custom)
	RelayModeSetting.Disabled -> stringResource(Res.string.relay_mode_none)
}

@Composable
private fun relayModeDescription(mode: RelayModeSetting): String = when (mode) {
	RelayModeSetting.Standard -> stringResource(Res.string.relay_mode_default_description)
	RelayModeSetting.Custom -> stringResource(Res.string.relay_mode_custom_description)
	RelayModeSetting.Disabled -> stringResource(Res.string.relay_mode_none_description)
}

@Composable
private fun UiText.resolveText(): String = when (this) {
	is UiText.Dynamic -> value
	is UiText.Resource -> stringResource(resource)
}
