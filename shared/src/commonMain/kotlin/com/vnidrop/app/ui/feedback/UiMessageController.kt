package com.vnidrop.app.ui.feedback

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.receiveAsFlow
import org.jetbrains.compose.resources.StringResource

sealed interface UiText {
	data class Resource(val resource: StringResource) : UiText
	data class Dynamic(val value: String) : UiText
}

enum class UiMessageTone {
	Info,
	Success,
	Warning,
	Error,
}

data class UiMessage(
	val text: UiText,
	val tone: UiMessageTone = UiMessageTone.Info,
	val actionLabel: UiText? = null,
	val onAction: (() -> Unit)? = null,
)

class UiMessageController {
	private val channel = Channel<UiMessage>(Channel.BUFFERED)
	val messages: Flow<UiMessage> = channel.receiveAsFlow()
	private val _dismissals = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
	val dismissals: SharedFlow<Unit> = _dismissals.asSharedFlow()

	suspend fun show(message: UiMessage) {
		channel.send(message)
	}

	fun tryShow(message: UiMessage): Boolean = channel.trySend(message).isSuccess

	fun dismissCurrent() {
		_dismissals.tryEmit(Unit)
	}

	fun error(error: Throwable, fallback: String = "Something went wrong.") {
		tryShow(
			UiMessage(
				text = UiText.Dynamic(error.message?.takeIf(String::isNotBlank) ?: fallback),
				tone = UiMessageTone.Error,
			),
		)
	}
}
