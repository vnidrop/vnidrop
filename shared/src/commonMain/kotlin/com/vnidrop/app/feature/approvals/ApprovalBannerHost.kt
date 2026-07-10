package com.vnidrop.app.feature.approvals

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vnidrop.app.ui.components.PrimaryButton
import com.vnidrop.app.ui.components.SecondaryButton
import com.vnidrop.app.ui.theme.LocalVniDropColors
import org.jetbrains.compose.resources.stringResource
import vnidrop.shared.generated.resources.Res
import vnidrop.shared.generated.resources.approval_connection_request
import vnidrop.shared.generated.resources.approval_pending_count
import vnidrop.shared.generated.resources.button_approve
import vnidrop.shared.generated.resources.button_refuse

@Composable
fun ApprovalBannerHost(
	state: ApprovalState,
	onAccept: (String) -> Unit,
	onRefuse: (String) -> Unit,
	modifier: Modifier = Modifier,
) {
	val request = state.current ?: return
	val busy = request.id in state.respondingIds
	val receiver = request.receiverName ?: request.receiverDeviceName ?: "A nearby device"
	val colors = LocalVniDropColors.current
	Card(
		modifier = modifier.widthIn(max = 640.dp).fillMaxWidth().padding(12.dp),
		shape = RoundedCornerShape(14.dp),
		colors = CardDefaults.cardColors(containerColor = colors.backgroundDialog),
		border = BorderStroke(1.dp, colors.warningDefault),
	) {
		Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
			Text(stringResource(Res.string.approval_connection_request), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
			Text("$receiver wants to receive ${request.transferName}.")
			if (state.pending.size > 1) {
				Text(stringResource(Res.string.approval_pending_count, state.pending.size), style = MaterialTheme.typography.bodySmall)
			}
			Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
				SecondaryButton(stringResource(Res.string.button_refuse), onClick = { onRefuse(request.id) }, enabled = !busy)
				PrimaryButton(stringResource(Res.string.button_approve), onClick = { onAccept(request.id) }, enabled = !busy)
			}
		}
	}
}
