package com.vnidrop.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vnidrop.app.DeviceInfo
import com.vnidrop.app.VniDropAppEvent
import com.vnidrop.app.core.CoreUiState
import com.vnidrop.app.core.FolderAccessStatus
import com.vnidrop.app.ui.components.Field
import com.vnidrop.app.ui.components.PrimaryButton
import com.vnidrop.app.ui.components.SecondaryButton
import com.vnidrop.app.ui.state.PreferencesUiState
import com.vnidrop.app.ui.state.WindowClass
import com.vnidrop.app.ui.theme.LocalVniDropColors
import com.vnidrop.app.ui.theme.ThemeMode
import org.jetbrains.compose.resources.stringResource
import vnidrop.shared.generated.resources.Res
import vnidrop.shared.generated.resources.about_bug_report
import vnidrop.shared.generated.resources.about_privacy
import vnidrop.shared.generated.resources.about_title
import vnidrop.shared.generated.resources.appearance_auto_description
import vnidrop.shared.generated.resources.appearance_dark_mode
import vnidrop.shared.generated.resources.appearance_light_mode
import vnidrop.shared.generated.resources.appearance_mode_title
import vnidrop.shared.generated.resources.appearance_system_mode
import vnidrop.shared.generated.resources.appearance_title
import vnidrop.shared.generated.resources.battery_level_title
import vnidrop.shared.generated.resources.button_choose_folder
import vnidrop.shared.generated.resources.core_status_ready
import vnidrop.shared.generated.resources.device_model_title
import vnidrop.shared.generated.resources.device_name_title
import vnidrop.shared.generated.resources.folder_status_permission_required
import vnidrop.shared.generated.resources.folder_status_unavailable
import vnidrop.shared.generated.resources.folder_status_validating
import vnidrop.shared.generated.resources.folder_status_writable
import vnidrop.shared.generated.resources.preferences_receive_folder_title
import vnidrop.shared.generated.resources.preferences_title
import vnidrop.shared.generated.resources.button_reset_default
import vnidrop.shared.generated.resources.field_username
import vnidrop.shared.generated.resources.network_title
import vnidrop.shared.generated.resources.node_title
import vnidrop.shared.generated.resources.not_initialized
import vnidrop.shared.generated.resources.os_version_title
import vnidrop.shared.generated.resources.settings_title
import vnidrop.shared.generated.resources.value_unavailable
import vnidrop.shared.generated.resources.version_title

private enum class SettingsPane {
	Overview,
	Preferences,
	Appearance,
	About,
}

@Composable
fun SettingsScreen(
	deviceInfo: DeviceInfo,
	coreState: CoreUiState,
	themeMode: ThemeMode,
	preferencesState: PreferencesUiState,
	windowClass: WindowClass,
	onEvent: (VniDropAppEvent) -> Unit,
) {
	var pane by remember { mutableStateOf(SettingsPane.Overview) }

	when (windowClass) {
		WindowClass.Desktop -> DesktopSettings(
			selectedPane = pane,
			onPaneSelected = { pane = it },
			deviceInfo = deviceInfo,
			coreState = coreState,
			themeMode = themeMode,
			preferencesState = preferencesState,
			onEvent = onEvent,
		)
		else -> MobileSettings(
			pane = pane,
			onPaneSelected = { pane = it },
			deviceInfo = deviceInfo,
			coreState = coreState,
			themeMode = themeMode,
			preferencesState = preferencesState,
			onEvent = onEvent,
		)
	}
}

