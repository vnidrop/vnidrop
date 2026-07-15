package com.vnidrop.app.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readBytes
import platform.Foundation.NSURL
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSNumber
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIDocumentInteractionController
import platform.UIKit.UIImage
import platform.UIKit.UIImagePNGRepresentation
import platform.UIKit.UIModalPresentationFormSheet
import platform.UniformTypeIdentifiers.UTTypeFolder
import platform.UniformTypeIdentifiers.UTTypeItem
import platform.darwin.NSObject

private var retainedPickerDelegate: DocumentPickerDelegate? = null

@Composable
actual fun rememberShareFilePicker(
	onFilesPicked: (List<PickedShareFile>) -> Unit,
	onError: (String) -> Unit,
): ShareFilePicker = remember(onFilesPicked, onError) {
	object : ShareFilePicker {
		@OptIn(ExperimentalForeignApi::class)
		override fun pickFiles() {
			val presenter = UIApplication.sharedApplication.keyWindow?.rootViewController
			if (presenter == null) {
				onError("Could not find an iOS view controller for the document picker")
				return
			}

			// The composer outlives this callback, so Rust imports a sandbox copy instead of a short-lived provider URL.
			val picker = UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeItem), asCopy = true)
			picker.allowsMultipleSelection = true
			val delegate = DocumentPickerDelegate(
				onFilesPicked = onFilesPicked,
				onError = onError,
				useFileSystemPaths = true,
			)
			retainedPickerDelegate = delegate
			picker.delegate = delegate
			picker.modalPresentationStyle = UIModalPresentationFormSheet
			presenter.presentViewController(picker, animated = true, completion = null)
		}

		@OptIn(ExperimentalForeignApi::class)
		override fun pickFolder() {
			val presenter = UIApplication.sharedApplication.keyWindow?.rootViewController
			if (presenter == null) {
				onError("Could not find an iOS view controller for the folder picker")
				return
			}
			val picker = UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeFolder), asCopy = true)
			val delegate = DocumentPickerDelegate(
				onFilesPicked = { folders ->
					val folder = folders.firstOrNull() ?: return@DocumentPickerDelegate
					onFilesPicked(
						listOf(
							folder.copy(isDirectory = true),
						),
					)
				},
				onError = onError,
				forceDirectory = true,
				useFileSystemPaths = true,
			)
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
				onFilesPicked = { folders ->
					val folder = folders.firstOrNull() ?: return@DocumentPickerDelegate
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

private class DocumentPickerDelegate(
	private val onFilesPicked: (List<PickedShareFile>) -> Unit,
	private val onError: (String) -> Unit,
	private val forceDirectory: Boolean = false,
	private val useFileSystemPaths: Boolean = false,
) : NSObject(), UIDocumentPickerDelegateProtocol {
	override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
		val files = didPickDocumentsAtURLs.mapNotNull { raw ->
			val url = raw as? NSURL ?: return@mapNotNull null
			val displayName = url.lastPathComponent ?: "transfer"
			val didStartAccess = url.startAccessingSecurityScopedResource()
			val sizeBytes = try {
				if (forceDirectory) {
					null
				} else {
					val attributes = url.path?.let { NSFileManager.defaultManager.attributesOfItemAtPath(it, null) }
					(attributes?.get(NSFileSize) as? NSNumber)?.unsignedLongLongValue
				}
			} finally {
				if (didStartAccess) url.stopAccessingSecurityScopedResource()
			}
			PickedShareFile(
				if (useFileSystemPaths) url.path.orEmpty() else url.absoluteString ?: url.path.orEmpty(),
				displayName,
				sizeBytes,
				nativeFileIcon(url),
				isTemporaryCopy = useFileSystemPaths,
				isDirectory = forceDirectory,
			)
		}
		if (files.isEmpty()) {
			onError("The selected iOS document URL was invalid")
		} else {
			onFilesPicked(files)
		}
		retainedPickerDelegate = null
	}

	override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
		retainedPickerDelegate = null
	}
}

@OptIn(ExperimentalForeignApi::class)
private fun nativeFileIcon(url: NSURL): ByteArray? = runCatching {
	val controller = UIDocumentInteractionController.interactionControllerWithURL(url)
	val icon = controller.icons.lastOrNull() as? UIImage ?: return null
	val data = UIImagePNGRepresentation(icon) ?: return null
	data.bytes?.readBytes(data.length.toInt())
}.getOrNull()
