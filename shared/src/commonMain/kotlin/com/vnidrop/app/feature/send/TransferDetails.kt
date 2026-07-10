package com.vnidrop.app.feature.send

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vnidrop.app.core.ShareAccessPolicy
import com.vnidrop.app.core.Transfer
import com.vnidrop.app.ui.components.AppCard
import com.vnidrop.app.ui.components.PrimaryButton
import com.vnidrop.app.ui.screens.TicketText
import com.vnidrop.app.ui.state.displayNameForStatus
import com.vnidrop.app.ui.state.formatBytes
import com.vnidrop.app.ui.theme.LocalVniDropColors
import org.jetbrains.compose.resources.stringResource
import vnidrop.shared.generated.resources.Res
import vnidrop.shared.generated.resources.button_back
import vnidrop.shared.generated.resources.button_copy_ticket
import vnidrop.shared.generated.resources.metadata_files
import vnidrop.shared.generated.resources.metadata_size
import vnidrop.shared.generated.resources.metadata_status
import vnidrop.shared.generated.resources.send_access_anyone
import vnidrop.shared.generated.resources.send_access_approval
import vnidrop.shared.generated.resources.send_access_title
import vnidrop.shared.generated.resources.send_new_transfer_title
import vnidrop.shared.generated.resources.send_transfer_details_title

@Composable
internal fun TransferDetails(transfer: Transfer, onBack: () -> Unit, onCopyTicket: (String) -> Unit) {
	LazyColumn(
		modifier = Modifier.fillMaxSize().statusBarsPadding(),
		contentPadding = PaddingValues(16.dp),
		verticalArrangement = Arrangement.spacedBy(16.dp),
	) {
		item {
			Row(verticalAlignment = Alignment.CenterVertically) {
				IconButton(onClick = onBack) {
					Icon(SendIcons.Back, contentDescription = stringResource(Res.string.button_back))
				}
				Text(
					stringResource(Res.string.send_transfer_details_title),
					style = MaterialTheme.typography.headlineSmall,
					fontWeight = FontWeight.Bold,
				)
			}
		}
		item {
			AppCard(title = transfer.transferName ?: stringResource(Res.string.send_new_transfer_title)) {
				DetailValue(stringResource(Res.string.metadata_status), displayNameForStatus(transfer.status))
				HorizontalDivider(color = LocalVniDropColors.current.borderDefault)
				DetailValue(stringResource(Res.string.metadata_size), formatBytes(transfer.totalSize))
				HorizontalDivider(color = LocalVniDropColors.current.borderDefault)
				DetailValue(stringResource(Res.string.metadata_files), transfer.fileCount.toString())
				HorizontalDivider(color = LocalVniDropColors.current.borderDefault)
				DetailValue(stringResource(Res.string.send_access_title), accessPolicyLabel(transfer.accessPolicy))
			}
		}
		transfer.ticket?.let { ticket ->
			item {
				AppCard(title = stringResource(Res.string.button_copy_ticket)) {
					TicketText(ticket)
					PrimaryButton(stringResource(Res.string.button_copy_ticket), onClick = { onCopyTicket(ticket) })
				}
			}
		}
	}
}

@Composable
private fun DetailValue(label: String, value: String) {
	Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
		Text(label, color = LocalVniDropColors.current.foregroundLighter, style = MaterialTheme.typography.bodySmall)
		Text(value, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
	}
}

@Composable
internal fun accessPolicyLabel(policy: ShareAccessPolicy): String = when (policy) {
	ShareAccessPolicy.RequireApproval -> stringResource(Res.string.send_access_approval)
	ShareAccessPolicy.AnyoneWithTransfer -> stringResource(Res.string.send_access_anyone)
}
