package com.vnidrop.app.feature.approvals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vnidrop.app.ui.components.PrimaryButton
import com.vnidrop.app.ui.components.SecondaryButton
import com.vnidrop.app.ui.theme.LocalVniDropColors
import org.jetbrains.compose.resources.stringResource
import vnidrop.shared.generated.resources.Res
import vnidrop.shared.generated.resources.approval_connection_request
import vnidrop.shared.generated.resources.approval_endpoint_id
import vnidrop.shared.generated.resources.approval_pending_count
import vnidrop.shared.generated.resources.button_approve
import vnidrop.shared.generated.resources.button_refuse

@Composable
fun ApprovalModalHost(
	state: ApprovalState,
	onAccept: (String) -> Unit,
	onRefuse: (String) -> Unit,
) {
	val request = state.current ?: return
	val busy = request.id in state.respondingIds
	val receiver = request.receiverName ?: request.receiverDeviceName ?: "A nearby device"
	val colors = LocalVniDropColors.current
	Dialog(
		onDismissRequest = {},
		properties = DialogProperties(
			dismissOnBackPress = false,
			dismissOnClickOutside = false,
			usePlatformDefaultWidth = false,
		),
	) {
		Surface(
			modifier = Modifier.padding(24.dp).widthIn(max = 440.dp).fillMaxWidth(),
			shape = RoundedCornerShape(24.dp),
			color = colors.backgroundDialog,
			shadowElevation = 16.dp,
		) {
			Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
				Surface(shape = RoundedCornerShape(14.dp), color = colors.backgroundSelection) {
					Icon(
						ApprovalIcon,
						contentDescription = null,
						tint = colors.brandLink,
						modifier = Modifier.padding(11.dp).size(24.dp),
					)
				}
				Text(
					stringResource(Res.string.approval_connection_request),
					style = MaterialTheme.typography.headlineSmall,
					fontWeight = FontWeight.Bold,
				)
				Text(
					"$receiver wants to receive ${request.transferName}.",
					style = MaterialTheme.typography.bodyLarge,
					color = colors.foregroundLight,
				)
				// Trusted identity is the endpoint id; display names are peer-provided.
				Text(
					stringResource(Res.string.approval_endpoint_id, request.remoteEndpointId),
					style = MaterialTheme.typography.bodySmall,
					color = colors.foregroundLighter,
				)
				if (state.pending.size > 1) {
					Text(
						stringResource(Res.string.approval_pending_count, state.pending.size),
						style = MaterialTheme.typography.bodySmall,
						color = colors.foregroundLighter,
					)
				}
				BoxWithConstraints(Modifier.fillMaxWidth()) {
					if (maxWidth < 330.dp) {
						Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
							PrimaryButton(stringResource(Res.string.button_approve), { onAccept(request.id) }, Modifier.fillMaxWidth(), !busy)
							SecondaryButton(stringResource(Res.string.button_refuse), { onRefuse(request.id) }, Modifier.fillMaxWidth(), !busy)
						}
					} else {
						Row(verticalAlignment = Alignment.CenterVertically) {
							if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
							Spacer(Modifier.weight(1f))
							SecondaryButton(stringResource(Res.string.button_refuse), { onRefuse(request.id) }, enabled = !busy)
							Spacer(Modifier.width(10.dp))
							PrimaryButton(stringResource(Res.string.button_approve), { onAccept(request.id) }, enabled = !busy)
						}
					}
				}
			}
		}
	}
}

private val ApprovalIcon: ImageVector = ImageVector.Builder(
	name = "Approval",
	defaultWidth = 24.dp,
	defaultHeight = 24.dp,
	viewportWidth = 24f,
	viewportHeight = 24f,
).apply {
	path {
		moveTo(12f, 2f); lineTo(20f, 5.5f); verticalLineTo(11f)
		curveTo(20f, 16.1f, 16.6f, 20.7f, 12f, 22f)
		curveTo(7.4f, 20.7f, 4f, 16.1f, 4f, 11f); verticalLineTo(5.5f); close()
		moveTo(8.2f, 11.8f); lineTo(10.7f, 14.3f); lineTo(15.9f, 9.1f)
	}
}.build()
