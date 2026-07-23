package com.vnidrop.app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vnidrop.app.core.RelayMode
import com.vnidrop.app.ui.components.Field
import com.vnidrop.app.ui.components.PrimaryButton
import com.vnidrop.app.ui.icons.AppIcon
import com.vnidrop.app.ui.icons.PlatformIcon
import com.vnidrop.app.ui.theme.LocalVniDropColors
import org.jetbrains.compose.resources.stringResource
import vnidrop.shared.generated.resources.Res
import vnidrop.shared.generated.resources.approval_endpoint_id
import vnidrop.shared.generated.resources.relay_apply
import vnidrop.shared.generated.resources.relay_apply_active_transfers
import vnidrop.shared.generated.resources.relay_apply_failed
import vnidrop.shared.generated.resources.relay_apply_restart_description
import vnidrop.shared.generated.resources.relay_applying
import vnidrop.shared.generated.resources.relay_custom_urls_help
import vnidrop.shared.generated.resources.relay_custom_urls_label
import vnidrop.shared.generated.resources.relay_mode_automatic
import vnidrop.shared.generated.resources.relay_mode_automatic_description
import vnidrop.shared.generated.resources.relay_mode_custom
import vnidrop.shared.generated.resources.relay_mode_custom_description
import vnidrop.shared.generated.resources.relay_privacy_description
import vnidrop.shared.generated.resources.relay_restore_failed
import vnidrop.shared.generated.resources.relay_strict_warning
import vnidrop.shared.generated.resources.relay_validation_duplicate_url
import vnidrop.shared.generated.resources.relay_validation_https_required
import vnidrop.shared.generated.resources.relay_validation_invalid_url
import vnidrop.shared.generated.resources.relay_validation_missing_url
import vnidrop.shared.generated.resources.relay_validation_too_many_urls
import vnidrop.shared.generated.resources.settings_network_title

@Composable
internal fun NetworkSettings(
	state: SettingsState,
	onModeChanged: (RelayMode) -> Unit,
	onUrlsChanged: (String) -> Unit,
	onApply: () -> Unit,
	onBack: () -> Unit,
	showBack: Boolean,
) {
	val colors = LocalVniDropColors.current
	Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
		SettingsTopBar(stringResource(Res.string.settings_network_title), onBack, showBack)
		state.endpointId?.takeIf(String::isNotBlank)?.let { endpointId ->
			SelectionContainer {
				Text(
					stringResource(Res.string.approval_endpoint_id, endpointId),
					color = colors.foregroundLighter,
					style = MaterialTheme.typography.bodySmall,
				)
			}
		}
		SettingsGroup {
			RelayModeRow(
				icon = AppIcon.Globe,
				title = stringResource(Res.string.relay_mode_automatic),
				description = stringResource(Res.string.relay_mode_automatic_description),
				selected = state.relayMode == RelayMode.Automatic,
				enabled = !state.isApplyingRelaySettings,
				onClick = { onModeChanged(RelayMode.Automatic) },
			)
			SettingsDivider()
			RelayModeRow(
				icon = AppIcon.Radio,
				title = stringResource(Res.string.relay_mode_custom),
				description = stringResource(Res.string.relay_mode_custom_description),
				selected = state.relayMode == RelayMode.Custom,
				enabled = !state.isApplyingRelaySettings,
				onClick = { onModeChanged(RelayMode.Custom) },
			)
		}
		if (state.relayMode == RelayMode.Custom) {
			Field(
				value = state.relayUrlsText,
				onValueChange = onUrlsChanged,
				label = stringResource(Res.string.relay_custom_urls_label),
				minLines = 3,
				enabled = !state.isApplyingRelaySettings,
			)
			Text(
				stringResource(Res.string.relay_custom_urls_help),
				color = colors.foregroundLighter,
				style = MaterialTheme.typography.bodySmall,
			)
			Text(
				stringResource(Res.string.relay_strict_warning),
				color = colors.foregroundLight,
				style = MaterialTheme.typography.bodySmall,
				fontWeight = FontWeight.Medium,
			)
			Text(
				stringResource(Res.string.relay_privacy_description),
				color = colors.foregroundLighter,
				style = MaterialTheme.typography.bodySmall,
			)
		}
		relayErrorText(state)?.let { error ->
			Text(
				error,
				color = colors.destructiveDefault,
				style = MaterialTheme.typography.bodySmall,
				fontWeight = FontWeight.Medium,
			)
		}
		Text(
			stringResource(Res.string.relay_apply_restart_description),
			color = colors.foregroundLighter,
			style = MaterialTheme.typography.bodySmall,
		)
		PrimaryButton(
			text = stringResource(
				if (state.isApplyingRelaySettings) Res.string.relay_applying else Res.string.relay_apply,
			),
			onClick = onApply,
			modifier = Modifier.fillMaxWidth(),
			enabled = state.hasRelaySettingsChanges &&
				!state.isApplyingRelaySettings &&
				!state.hasActiveNetworkWork,
		)
	}
}

@Composable
private fun RelayModeRow(
	icon: AppIcon,
	title: String,
	description: String,
	selected: Boolean,
	enabled: Boolean,
	onClick: () -> Unit,
) {
	SettingsRow(
		icon = icon,
		title = title,
		subtitle = description,
		selected = selected,
		onClick = onClick.takeIf { enabled },
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

@Composable
private fun relayErrorText(state: SettingsState): String? {
	if (state.hasActiveNetworkWork || state.relayApplyError == RelaySettingsApplyError.ActiveTransfers) {
		return stringResource(Res.string.relay_apply_active_transfers)
	}
	state.relayInputError?.let { error ->
		return when (error) {
			RelaySettingsInputError.MissingUrl -> stringResource(Res.string.relay_validation_missing_url)
			is RelaySettingsInputError.TooManyUrls -> stringResource(
				Res.string.relay_validation_too_many_urls,
				error.maximum,
			)
			is RelaySettingsInputError.HttpsRequired -> stringResource(
				Res.string.relay_validation_https_required,
				error.line,
			)
			is RelaySettingsInputError.InvalidUrl -> stringResource(
				Res.string.relay_validation_invalid_url,
				error.line,
			)
			is RelaySettingsInputError.DuplicateUrl -> stringResource(
				Res.string.relay_validation_duplicate_url,
				error.line,
			)
		}
	}
	return when (state.relayApplyError) {
		RelaySettingsApplyError.ApplyFailed -> stringResource(Res.string.relay_apply_failed)
		RelaySettingsApplyError.RestoreFailed -> stringResource(Res.string.relay_restore_failed)
		RelaySettingsApplyError.ActiveTransfers,
		null,
		-> null
	}
}
