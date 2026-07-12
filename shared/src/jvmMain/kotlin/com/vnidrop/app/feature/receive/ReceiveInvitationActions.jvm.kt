package com.vnidrop.app.feature.receive

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.awt.EventQueue
import java.awt.FileDialog
import java.awt.Frame
import java.awt.KeyboardFocusManager
import java.io.File

@Composable
actual fun rememberReceiveInvitationActions(): ReceiveInvitationActions = remember {
	object : ReceiveInvitationActions {
		override val fileAvailability = ReceiveMethodAvailability.Available
		override val qrAvailability = ReceiveMethodAvailability.Hidden
		override val nfcAvailability = ReceiveMethodAvailability.Hidden

		override fun pickInvitation(onResult: (Result<String>) -> Unit) {
			EventQueue.invokeLater {
				val dialog = FileDialog(activeFrame(), "Open VniDrop invitation", FileDialog.LOAD).apply {
					setFilenameFilter { _, name -> name.endsWith(".vnd", ignoreCase = true) }
				}
				try {
					dialog.isVisible = true
					val directory = dialog.directory
					val name = dialog.file
					if (directory != null && name != null) onResult(readInvitation(File(directory, name)))
				} finally { dialog.dispose() }
			}
		}

		override fun scanQrCode(onResult: (Result<String>) -> Unit) =
			onResult(Result.failure(UnsupportedOperationException("QR scanning is unavailable on desktop")))

		override fun readNfcInvitation(onResult: (Result<String>) -> Unit) =
			onResult(Result.failure(UnsupportedOperationException("NFC is unavailable on desktop")))

		override fun cancel() = Unit
	}
}

private fun readInvitation(file: File): Result<String> = runCatching {
	require(file.length() <= MaxInvitationBytes) { "The invitation is too large" }
	file.readText()
}

private fun activeFrame(): Frame? =
	(KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow as? Frame)
		?: Frame.getFrames().firstOrNull { it.isActive || it.isFocused }
