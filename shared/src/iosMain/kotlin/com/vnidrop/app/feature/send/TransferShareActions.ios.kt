package com.vnidrop.app.feature.send

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.BetaInteropApi
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.writeToFile
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIModalPresentationFormSheet

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberTransferShareActions(): TransferShareActions = remember {
	object : TransferShareActions {
		override val canUseNativeShare = true
		// Core NFC tag writing requires the NFC entitlement. Keep the action
		// visible but disabled until that capability is provisioned for the app.
		override val nfcAvailability = NfcShareAvailability.Unavailable

		override fun exportInvitation(ticket: String, transferName: String, onResult: (Result<Unit>) -> Unit) {
			onResult(runCatching {
				val url = createInvitation(ticket, transferName)
				val picker = UIDocumentPickerViewController(forExportingURLs = listOf(url), asCopy = true)
				present(picker)
			})
		}

		override fun shareInvitation(ticket: String, transferName: String, onResult: (Result<Unit>) -> Unit) {
			onResult(runCatching {
				val url = createInvitation(ticket, transferName)
				val controller = UIActivityViewController(activityItems = listOf(url), applicationActivities = null)
				controller.modalPresentationStyle = UIModalPresentationFormSheet
				presenter().presentViewController(controller, animated = true, completion = null)
			})
		}

		override fun writeInvitationToNfc(ticket: String, onResult: (Result<Unit>) -> Unit) {
			onResult(Result.failure(UnsupportedOperationException("NFC tag writing is not enabled for this build")))
		}
		override fun cancelNfcWrite() = Unit
	}
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun createInvitation(ticket: String, transferName: String): NSURL {
	val path = NSTemporaryDirectory().trimEnd('/') + "/" + invitationFileName(transferName)
	val text = NSString.create(string = ticket)
	require(text.writeToFile(path, atomically = true, encoding = NSUTF8StringEncoding, error = null)) {
		"The invitation file could not be created"
	}
	return NSURL.fileURLWithPath(path)
}

private fun presenter() = UIApplication.sharedApplication.keyWindow?.rootViewController
	?: error("Could not find an iOS view controller")

private fun present(controller: platform.UIKit.UIViewController) {
	presenter().presentViewController(controller, animated = true, completion = null)
}
