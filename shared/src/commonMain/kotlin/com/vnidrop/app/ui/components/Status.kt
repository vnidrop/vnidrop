package com.vnidrop.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.vnidrop.app.ui.theme.LocalVniDropColors

enum class PillTone { Neutral, Success, Warning, Destructive, Brand }

@Composable
fun StatusPill(label: String, modifier: Modifier = Modifier, tone: PillTone = PillTone.Neutral) {
	val colors = LocalVniDropColors.current
	val color = when (tone) {
		PillTone.Neutral -> colors.foregroundLighter
		PillTone.Success, PillTone.Brand -> colors.brandLink
		PillTone.Warning -> colors.warningDefault
		PillTone.Destructive -> colors.destructiveDefault
	}
	val shape = RoundedCornerShape(7.dp)
	Row(
		modifier = modifier.clip(shape).background(color.copy(alpha = 0.12f))
			.border(1.dp, color.copy(alpha = 0.32f), shape).padding(horizontal = 8.dp, vertical = 4.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		Box(Modifier.size(7.dp).clip(CircleShape).background(color))
		Text(label, modifier = Modifier.padding(start = 6.dp), color = color, style = MaterialTheme.typography.labelMedium, maxLines = 1)
	}
}
