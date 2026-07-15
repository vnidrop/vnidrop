package com.vnidrop.app.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSFileManager
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.UIKit.UIApplication
import uniffi.vnidrop.ReceiveOutputSink
import uniffi.vnidrop.SourceKind
import kotlin.coroutines.resume

@Composable
actual fun rememberFileSystemService(): FileSystemService =
	remember { IosFileSystemService() }

private class IosFileSystemService : FileSystemService {
	// App-owned Documents remains durable across launches; raw external picker URLs do not.
	override val supportsCustomReceiveFolders: Boolean = false

	override fun defaultReceiveFolder(): ReceiveFolder {
		val path = NSSearchPathForDirectoriesInDomains(
			NSDocumentDirectory,
			NSUserDomainMask,
			true,
		).firstOrNull() as? String ?: ""
		return ReceiveFolder(
			kind = ReceiveFolderKind.FileSystemPath,
			value = path,
			displayName = "Documents",
		)
	}

	override suspend fun validateReceiveFolder(folder: ReceiveFolder): FolderAccessStatus =
		when (folder.kind) {
			ReceiveFolderKind.FileSystemPath -> {
				if (NSFileManager.defaultManager.isWritableFileAtPath(folder.value)) {
					FolderAccessStatus.Writable
				} else {
					FolderAccessStatus.Unavailable
				}
			}
			ReceiveFolderKind.IosSecurityScopedUrl -> validateSecurityScopedUrl(folder.value)
			ReceiveFolderKind.AndroidTreeUri,
			ReceiveFolderKind.AndroidPublicDownloads -> FolderAccessStatus.Unavailable
		}

	override fun createReceiveOutputSink(folder: ReceiveFolder): ReceiveOutputSink? = null

	override suspend fun discardPickedFiles(files: List<PickedShareFile>) {
		files.asSequence()
			.filter(PickedShareFile::isTemporaryCopy)
			.map(PickedShareFile::value)
			.distinct()
			.forEach { path -> NSFileManager.defaultManager.removeItemAtPath(path, null) }
	}

	override fun canRevealReceiveFolder(folder: ReceiveFolder): Boolean =
		folder.kind == ReceiveFolderKind.FileSystemPath &&
			folder.value.trimEnd('/') == defaultReceiveFolder().value.trimEnd('/')

	override suspend fun revealReceiveFolder(folder: ReceiveFolder): Result<Unit> {
		if (!canRevealReceiveFolder(folder)) {
			return Result.failure(IllegalArgumentException("The receive folder is not VniDrop Documents"))
		}
		// Files can reveal app-owned Documents after the sharing keys in Info.plist are enabled.
		val url = NSURL.URLWithString("shareddocuments://${folder.value}")
			?: return Result.failure(IllegalStateException("The Files location URL is unavailable"))
		val opened = suspendCancellableCoroutine { continuation ->
			UIApplication.sharedApplication.openURL(url, emptyMap<Any?, Any>()) { success ->
				if (continuation.isActive) continuation.resume(success)
			}
		}
		return if (opened) {
			Result.success(Unit)
		} else {
			Result.failure(IllegalStateException("Could not open VniDrop Documents in Files"))
		}
	}

	override suspend fun sharePickedFiles(
		repository: CoreGateway,
		files: List<PickedShareFile>,
		transferName: String,
		senderName: String,
		accessPolicy: ShareAccessPolicy,
	): Result<Share> {
		require(files.isNotEmpty()) { "Select at least one file to share" }
		return repository.shareSources(files.map(PickedShareFile::toIosShareSource), transferName, senderName, accessPolicy)
	}

	private fun validateSecurityScopedUrl(value: String): FolderAccessStatus {
		val url = NSURL.URLWithString(value) ?: NSURL.fileURLWithPath(value)
		val didStartAccess = url.startAccessingSecurityScopedResource()
		return try {
			val path = url.path
			if (path != null && NSFileManager.defaultManager.isWritableFileAtPath(path)) {
				FolderAccessStatus.Writable
			} else {
				FolderAccessStatus.PermissionRequired
			}
		} finally {
			if (didStartAccess) url.stopAccessingSecurityScopedResource()
		}
	}
}

internal fun PickedShareFile.toIosShareSource() = uniffi.vnidrop.ShareSource(
	kind = SourceKind.PATH,
	value = value,
	displayName = displayName,
	isDirectory = isDirectory,
)
