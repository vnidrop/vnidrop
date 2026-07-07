package com.vnidrop.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vnidrop.app.core.CoreUiState
import com.vnidrop.app.ui.components.AppCard
import com.vnidrop.app.ui.components.ErrorBanner
import com.vnidrop.app.ui.components.MetadataRow
import com.vnidrop.app.ui.components.PillTone
import com.vnidrop.app.ui.components.PrimaryButton
import com.vnidrop.app.ui.components.ProgressRow
import com.vnidrop.app.ui.components.SecondaryButton
import com.vnidrop.app.ui.components.StatusPill
import com.vnidrop.app.ui.state.displayNameForStatus
import com.vnidrop.app.ui.state.formatBytes
import com.vnidrop.app.ui.state.friendlyCoreError
import com.vnidrop.app.ui.state.summarizeProgress
import com.vnidrop.app.ui.theme.LocalVniDropColors
import org.jetbrains.compose.resources.stringResource
import uniffi.vnidrop.CoreEvent
import uniffi.vnidrop.ReceiverRequest
import uniffi.vnidrop.TicketInspection
import vnidrop.shared.generated.resources.Res
import vnidrop.shared.generated.resources.button_approve
import vnidrop.shared.generated.resources.button_refuse
import vnidrop.shared.generated.resources.event_log_title
import vnidrop.shared.generated.resources.metadata_files
import vnidrop.shared.generated.resources.metadata_hash
import vnidrop.shared.generated.resources.metadata_kind
import vnidrop.shared.generated.resources.metadata_sender
import vnidrop.shared.generated.resources.metadata_size
import vnidrop.shared.generated.resources.metadata_transfer
import vnidrop.shared.generated.resources.no_events
import vnidrop.shared.generated.resources.progress_title
import vnidrop.shared.generated.resources.ticket_details_title
import vnidrop.shared.generated.resources.ticket_no_metadata
import vnidrop.shared.generated.resources.unknown_sender

@Composable
fun ScreenHeader(title: String, subtitle: String) {
	Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
		Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
		Text(subtitle, color = LocalVniDropColors.current.foregroundLighter, style = MaterialTheme.typography.bodyMedium)
	}
}

@Composable
fun ErrorSection(coreState: CoreUiState) {
	friendlyCoreError(coreState.error)?.let { ErrorBanner(it) }
}

@Composable
fun EmptyText(text: String) {
	Text(text, color = LocalVniDropColors.current.foregroundLighter, style = MaterialTheme.typography.bodyMedium)
}

@Composable
fun ProgressSection(coreState: CoreUiState) {
	val progress = summarizeProgress(coreState.events)
	if (progress.isNotEmpty()) {
		AppCard(title = stringResource(Res.string.progress_title)) {
			progress.forEach { item ->
				ProgressRow(label = item.label, progress = item.progress)
			}
		}
	}
}

@Composable
fun TicketInspectionCard(inspection: TicketInspection) {
	AppCard(title = stringResource(Res.string.ticket_details_title)) {
		MetadataRow(stringResource(Res.string.metadata_kind), inspection.kind)
		inspection.metadata?.let { metadata ->
			MetadataRow(stringResource(Res.string.metadata_transfer), metadata.transferName)
			MetadataRow(stringResource(Res.string.metadata_sender), metadata.senderName ?: stringResource(Res.string.unknown_sender))
			MetadataRow(stringResource(Res.string.metadata_files), metadata.fileCount.toString())
			MetadataRow(stringResource(Res.string.metadata_size), formatBytes(metadata.totalSize))
			MetadataRow(stringResource(Res.string.metadata_hash), metadata.contentHash)
		} ?: EmptyText(stringResource(Res.string.ticket_no_metadata))
	}
}

@Composable
fun ReceiverRequestList(
	requests: List<ReceiverRequest>,
	onRespondRequest: (String, Boolean) -> Unit,
) {
	Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
		requests.forEach { request ->
			Column(
				modifier = Modifier
					.fillMaxWidth()
					.clip(RoundedCornerShape(8.dp))
					.background(LocalVniDropColors.current.backgroundSurface100)
					.padding(12.dp),
				verticalArrangement = Arrangement.spacedBy(8.dp),
			) {
				Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
					Column(modifier = Modifier.weight(1f)) {
						Text(request.receiverName ?: "Receiver", fontWeight = FontWeight.SemiBold)
						Text(request.remoteEndpointId.take(28), color = LocalVniDropColors.current.foregroundLighter, style = MaterialTheme.typography.bodySmall)
					}
					StatusPill(displayNameForStatus(request.status), tone = if (request.status == "requested") PillTone.Warning else PillTone.Neutral)
				}
				request.reason?.let { Text(it, color = LocalVniDropColors.current.foregroundLighter, style = MaterialTheme.typography.bodySmall) }
				if (request.status == "requested") {
					Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
						SecondaryButton(stringResource(Res.string.button_refuse), onClick = { onRespondRequest(request.id, false) })
						PrimaryButton(stringResource(Res.string.button_approve), onClick = { onRespondRequest(request.id, true) })
					}
				}
			}
		}
	}
}

@Composable
fun TicketText(ticket: String) {
	SelectionContainer {
		Text(
			text = ticket,
			modifier = Modifier
				.fillMaxWidth()
				.clip(RoundedCornerShape(8.dp))
				.background(LocalVniDropColors.current.backgroundSurface200)
				.padding(12.dp),
			style = MaterialTheme.typography.bodySmall,
		)
	}
}

@Composable
fun DiagnosticsPanel(events: List<CoreEvent>) {
	AppCard(title = stringResource(Res.string.event_log_title)) {
		if (events.isEmpty()) {
			EmptyText(stringResource(Res.string.no_events))
		} else {
			Column(
				modifier = Modifier
					.fillMaxWidth()
					.height(280.dp)
					.verticalScroll(rememberScrollState()),
				verticalArrangement = Arrangement.spacedBy(8.dp),
			) {
				events.forEach { event -> EventRow(event) }
			}
		}
	}
}

@Composable
private fun EventRow(event: CoreEvent) {
	Column(
		modifier = Modifier
			.fillMaxWidth()
			.clip(RoundedCornerShape(8.dp))
			.background(LocalVniDropColors.current.backgroundSurface100)
			.padding(10.dp),
		verticalArrangement = Arrangement.spacedBy(4.dp),
	) {
		Text("${event.scope}/${event.direction ?: "-"} ${event.phase}:${event.kind}", style = MaterialTheme.typography.bodySmall)
		Text(event.dataJson, color = LocalVniDropColors.current.foregroundLighter, style = MaterialTheme.typography.bodySmall)
	}
}

@Composable
fun SectionDivider() {
	HorizontalDivider(color = LocalVniDropColors.current.borderDefault)
}
