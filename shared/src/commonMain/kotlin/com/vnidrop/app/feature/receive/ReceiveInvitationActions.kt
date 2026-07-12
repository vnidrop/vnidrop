package com.vnidrop.app.feature.receive

import androidx.compose.runtime.Composable

enum class ReceiveMethod { InvitationFile, QrCode, Nfc }

enum class ReceiveMethodAvailability { Available, Unavailable, Hidden }

interface ReceiveInvitationActions {
	val fileAvailability: ReceiveMethodAvailability
	val qrAvailability: ReceiveMethodAvailability
	val nfcAvailability: ReceiveMethodAvailability

	fun pickInvitation(onResult: (Result<String>) -> Unit)
	fun scanQrCode(onResult: (Result<String>) -> Unit)
	fun readNfcInvitation(onResult: (Result<String>) -> Unit)
	fun cancel()
}

@Composable
expect fun rememberReceiveInvitationActions(): ReceiveInvitationActions

internal const val InvitationMimeType = VniDropInvitationMimeType
internal const val MaxInvitationBytes = MaxVniDropInvitationBytes