@Composable
private fun MobileSettings(
	pane: SettingsPane,
	onPaneSelected: (SettingsPane) -> Unit,
	deviceInfo: DeviceInfo,
	coreState: CoreUiState,
	themeMode: ThemeMode,
	preferencesState: PreferencesUiState,
	onEvent: (VniDropAppEvent) -> Unit,
) {
	Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
		ErrorSection(coreState)
		when (pane) {
			SettingsPane.Overview -> SettingsOverview(
				coreState = coreState,
				themeMode = themeMode,
				preferencesState = preferencesState,
				onOpenPreferences = { onPaneSelected(SettingsPane.Preferences) },
				onOpenAppearance = { onPaneSelected(SettingsPane.Appearance) },
				onOpenAbout = { onPaneSelected(SettingsPane.About) },
				largeTitle = true,
			)
			SettingsPane.Preferences -> PreferencesSettings(
				preferencesState = preferencesState,
				onEvent = onEvent,
				onBack = { onPaneSelected(SettingsPane.Overview) },
				showBack = true,
			)
			SettingsPane.Appearance -> AppearanceSettings(
				themeMode = themeMode,
				onThemeModeChange = { onEvent(VniDropAppEvent.ThemeModeChanged(it)) },
				onBack = { onPaneSelected(SettingsPane.Overview) },
				showBack = true,
			)
			SettingsPane.About -> AboutSettings(
				deviceInfo = deviceInfo,
				coreState = coreState,
				onBack = { onPaneSelected(SettingsPane.Overview) },
				showBack = true,
			)
		}
	}
}

@Composable
private fun DesktopSettings(
	selectedPane: SettingsPane,
	onPaneSelected: (SettingsPane) -> Unit,
	deviceInfo: DeviceInfo,
	coreState: CoreUiState,
	themeMode: ThemeMode,
	preferencesState: PreferencesUiState,
	onEvent: (VniDropAppEvent) -> Unit,
) {
	Row(
		modifier = Modifier.fillMaxWidth(),
		horizontalArrangement = Arrangement.spacedBy(24.dp),
		verticalAlignment = Alignment.Top,
	) {
		Column(
			modifier = Modifier.widthIn(min = 280.dp, max = 340.dp),
			verticalArrangement = Arrangement.spacedBy(18.dp),
		) {
			SettingsOverview(
				coreState = coreState,
				themeMode = themeMode,
				preferencesState = preferencesState,
				onOpenPreferences = { onPaneSelected(SettingsPane.Preferences) },
				onOpenAppearance = { onPaneSelected(SettingsPane.Appearance) },
				onOpenAbout = { onPaneSelected(SettingsPane.About) },
				largeTitle = false,
				selectedPane = selectedPane,
			)
		}
		Column(
			modifier = Modifier.weight(1f),
			verticalArrangement = Arrangement.spacedBy(18.dp),
		) {
			when (selectedPane) {
				SettingsPane.Overview,
				SettingsPane.Preferences -> PreferencesSettings(
					preferencesState = preferencesState,
					onEvent = onEvent,
					onBack = {},
					showBack = false,
				)
				SettingsPane.Appearance -> AppearanceSettings(
					themeMode = themeMode,
					onThemeModeChange = { onEvent(VniDropAppEvent.ThemeModeChanged(it)) },
					onBack = {},
					showBack = false,
				)
				SettingsPane.About -> AboutSettings(
					deviceInfo = deviceInfo,
					coreState = coreState,
					onBack = {},
					showBack = false,
				)
			}
		}
	}
}

