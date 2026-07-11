package com.vnidrop.app.feature.send

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vnidrop.app.core.PickedShareFile
import com.vnidrop.app.core.ShareAccessPolicy
import com.vnidrop.app.ui.components.Field
import com.vnidrop.app.ui.components.PrimaryButton
import com.vnidrop.app.ui.components.QuietButton
import com.vnidrop.app.ui.state.WindowClass
import com.vnidrop.app.ui.state.formatBytes
import com.vnidrop.app.ui.theme.LocalVniDropColors
import org.jetbrains.compose.resources.stringResource
import vnidrop.shared.generated.resources.Res
import vnidrop.shared.generated.resources.button_change_file
import vnidrop.shared.generated.resources.button_choose_file
import vnidrop.shared.generated.resources.button_clear
import vnidrop.shared.generated.resources.button_share_file
import vnidrop.shared.generated.resources.button_sharing_file
import vnidrop.shared.generated.resources.field_sender_name
import vnidrop.shared.generated.resources.field_transfer_name
import vnidrop.shared.generated.resources.send_access_anyone
import vnidrop.shared.generated.resources.send_access_anyone_description
import vnidrop.shared.generated.resources.send_access_approval
import vnidrop.shared.generated.resources.send_access_approval_description
import vnidrop.shared.generated.resources.send_access_title
import vnidrop.shared.generated.resources.send_choose_file_body
import vnidrop.shared.generated.resources.send_choose_file_title
import vnidrop.shared.generated.resources.send_file_size_unknown
import vnidrop.shared.generated.resources.send_review_title

@Composable
internal fun TransferComposer(
	coreInitialized: Boolean,
	state: SendState,
	windowClass: WindowClass,
	onSelectFile: () -> Unit,
	onClearFile: () -> Unit,
	onTransferNameChanged: (String) -> Unit,
	onSenderNameChanged: (String) -> Unit,
	onAccessPolicyChanged: (ShareAccessPolicy) -> Unit,
	onCreateShare: () -> Unit,
) {
	Column(
		modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
		verticalArrangement = Arrangement.spacedBy(16.dp),
	) {
		val file = state.selectedFile
		if (file == null) {
			ChooseFileStep(onSelectFile)
		} else {
			ReviewFileStep(
				file = file,
				state = state,
				windowClass = windowClass,
				onSelectFile = onSelectFile,
				onClearFile = onClearFile,
				onTransferNameChanged = onTransferNameChanged,
				onSenderNameChanged = onSenderNameChanged,
				onAccessPolicyChanged = onAccessPolicyChanged,
				onCreateShare = onCreateShare,
				coreInitialized = coreInitialized,
			)
		}
	}
}

