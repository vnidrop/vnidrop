package com.vnidrop.app.feature.send

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.awt.EventQueue
import java.awt.FileDialog
import java.awt.Frame
import java.awt.KeyboardFocusManager
import java.io.File

@Composable
actual fun rememberTransferShareActions(): TransferShareActions = remember {
	object : TransferShareActions {
		override val canUseNativeShare = DesktopShareBridge.shareFile != null
		override val nfcAvailability = NfcShareAvailability.Hidden

		override fun exportInvitation(ticket: String, transferName: String, onResult: (Result<Unit>) -> Unit) {
			EventQueue.invokeLater {
				onResult(runCatching {
					val dialog = FileDialog(activeFrame(), "Save VniDrop invitation", FileDialog.SAVE).apply {
						file = invitationFileName(transferName)
					}
					try {
						dialog.isVisible = true
						val directory = dialog.directory
						val name = dialog.file
						if (directory != null && name != null) File(directory, name).writeText(ticket)
					} finally { dialog.dispose() }
				})
			}
		}

		override fun shareInvitation(ticket: String, transferName: String, onResult: (Result<Unit>) -> Unit) {
			EventQueue.invokeLater {
				val share = DesktopShareBridge.shareFile
				if (share == null) {
					onResult(Result.failure(UnsupportedOperationException("System sharing is unavailable on this desktop")))
					return@invokeLater
				}
				onResult(runCatching {
					val directory = File(System.getProperty("java.io.tmpdir"), "vnidrop-share").apply { mkdirs() }
					val file = File(directory, invitationFileName(transferName)).apply { writeText(ticket) }
					share(file).getOrThrow()
				})
			}
		}

		override fun writeInvitationToNfc(ticket: String, onResult: (Result<Unit>) -> Unit) {
			onResult(Result.failure(UnsupportedOperationException("NFC is unavailable on desktop")))
		}
		override fun cancelNfcWrite() = Unit
	}
}

private fun activeFrame(): Frame? =
	(KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow as? Frame)
		?: Frame.getFrames().firstOrNull { it.isActive || it.isFocused }

object DesktopShareBridge {
	@Volatile
	var shareFile: ((File) -> Result<Unit>)? = null
}
