package com.vnidrop.app.ui.components

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vnidrop.app.ui.theme.LocalVniDropColors

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
	Button(
		onClick = onClick,
		enabled = enabled,
		modifier = modifier.heightIn(min = 44.dp),
		shape = RoundedCornerShape(8.dp),
		colors = ButtonDefaults.buttonColors(containerColor = LocalVniDropColors.current.brandButton, contentColor = Color.White),
	) {
		Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
	}
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
	OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = 44.dp), shape = RoundedCornerShape(8.dp)) {
		Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
	}
}

@Composable
fun QuietButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
	TextButton(onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = 40.dp)) {
		Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
	}
}
