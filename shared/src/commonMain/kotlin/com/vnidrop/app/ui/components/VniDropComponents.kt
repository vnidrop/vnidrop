package com.vnidrop.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vnidrop.app.ui.theme.LocalVniDropColors

@Composable
fun AppCard(
	title: String,
	modifier: Modifier = Modifier,
	trailing: @Composable (() -> Unit)? = null,
	content: @Composable ColumnScope.() -> Unit,
) {
	val colors = LocalVniDropColors.current
	Card(
		modifier = modifier.fillMaxWidth(),
		shape = RoundedCornerShape(8.dp),
		colors = CardDefaults.cardColors(containerColor = colors.surface),
		border = BorderStroke(1.dp, colors.border),
	) {
		Column(
			modifier = Modifier.padding(16.dp),
			verticalArrangement = Arrangement.spacedBy(12.dp),
		) {
			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.SpaceBetween,
				verticalAlignment = Alignment.CenterVertically,
			) {
				Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
				trailing?.invoke()
			}
			HorizontalDivider(color = colors.border)
			content()
		}
	}
}

@Composable
fun Field(
	value: String,
	onValueChange: (String) -> Unit,
	label: String,
	modifier: Modifier = Modifier,
	minLines: Int = 1,
	enabled: Boolean = true,
) {
	OutlinedTextField(
		value = value,
		onValueChange = onValueChange,
		label = { Text(label) },
		modifier = modifier.fillMaxWidth(),
		minLines = minLines,
		enabled = enabled,
		shape = RoundedCornerShape(8.dp),
	)
}

@Composable
fun PrimaryButton(
	text: String,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
	enabled: Boolean = true,
) {
	val colors = LocalVniDropColors.current
	Button(
		onClick = onClick,
		enabled = enabled,
		modifier = modifier.heightIn(min = 44.dp),
		shape = RoundedCornerShape(8.dp),
		colors = ButtonDefaults.buttonColors(containerColor = colors.brand, contentColor = Color.White),
	) {
		Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
	}
}

@Composable
fun SecondaryButton(
	text: String,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
	enabled: Boolean = true,
) {
	OutlinedButton(
		onClick = onClick,
		enabled = enabled,
		modifier = modifier.heightIn(min = 44.dp),
		shape = RoundedCornerShape(8.dp),
	) {
		Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
	}
}

@Composable
fun QuietButton(
	text: String,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
	enabled: Boolean = true,
) {
	TextButton(onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = 40.dp)) {
		Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
	}
}

@Composable
fun StatusPill(
	label: String,
	modifier: Modifier = Modifier,
	tone: PillTone = PillTone.Neutral,
) {
	val colors = LocalVniDropColors.current
	val color = when (tone) {
		PillTone.Neutral -> colors.textMuted
		PillTone.Success -> colors.success
		PillTone.Warning -> colors.warning
		PillTone.Destructive -> colors.destructive
		PillTone.Brand -> colors.brand
	}
	Row(
		modifier = modifier
			.clip(RoundedCornerShape(999.dp))
			.background(color.copy(alpha = 0.12f))
			.border(1.dp, color.copy(alpha = 0.32f), RoundedCornerShape(999.dp))
			.padding(horizontal = 10.dp, vertical = 5.dp),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(6.dp),
	) {
		Box(
			modifier = Modifier
				.size(7.dp)
				.clip(CircleShape)
				.background(color),
		)
		Text(label, color = color, style = MaterialTheme.typography.labelMedium, maxLines = 1)
	}
}

enum class PillTone {
	Neutral,
	Success,
	Warning,
	Destructive,
	Brand,
}

@Composable
fun ErrorBanner(message: String, modifier: Modifier = Modifier) {
	val colors = LocalVniDropColors.current
	Card(
		modifier = modifier.fillMaxWidth(),
		shape = RoundedCornerShape(8.dp),
		colors = CardDefaults.cardColors(containerColor = colors.destructive.copy(alpha = 0.14f)),
		border = BorderStroke(1.dp, colors.destructive.copy(alpha = 0.28f)),
	) {
		Text(
			text = message,
			modifier = Modifier.padding(14.dp),
			color = MaterialTheme.colorScheme.onSurface,
			style = MaterialTheme.typography.bodyMedium,
		)
	}
}

@Composable
fun ProgressRow(
	label: String,
	progress: Float?,
	modifier: Modifier = Modifier,
) {
	Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
		Text(label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
		if (progress == null) {
			LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
		} else {
			LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
		}
	}
}

@Composable
fun MetadataRow(label: String, value: String, modifier: Modifier = Modifier) {
	Row(
		modifier = modifier.fillMaxWidth(),
		horizontalArrangement = Arrangement.spacedBy(12.dp),
		verticalAlignment = Alignment.Top,
	) {
		Text(
			text = label,
			modifier = Modifier.weight(0.35f),
			color = LocalVniDropColors.current.textMuted,
			style = MaterialTheme.typography.bodySmall,
		)
		Text(
			text = value,
			modifier = Modifier.weight(0.65f),
			style = MaterialTheme.typography.bodySmall,
		)
	}
}
