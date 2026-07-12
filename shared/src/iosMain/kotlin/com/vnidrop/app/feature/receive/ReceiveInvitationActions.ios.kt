package com.vnidrop.app.feature.receive

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readBytes
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIModalPresentationFormSheet
import platform.UniformTypeIdentifiers.UTTypeData
import platform.darwin.NSObject

private var retainedInvitationDelegate: InvitationDocumentDelegate? = null

@Composable
actual fun rememberReceiveInvitationActions(): ReceiveInvitationActions = remember {
	object : ReceiveInvitationActions {
		override val fileAvailability = ReceiveMethodAvailability.Available
		override val qrAvailability = ReceiveMethodAvailability.Unavailable
		override val nfcAvailability = ReceiveMethodAvailability.Unavailable

		@OptIn(ExperimentalForeignApi::class)
		override fun pickInvitation(onResult: (Result<String>) -> Unit) {
			val presenter = UIApplication.sharedApplication.keyWindow?.rootViewController
				?: return onResult(Result.failure(IllegalStateException("Could not find an iOS view controller")))
			val picker = UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeData), asCopy = true)
			val delegate = InvitationDocumentDelegate(onResult)
			retainedInvitationDelegate = delegate
			picker.delegate = delegate
			picker.modalPresentationStyle = UIModalPresentationFormSheet
			presenter.presentViewController(picker, animated = true, completion = null)
		}

		override fun scanQrCode(onResult: (Result<String>) -> Unit) =
			onResult(Result.failure(UnsupportedOperationException("QR scanning is not enabled for this iOS build")))

		override fun readNfcInvitation(onResult: (Result<String>) -> Unit) =
			onResult(Result.failure(UnsupportedOperationException("NFC reading is not enabled for this iOS build")))

		override fun cancel() = Unit
	}
}

private class InvitationDocumentDelegate(
	private val onResult: (Result<String>) -> Unit,
) : NSObject(), UIDocumentPickerDelegateProtocol {
	@OptIn(ExperimentalForeignApi::class)
	override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
		val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL
		onResult(runCatching {
			requireNotNull(url) { "The selected invitation URL was invalid" }
			val path = url.path ?: error("The invitation path was invalid")
			val data = NSFileManager.defaultManager.contentsAtPath(path) ?: error("The invitation could not be opened")
			val length = data.length.toInt()
			require(length <= MaxInvitationBytes) { "The invitation is too large" }
			val bytes = data.bytes?.readBytes(length) ?: error("The invitation is empty")
			decodeInvitationBytes(bytes)
		})
		retainedInvitationDelegate = null
	}

	override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
		retainedInvitationDelegate = null
	}
}
