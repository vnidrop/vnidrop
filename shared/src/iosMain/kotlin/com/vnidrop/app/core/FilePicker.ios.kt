package com.vnidrop.app.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIModalPresentationFormSheet
import platform.UniformTypeIdentifiers.UTTypeFolder
import platform.UniformTypeIdentifiers.UTTypeItem
import platform.darwin.NSObject

private var retainedPickerDelegate: DocumentPickerDelegate? = null

@Composable
actual fun rememberShareFilePicker(
	onFilePicked: (PickedShareFile) -> Unit,
	onError: (String) -> Unit,
): ShareFilePicker = remember(onFilePicked, onError) {
	object : ShareFilePicker {
		@OptIn(ExperimentalForeignApi::class)
		override fun pickFile() {
			val presenter = UIApplication.sharedApplication.keyWindow?.rootViewController
			if (presenter == null) {
				onError("Could not find an iOS view controller for the document picker")
				return
			}

			val picker = UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeItem), asCopy = false)
			val delegate = DocumentPickerDelegate(onFilePicked, onError)
			retainedPickerDelegate = delegate
			picker.delegate = delegate
			picker.modalPresentationStyle = UIModalPresentationFormSheet
			presenter.presentViewController(picker, animated = true, completion = null)
		}
	}
}

@Composable
actual fun rememberReceiveFolderPicker(
	onFolderPicked: (ReceiveFolder) -> Unit,
	onError: (String) -> Unit,
): ReceiveFolderPicker = remember(onFolderPicked, onError) {
	object : ReceiveFolderPicker {
		@OptIn(ExperimentalForeignApi::class)
		override fun pickFolder() {
			val presenter = UIApplication.sharedApplication.keyWindow?.rootViewController
			if (presenter == null) {
				onError("Could not find an iOS view controller for the folder picker")
				return
			}

			val picker = UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeFolder), asCopy = false)
			val delegate = DocumentPickerDelegate(
				onFilePicked = { folder ->
					onFolderPicked(
						ReceiveFolder(
							kind = ReceiveFolderKind.IosSecurityScopedUrl,
							value = folder.value,
							displayName = folder.displayName,
						),
					)
				},
				onError = onError,
			)
			retainedPickerDelegate = delegate
			picker.delegate = delegate
			picker.modalPresentationStyle = UIModalPresentationFormSheet
			presenter.presentViewController(picker, animated = true, completion = null)
		}
	}
}

actual suspend fun sharePickedFile(
	repository: CoreRepository,
	file: PickedShareFile,
	transferName: String,
	senderName: String,
) {
	repository.shareSecurityScopedFileUrl(file.value, file.displayName, transferName, senderName)
}

private class DocumentPickerDelegate(
	private val onFilePicked: (PickedShareFile) -> Unit,
	private val onError: (String) -> Unit,
) : NSObject(), UIDocumentPickerDelegateProtocol {
	override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
		val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL
		if (url == null) {
			onError("The selected iOS document URL was invalid")
		} else {
			val displayName = url.lastPathComponent ?: "transfer"
			onFilePicked(PickedShareFile(url.absoluteString ?: url.path.orEmpty(), displayName))
		}
		retainedPickerDelegate = null
	}

	override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
		retainedPickerDelegate = null
	}
}
