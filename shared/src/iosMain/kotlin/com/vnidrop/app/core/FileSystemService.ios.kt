package com.vnidrop.app.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.Foundation.NSFileManager
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import uniffi.vnidrop.ReceiveOutputSink

@Composable
actual fun rememberFileSystemService(): FileSystemService =
	remember { IosFileSystemService() }

private class IosFileSystemService : FileSystemService {
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
			ReceiveFolderKind.AndroidTreeUri -> FolderAccessStatus.Unavailable
		}

	override fun createReceiveOutputSink(folder: ReceiveFolder): ReceiveOutputSink? = null

	override suspend fun sharePickedFile(
		repository: CoreGateway,
		file: PickedShareFile,
		transferName: String,
		senderName: String,
	): Result<Share> = repository.shareSecurityScopedFileUrl(
		file.value,
		file.displayName,
		transferName,
		senderName,
	)

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
