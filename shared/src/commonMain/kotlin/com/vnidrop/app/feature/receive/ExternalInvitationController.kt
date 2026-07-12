package com.vnidrop.app.feature.receive

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

const val VniDropInvitationMimeType = "application/vnd.vnidrop.transfer"
const val VniDropInvitationExtension = "vnd"
const val MaxVniDropInvitationBytes = 64 * 1024

/**
 * Buffered ingress for invitation documents opened by a platform host.
 *
 * Hosts can submit before Compose is attached during a cold launch. Each
 * document is then consumed exactly once by the app-level receive workflow.
 */
class ExternalInvitationController {
	// OS document-open dispatch can be triggered by another process. Keep the
	// cold-launch queue bounded so repeated intents cannot grow memory forever.
	private val pending = Channel<Result<String>>(capacity = 16)
	val invitations: Flow<Result<String>> = pending.receiveAsFlow()

	fun openInvitation(raw: String) {
		pending.trySend(validateInvitation(raw))
	}

	fun reportOpenFailure(message: String) {
		pending.trySend(Result.failure(IllegalArgumentException(message)))
	}
}

internal fun validateInvitation(raw: String): Result<String> = runCatching {
	require(raw.isNotBlank()) { "The invitation is empty" }
	require(raw.encodeToByteArray().size <= MaxVniDropInvitationBytes) { "The invitation is too large" }
	raw
}