@Composable
private fun SettingsOverview(
	coreState: CoreUiState,
	themeMode: ThemeMode,
	preferencesState: PreferencesUiState,
	onOpenPreferences: () -> Unit,
	onOpenAppearance: () -> Unit,
	onOpenAbout: () -> Unit,
	largeTitle: Boolean,
	selectedPane: SettingsPane = SettingsPane.Overview,
) {
	Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
		if (largeTitle) {
			SettingsLargeTitle(stringResource(Res.string.settings_title))
		} else {
			Text(stringResource(Res.string.settings_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
		}
		SettingsGroup {
			SettingsRow(
				icon = SettingsIcons.Device,
				title = stringResource(Res.string.preferences_title),
				value = preferencesState.username,
				selected = selectedPane == SettingsPane.Preferences,
				onClick = onOpenPreferences,
			)
			SettingsRow(
				icon = SettingsIcons.Sun,
				title = stringResource(Res.string.appearance_title),
				value = themeMode.displayName(),
				selected = selectedPane == SettingsPane.Appearance,
				onClick = onOpenAppearance,
			)
		}
		SettingsGroup {
			SettingsRow(
				icon = SettingsIcons.Node,
				title = stringResource(Res.string.node_title),
				value = if (coreState.isInitialized) stringResource(Res.string.core_status_ready) else stringResource(Res.string.not_initialized),
				iconTone = IconTone.Neutral,
			)
			SettingsRow(
				icon = SettingsIcons.Info,
				title = stringResource(Res.string.about_title),
				selected = selectedPane == SettingsPane.About,
				onClick = onOpenAbout,
			)
		}
	}
}

@Composable
private fun PreferencesSettings(
	preferencesState: PreferencesUiState,
	onEvent: (VniDropAppEvent) -> Unit,
	onBack: () -> Unit,
	showBack: Boolean,
) {
	Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
		SettingsTopBar(title = stringResource(Res.string.preferences_title), onBack = onBack, showBack = showBack)
		Field(
			value = preferencesState.username,
			onValueChange = { onEvent(VniDropAppEvent.UsernameChanged(it)) },
			label = stringResource(Res.string.field_username),
		)
		SettingsGroup {
			SettingsRow(
				icon = SettingsIcons.Folder,
				title = stringResource(Res.string.preferences_receive_folder_title),
				value = preferencesState.receiveFolder.displayName.ifBlank { preferencesState.receiveFolder.value },
				iconTone = IconTone.Neutral,
			)
			SettingsRow(
				icon = SettingsIcons.Check,
				title = preferencesState.folderAccessStatus.displayName(isValidating = preferencesState.isValidatingFolder),
				iconTone = IconTone.Neutral,
			)
		}
		Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
			PrimaryButton(
				text = stringResource(Res.string.button_choose_folder),
				onClick = { onEvent(VniDropAppEvent.ChooseReceiveFolderClicked) },
			)
			SecondaryButton(
				text = stringResource(Res.string.button_reset_default),
				onClick = { onEvent(VniDropAppEvent.ResetReceiveFolderClicked) },
			)
		}
	}
}

@Composable
private fun AppearanceSettings(
	themeMode: ThemeMode,
	onThemeModeChange: (ThemeMode) -> Unit,
	onBack: () -> Unit,
	showBack: Boolean,
) {
	Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
		SettingsTopBar(title = stringResource(Res.string.appearance_title), onBack = onBack, showBack = showBack)
		Text(
			text = stringResource(Res.string.appearance_mode_title),
			style = MaterialTheme.typography.titleLarge,
			fontWeight = FontWeight.SemiBold,
		)
		ThemeChoice(
			icon = SettingsIcons.Device,
			title = stringResource(Res.string.appearance_system_mode),
			description = stringResource(Res.string.appearance_auto_description),
			selected = themeMode == ThemeMode.System,
			onClick = { onThemeModeChange(ThemeMode.System) },
		)
		ThemeChoice(
			icon = SettingsIcons.Moon,
			title = stringResource(Res.string.appearance_dark_mode),
			selected = themeMode == ThemeMode.Dark,
			onClick = { onThemeModeChange(ThemeMode.Dark) },
		)
		ThemeChoice(
			icon = SettingsIcons.Sun,
			title = stringResource(Res.string.appearance_light_mode),
			selected = themeMode == ThemeMode.Light,
			onClick = { onThemeModeChange(ThemeMode.Light) },
		)
	}
}

