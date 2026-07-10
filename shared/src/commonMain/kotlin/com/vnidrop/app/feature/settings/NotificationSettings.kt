package com.vnidrop.app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vnidrop.app.notifications.NotificationPermission
import com.vnidrop.app.ui.components.SecondaryButton
import com.vnidrop.app.ui.theme.LocalVniDropColors
import org.jetbrains.compose.resources.stringResource
import vnidrop.shared.generated.resources.Res
import vnidrop.shared.generated.resources.button_open_settings
import vnidrop.shared.generated.resources.notifications_description
import vnidrop.shared.generated.resources.notifications_local_title
import vnidrop.shared.generated.resources.notifications_permission_denied
import vnidrop.shared.generated.resources.notifications_unsupported
import vnidrop.shared.generated.resources.notifications_title

@Composable
internal fun NotificationSettings(
	state: SettingsState,
	onEnabledChanged: (Boolean) -> Unit,
	onOpenSettings: () -> Unit,
	onBack: () -> Unit,
	showBack: Boolean,
) {
	Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
		SettingsTopBar(stringResource(Res.string.notifications_title), onBack, showBack)
		SettingsGroup {
			SettingsToggleRow(
				icon = SettingsIcons.Bell,
				title = stringResource(Res.string.notifications_local_title),
				description = stringResource(Res.string.notifications_description),
				checked = state.notificationsEnabled,
				enabled = state.notificationPermission != NotificationPermission.Unsupported,
				onCheckedChange = onEnabledChanged,
			)
		}
		when (state.notificationPermission) {
			NotificationPermission.Denied -> NotificationPermissionHelp(onOpenSettings)
			NotificationPermission.Unsupported -> NotificationSupportText(stringResource(Res.string.notifications_unsupported))
			else -> Unit
		}
	}
}

@Composable
private fun NotificationPermissionHelp(onOpenSettings: () -> Unit) {
	Column(
		modifier = Modifier.padding(horizontal = 4.dp),
		verticalArrangement = Arrangement.spacedBy(8.dp),
	) {
		NotificationSupportText(stringResource(Res.string.notifications_permission_denied))
		SecondaryButton(stringResource(Res.string.button_open_settings), onClick = onOpenSettings)
	}
}

@Composable
private fun NotificationSupportText(text: String) {
	Text(text = text, color = LocalVniDropColors.current.foregroundLighter, style = MaterialTheme.typography.bodySmall)
}
