package com.vnidrop.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vnidrop.app.core.CoreUiState
import com.vnidrop.app.ui.components.PrimaryButton
import com.vnidrop.app.ui.navigation.VniDropIcons
import com.vnidrop.app.ui.state.SendUiState
import com.vnidrop.app.ui.state.WindowClass
import com.vnidrop.app.ui.state.isActiveTransfer
import com.vnidrop.app.ui.theme.LocalVniDropColors
import org.jetbrains.compose.resources.stringResource
import vnidrop.shared.generated.resources.Res
import vnidrop.shared.generated.resources.button_create_new_transfer
import vnidrop.shared.generated.resources.send_empty_body
import vnidrop.shared.generated.resources.send_empty_title

@Composable
internal fun SendEmptyState(windowClass: WindowClass) {
	val colors = LocalVniDropColors.current
	Column(
		modifier = Modifier
			.fillMaxWidth()
			.heightIn(min = if (windowClass == WindowClass.Phone) 540.dp else 420.dp)
			.padding(horizontal = 24.dp, vertical = 40.dp),
		horizontalAlignment = Alignment.CenterHorizontally,
		verticalArrangement = Arrangement.Center,
	) {
		Icon(
			imageVector = VniDropIcons.Send,
			contentDescription = null,
			modifier = Modifier.size(96.dp),
			tint = colors.brandDefault,
		)
		Text(
			text = stringResource(Res.string.send_empty_title),
			modifier = Modifier.padding(top = 24.dp),
			style = MaterialTheme.typography.headlineSmall,
			fontWeight = FontWeight.Bold,
			textAlign = TextAlign.Center,
		)
		Text(
			text = stringResource(Res.string.send_empty_body),
			modifier = Modifier
				.padding(top = 10.dp)
				.widthIn(max = 520.dp),
			color = colors.foregroundLighter,
			style = MaterialTheme.typography.bodyMedium,
			textAlign = TextAlign.Center,
		)
		PrimaryButton(
			text = stringResource(Res.string.button_create_new_transfer),
			onClick = {},
			modifier = Modifier.padding(top = 24.dp),
		)
	}
}

internal fun SendUiState.shouldShowEmptyState(coreState: CoreUiState): Boolean =
	!hasSelectedSource &&
		!isSharing &&
		coreState.lastShare == null &&
		coreState.transfers.none { it.direction == "send" && it.isActiveTransfer() }