@Composable
private fun AboutSettings(
	deviceInfo: DeviceInfo,
	coreState: CoreUiState,
	onBack: () -> Unit,
	showBack: Boolean,
) {
	val unavailable = stringResource(Res.string.value_unavailable)

	Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
		SettingsTopBar(title = stringResource(Res.string.about_title), onBack = onBack, showBack = showBack)
		SettingsGroup {
			SettingsRow(icon = SettingsIcons.Document, title = stringResource(Res.string.about_privacy), iconTone = IconTone.Neutral)
			SettingsRow(icon = SettingsIcons.Bug, title = stringResource(Res.string.about_bug_report), iconTone = IconTone.Neutral)
		}
		PlainInfoSection {
			PlainInfoItem(stringResource(Res.string.version_title), "0.1.0")
			PlainInfoItem(stringResource(Res.string.device_name_title), deviceInfo.deviceName.orUnavailable(unavailable))
			PlainInfoItem(stringResource(Res.string.device_model_title), deviceInfo.deviceModel.orUnavailable(unavailable))
			PlainInfoItem(stringResource(Res.string.os_version_title), deviceInfo.operatingSystem)
			PlainInfoItem(stringResource(Res.string.network_title), deviceInfo.network.orUnavailable(unavailable))
			PlainInfoItem(stringResource(Res.string.battery_level_title), deviceInfo.batteryLevel.orUnavailable(unavailable))
			PlainInfoItem(
				title = stringResource(Res.string.node_title),
				value = if (coreState.isInitialized) {
					stringResource(Res.string.core_status_ready)
				} else {
					stringResource(Res.string.not_initialized)
				},
			)
		}
	}
}

@Composable
private fun SettingsLargeTitle(title: String) {
	Text(
		text = title,
		style = MaterialTheme.typography.headlineLarge,
		fontWeight = FontWeight.Bold,
	)
}

@Composable
private fun SettingsTopBar(title: String, onBack: () -> Unit, showBack: Boolean) {
	Row(
		modifier = Modifier.fillMaxWidth(),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(12.dp),
	) {
		if (showBack) {
			Icon(
				imageVector = SettingsIcons.Back,
				contentDescription = null,
				tint = LocalVniDropColors.current.foregroundDefault,
				modifier = Modifier
					.size(30.dp)
					.clickable(onClick = onBack)
					.padding(3.dp),
			)
		}
		Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
	}
}

@Composable
private fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
	val colors = LocalVniDropColors.current
	Card(
		modifier = Modifier.fillMaxWidth(),
		shape = RoundedCornerShape(18.dp),
		colors = CardDefaults.cardColors(containerColor = colors.backgroundSurface200),
	) {
		Column(modifier = Modifier.padding(vertical = 4.dp), content = content)
	}
}

@Composable
private fun PlainInfoSection(content: @Composable ColumnScope.() -> Unit) {
	Column(
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = 4.dp, vertical = 8.dp),
		verticalArrangement = Arrangement.spacedBy(18.dp),
		content = content,
	)
}

@Composable
private fun PlainInfoItem(title: String, value: String) {
	val colors = LocalVniDropColors.current
	Column(
		modifier = Modifier.fillMaxWidth(),
		verticalArrangement = Arrangement.spacedBy(4.dp),
	) {
		Text(
			text = title,
			color = colors.foregroundLighter,
			style = MaterialTheme.typography.labelLarge,
			fontWeight = FontWeight.Medium,
		)
		Text(
			text = value,
			color = colors.foregroundDefault,
			style = MaterialTheme.typography.bodyLarge,
		)
	}
}

private fun String?.orUnavailable(fallback: String): String =
	this?.takeIf { it.isNotBlank() } ?: fallback

@Composable
private fun SettingsRow(
	icon: ImageVector,
	title: String,
	value: String? = null,
	selected: Boolean = false,
	iconTone: IconTone = IconTone.Brand,
	onClick: (() -> Unit)? = null,
) {
	val colors = LocalVniDropColors.current
	val iconColor = when (iconTone) {
		IconTone.Brand -> colors.brandLink
		IconTone.Neutral -> colors.foregroundLighter
	}
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.height(60.dp)
			.background(if (selected) colors.backgroundSurface300 else Color.Transparent)
			.then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
			.padding(horizontal = 16.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(22.dp))
		Spacer(Modifier.width(14.dp))
		Text(
			text = title,
			modifier = Modifier.weight(1f),
			style = MaterialTheme.typography.bodyLarge,
			fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
			maxLines = 1,
			overflow = TextOverflow.Ellipsis,
		)
		value?.let {
			Text(
				text = it,
				color = colors.foregroundLighter,
				style = MaterialTheme.typography.bodySmall,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
			)
			Spacer(Modifier.width(10.dp))
		}
		if (onClick != null) {
			Icon(SettingsIcons.ChevronRight, contentDescription = null, tint = colors.foregroundLighter, modifier = Modifier.size(20.dp))
		}
	}
}