@Composable
private fun ChooseFileStep(onSelectFile: () -> Unit) {
	Text(stringResource(Res.string.send_choose_file_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
	Text(
		stringResource(Res.string.send_choose_file_body),
		color = LocalVniDropColors.current.foregroundLighter,
		style = MaterialTheme.typography.bodyMedium,
	)
	Surface(shape = RoundedCornerShape(16.dp), color = LocalVniDropColors.current.backgroundSurface200) {
		Column(
			modifier = Modifier.fillMaxWidth().padding(24.dp),
			horizontalAlignment = Alignment.CenterHorizontally,
			verticalArrangement = Arrangement.spacedBy(14.dp),
		) {
			Icon(SendIcons.File, contentDescription = null, tint = LocalVniDropColors.current.brandLink, modifier = Modifier.size(32.dp))
			PrimaryButton(stringResource(Res.string.button_choose_file), onClick = onSelectFile)
		}
	}
}

@Composable
private fun ReviewFileStep(
	file: PickedShareFile,
	state: SendState,
	windowClass: WindowClass,
	onSelectFile: () -> Unit,
	onClearFile: () -> Unit,
	onTransferNameChanged: (String) -> Unit,
	onSenderNameChanged: (String) -> Unit,
	onAccessPolicyChanged: (ShareAccessPolicy) -> Unit,
	onCreateShare: () -> Unit,
	coreInitialized: Boolean,
) {
	Text(stringResource(Res.string.send_review_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
	SelectedFileCard(file)
	Field(state.transferName, onTransferNameChanged, stringResource(Res.string.field_transfer_name))
	Field(state.senderName, onSenderNameChanged, stringResource(Res.string.field_sender_name))
	Text(stringResource(Res.string.send_access_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
	PolicyOption(
		icon = SendIcons.Shield,
		title = stringResource(Res.string.send_access_approval),
		description = stringResource(Res.string.send_access_approval_description),
		selected = state.accessPolicy == ShareAccessPolicy.RequireApproval,
		onClick = { onAccessPolicyChanged(ShareAccessPolicy.RequireApproval) },
	)
	PolicyOption(
		icon = SendIcons.Globe,
		title = stringResource(Res.string.send_access_anyone),
		description = stringResource(Res.string.send_access_anyone_description),
		selected = state.accessPolicy == ShareAccessPolicy.AnyoneWithTransfer,
		onClick = { onAccessPolicyChanged(ShareAccessPolicy.AnyoneWithTransfer) },
	)
	if (windowClass == WindowClass.Phone) {
		Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
			ShareButton(state, coreInitialized, onCreateShare, Modifier.fillMaxWidth())
			QuietButton(stringResource(Res.string.button_change_file), onClick = onSelectFile, modifier = Modifier.fillMaxWidth(), enabled = !state.isSharing)
		}
	} else {
		Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
			ShareButton(state, coreInitialized, onCreateShare)
			QuietButton(stringResource(Res.string.button_change_file), onClick = onSelectFile, enabled = !state.isSharing)
			QuietButton(stringResource(Res.string.button_clear), onClick = onClearFile, enabled = !state.isSharing)
		}
	}
}

@Composable
private fun ShareButton(state: SendState, coreInitialized: Boolean, onCreateShare: () -> Unit, modifier: Modifier = Modifier) {
	PrimaryButton(
		if (state.isSharing) stringResource(Res.string.button_sharing_file) else stringResource(Res.string.button_share_file),
		onClick = onCreateShare,
		modifier = modifier,
		enabled = state.canCreateShare(coreInitialized),
	)
}

@Composable
private fun SelectedFileCard(file: PickedShareFile) {
	Surface(shape = RoundedCornerShape(14.dp), color = LocalVniDropColors.current.backgroundSurface200) {
		Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
			Box(Modifier.size(44.dp).background(LocalVniDropColors.current.backgroundSurface300, RoundedCornerShape(11.dp))) {
				FileArtwork(file.thumbnailBytes, Modifier.fillMaxSize())
			}
			Spacer(Modifier.width(12.dp))
			Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
				Text(file.displayName, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
				Text(
					file.sizeBytes?.let(::formatBytes) ?: stringResource(Res.string.send_file_size_unknown),
					color = LocalVniDropColors.current.foregroundLighter,
					style = MaterialTheme.typography.bodySmall,
				)
			}
		}
	}
}

@Composable
private fun PolicyOption(
	icon: ImageVector,
	title: String,
	description: String,
	selected: Boolean,
	onClick: () -> Unit,
) {
	val colors = LocalVniDropColors.current
	val shape = RoundedCornerShape(14.dp)
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.clip(shape)
			.background(if (selected) colors.backgroundSelection else colors.backgroundSurface200)
			.selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
			.padding(14.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		Icon(icon, contentDescription = null, tint = if (selected) colors.brandLink else colors.foregroundLight, modifier = Modifier.size(22.dp))
		Spacer(Modifier.width(12.dp))
		Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
			Text(title, fontWeight = FontWeight.SemiBold)
			Text(description, color = colors.foregroundLighter, style = MaterialTheme.typography.bodySmall)
		}
		RadioButton(selected = selected, onClick = null)
	}
}
