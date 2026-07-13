package com.vnidrop.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vnidrop.app.core.CoreState
import com.vnidrop.app.core.TicketInspectionModel
import com.vnidrop.app.ui.components.AppCard
import com.vnidrop.app.ui.components.MetadataRow
import com.vnidrop.app.ui.components.ProgressRow
import com.vnidrop.app.ui.state.formatBytes
import com.vnidrop.app.ui.state.summarizeProgress
import com.vnidrop.app.ui.theme.LocalVniDropColors
import org.jetbrains.compose.resources.stringResource
import vnidrop.shared.generated.resources.Res
import vnidrop.shared.generated.resources.metadata_files
import vnidrop.shared.generated.resources.metadata_hash
import vnidrop.shared.generated.resources.metadata_kind
import vnidrop.shared.generated.resources.metadata_sender
import vnidrop.shared.generated.resources.metadata_size
import vnidrop.shared.generated.resources.metadata_transfer
import vnidrop.shared.generated.resources.progress_title
import vnidrop.shared.generated.resources.ticket_details_title
import vnidrop.shared.generated.resources.unknown_sender

@Composable
fun ScreenHeader(title: String, subtitle: String) {
	Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
		Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
		Text(subtitle, color = LocalVniDropColors.current.foregroundLighter, style = MaterialTheme.typography.bodyMedium)
	}
}

@Composable
fun EmptyText(text: String) {
	Text(text, color = LocalVniDropColors.current.foregroundLighter, style = MaterialTheme.typography.bodyMedium)
}

@Composable
fun ProgressSection(coreState: CoreState) {
	val progress = summarizeProgress(coreState.events)
	if (progress.isNotEmpty()) {
		AppCard(title = stringResource(Res.string.progress_title)) {
			progress.forEach { item -> ProgressRow(label = item.label, progress = item.progress) }
		}
	}
}

@Composable
fun TicketInspectionCard(inspection: TicketInspectionModel) {
	val metadata = inspection.metadata
	AppCard(title = stringResource(Res.string.ticket_details_title)) {
		MetadataRow(stringResource(Res.string.metadata_kind), inspection.kind)
		MetadataRow(stringResource(Res.string.metadata_transfer), metadata.transferName)
		MetadataRow(stringResource(Res.string.metadata_sender), metadata.senderName ?: stringResource(Res.string.unknown_sender))
		MetadataRow(stringResource(Res.string.metadata_files), metadata.fileCount.toString())
		MetadataRow(stringResource(Res.string.metadata_size), formatBytes(metadata.totalSize))
		MetadataRow(stringResource(Res.string.metadata_hash), metadata.contentHash)
	}
}

@Composable
fun TicketText(ticket: String) {
	SelectionContainer {
		Text(
			text = ticket,
			modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
				.background(LocalVniDropColors.current.backgroundSurface200).padding(12.dp),
			style = MaterialTheme.typography.bodySmall,
		)
	}
}
