package com.vnidrop.app.ui.feedback

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vnidrop.app.ui.theme.LocalVniDropColors
import org.jetbrains.compose.resources.getString

@Composable
fun VniDropSnackbarHost(
	controller: UiMessageController,
	modifier: Modifier = Modifier,
) {
	val hostState = remember { SnackbarHostState() }
	var tone by remember { mutableStateOf(UiMessageTone.Info) }
	LaunchedEffect(controller) {
		controller.messages.collect { message ->
			tone = message.tone
			val result = hostState.showSnackbar(
				message = message.text.resolve(),
				actionLabel = message.actionLabel?.resolve(),
				withDismissAction = message.actionLabel == null,
				duration = if (message.tone == UiMessageTone.Error) SnackbarDuration.Long else SnackbarDuration.Short,
			)
			if (result == SnackbarResult.ActionPerformed) message.onAction?.invoke()
		}
	}
	LaunchedEffect(controller, hostState) {
		controller.dismissals.collect {
			hostState.currentSnackbarData?.dismiss()
		}
	}

	SnackbarHost(hostState = hostState, modifier = modifier) { data ->
		val colors = LocalVniDropColors.current
		val accent = when (tone) {
			UiMessageTone.Info -> colors.brandLink
			UiMessageTone.Success -> colors.brandDefault
			UiMessageTone.Warning -> colors.warningDefault
			UiMessageTone.Error -> colors.destructiveDefault
		}
		Snackbar(
			snackbarData = data,
			modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
			shape = RoundedCornerShape(10.dp),
			containerColor = colors.backgroundDialog,
			contentColor = colors.foregroundDefault,
			actionColor = accent,
			dismissActionContentColor = colors.foregroundLighter,
		)
	}
}

private suspend fun UiText.resolve(): String = when (this) {
	is UiText.Dynamic -> value
	is UiText.Resource -> getString(resource)
}