@Composable
private fun ThemeChoice(
	icon: ImageVector,
	title: String,
	selected: Boolean,
	onClick: () -> Unit,
	description: String? = null,
) {
	val colors = LocalVniDropColors.current
	val shape = RoundedCornerShape(18.dp)
	Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.height(62.dp)
				.background(colors.backgroundSurface200, shape)
				.border(
					border = if (selected) BorderStroke(1.5.dp, colors.brandLink) else BorderStroke(1.dp, Color.Transparent),
					shape = shape,
				)
				.clickable(onClick = onClick)
				.padding(horizontal = 16.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			Icon(icon, contentDescription = null, tint = colors.foregroundLight, modifier = Modifier.size(22.dp))
			Spacer(Modifier.width(14.dp))
			Text(
				text = title,
				modifier = Modifier.weight(1f),
				style = MaterialTheme.typography.bodyLarge,
				fontWeight = FontWeight.Medium,
			)
			if (selected) {
				Icon(SettingsIcons.Check, contentDescription = null, tint = colors.brandLink, modifier = Modifier.size(24.dp))
			}
		}
		description?.let {
			Text(
				text = it,
				color = colors.foregroundLighter,
				style = MaterialTheme.typography.bodySmall,
			)
		}
	}
}

@Composable
private fun ThemeMode.displayName(): String =
	when (this) {
		ThemeMode.System -> stringResource(Res.string.appearance_system_mode)
		ThemeMode.Light -> stringResource(Res.string.appearance_light_mode)
		ThemeMode.Dark -> stringResource(Res.string.appearance_dark_mode)
	}

@Composable
private fun FolderAccessStatus.displayName(isValidating: Boolean): String =
	if (isValidating) {
		stringResource(Res.string.folder_status_validating)
	} else {
		when (this) {
			FolderAccessStatus.Writable -> stringResource(Res.string.folder_status_writable)
			FolderAccessStatus.PermissionRequired -> stringResource(Res.string.folder_status_permission_required)
			FolderAccessStatus.Unavailable -> stringResource(Res.string.folder_status_unavailable)
		}
	}

private enum class IconTone {
	Brand,
	Neutral,
}

