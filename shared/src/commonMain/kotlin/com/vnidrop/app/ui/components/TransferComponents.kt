package com.vnidrop.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vnidrop.app.ui.theme.LocalVniDropColors

@Composable
fun ProgressRow(
	label: String,
	progress: Float?,
	modifier: Modifier = Modifier,
	detail: String? = null,
) {
	Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
		Row(
			Modifier.fillMaxWidth(),
			horizontalArrangement = Arrangement.SpaceBetween,
			verticalAlignment = Alignment.CenterVertically,
		) {
			Text(
				label,
				style = MaterialTheme.typography.bodyMedium,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
				modifier = Modifier.weight(1f, fill = false),
			)
			if (progress != null) {
				Text(
					"${(progress * 100).toInt()}%",
					color = LocalVniDropColors.current.foregroundLighter,
					style = MaterialTheme.typography.labelSmall,
				)
			}
		}
		if (detail != null) {
			Text(
				detail,
				color = LocalVniDropColors.current.foregroundLighter,
				style = MaterialTheme.typography.bodySmall,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
			)
		}
		if (progress == null) LinearProgressIndicator(Modifier.fillMaxWidth())
		else LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
	}
}

@Composable
fun MetadataRow(label: String, value: String, modifier: Modifier = Modifier) {
	Row(
		modifier = modifier.fillMaxWidth(),
		horizontalArrangement = Arrangement.spacedBy(12.dp),
		verticalAlignment = Alignment.Top,
	) {
		Text(label, Modifier.weight(0.35f), color = LocalVniDropColors.current.foregroundLighter, style = MaterialTheme.typography.bodySmall)
		Text(value, Modifier.weight(0.65f), style = MaterialTheme.typography.bodySmall)
	}
}
