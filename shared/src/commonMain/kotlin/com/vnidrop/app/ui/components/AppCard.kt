package com.vnidrop.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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
		colors = CardDefaults.cardColors(containerColor = colors.backgroundSurface75),
		border = BorderStroke(1.dp, colors.borderDefault),
	) {
		Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
			Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
				Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
				trailing?.invoke()
			}
			HorizontalDivider(color = colors.borderDefault)
			content()
		}
	}
}