private object SettingsIcons {
	val ChevronRight = lineIcon("ChevronRight") {
		moveTo(9f, 18f)
		lineTo(15f, 12f)
		lineTo(9f, 6f)
	}
	val Back = lineIcon("Back") {
		moveTo(19f, 12f)
		lineTo(5f, 12f)
		moveTo(12f, 19f)
		lineTo(5f, 12f)
		lineTo(12f, 5f)
	}
	val Check = lineIcon("Check") {
		moveTo(20f, 6f)
		lineTo(9f, 17f)
		lineTo(4f, 12f)
	}
	val Sun = lineIcon("Sun") {
		moveTo(12f, 4f)
		lineTo(12f, 2f)
		moveTo(12f, 22f)
		lineTo(12f, 20f)
		moveTo(4.93f, 4.93f)
		lineTo(6.34f, 6.34f)
		moveTo(17.66f, 17.66f)
		lineTo(19.07f, 19.07f)
		moveTo(2f, 12f)
		lineTo(4f, 12f)
		moveTo(20f, 12f)
		lineTo(22f, 12f)
		moveTo(4.93f, 19.07f)
		lineTo(6.34f, 17.66f)
		moveTo(17.66f, 6.34f)
		lineTo(19.07f, 4.93f)
		moveTo(16f, 12f)
		arcTo(4f, 4f, 0f, true, true, 8f, 12f)
		arcTo(4f, 4f, 0f, true, true, 16f, 12f)
	}
	val Moon = lineIcon("Moon") {
		moveTo(21f, 12.79f)
		arcTo(9f, 9f, 0f, true, true, 11.21f, 3f)
		arcTo(7f, 7f, 0f, false, false, 21f, 12.79f)
	}
	val Device = lineIcon("Device") {
		roundRect(7f, 2f, 10f, 20f, 2.5f)
		moveTo(11f, 18f)
		lineTo(13f, 18f)
	}
	val Folder = lineIcon("Folder") {
		moveTo(3f, 7f)
		lineTo(9f, 7f)
		lineTo(11f, 9f)
		lineTo(21f, 9f)
		lineTo(21f, 19f)
		lineTo(3f, 19f)
		close()
	}
	val Info = lineIcon("Info") {
		moveTo(12f, 16f)
		lineTo(12f, 12f)
		moveTo(12f, 8f)
		lineTo(12.01f, 8f)
		moveTo(21f, 12f)
		arcTo(9f, 9f, 0f, true, true, 3f, 12f)
		arcTo(9f, 9f, 0f, true, true, 21f, 12f)
	}
	val Bug = lineIcon("Bug") {
		moveTo(8f, 2f)
		lineTo(9.88f, 3.88f)
		moveTo(16f, 2f)
		lineTo(14.12f, 3.88f)
		roundRect(7f, 6f, 10f, 14f, 5f)
		moveTo(3f, 10f)
		lineTo(7f, 10f)
		moveTo(17f, 10f)
		lineTo(21f, 10f)
		moveTo(3f, 16f)
		lineTo(7f, 16f)
		moveTo(17f, 16f)
		lineTo(21f, 16f)
		moveTo(12f, 6f)
		lineTo(12f, 20f)
	}
	val Document = lineIcon("Document") {
		moveTo(14f, 2f)
		lineTo(6f, 2f)
		arcTo(2f, 2f, 0f, false, false, 4f, 4f)
		lineTo(4f, 20f)
		arcTo(2f, 2f, 0f, false, false, 6f, 22f)
		lineTo(18f, 22f)
		arcTo(2f, 2f, 0f, false, false, 20f, 20f)
		lineTo(20f, 8f)
		lineTo(14f, 2f)
		moveTo(14f, 2f)
		lineTo(14f, 8f)
		lineTo(20f, 8f)
		moveTo(8f, 13f)
		lineTo(16f, 13f)
		moveTo(8f, 17f)
		lineTo(16f, 17f)
	}
	val Node = lineIcon("Node") {
		roundRect(4f, 4f, 16f, 16f, 3f)
		moveTo(9f, 9f)
		lineTo(15f, 9f)
		moveTo(9f, 13f)
		lineTo(15f, 13f)
		moveTo(9f, 17f)
		lineTo(12f, 17f)
	}
}

private fun lineIcon(name: String, block: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit): ImageVector =
	ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
		path(
			fill = SolidColor(Color.Transparent),
			stroke = SolidColor(Color.Black),
			strokeLineWidth = 2f,
			strokeLineCap = StrokeCap.Round,
			strokeLineJoin = StrokeJoin.Round,
			pathFillType = PathFillType.NonZero,
			pathBuilder = block,
		)
	}.build()

private fun androidx.compose.ui.graphics.vector.PathBuilder.roundRect(x: Float, y: Float, width: Float, height: Float, radius: Float) {
	moveTo(x + radius, y)
	lineTo(x + width - radius, y)
	arcTo(radius, radius, 0f, false, true, x + width, y + radius)
	lineTo(x + width, y + height - radius)
	arcTo(radius, radius, 0f, false, true, x + width - radius, y + height)
	lineTo(x + radius, y + height)
	arcTo(radius, radius, 0f, false, true, x, y + height - radius)
	lineTo(x, y + radius)
	arcTo(radius, radius, 0f, false, true, x + radius, y)
}
