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
			ReceiveFolderKind.AndroidTreeUri,
			ReceiveFolderKind.AndroidPublicDownloads -> FolderAccessStatus.Unavailable
		}

	override fun createReceiveOutputSink(folder: ReceiveFolder): ReceiveOutputSink? = null

	override suspend fun sharePickedFiles(
		repository: CoreGateway,
		files: List<PickedShareFile>,
		transferName: String,
		senderName: String,
		accessPolicy: ShareAccessPolicy,
	): Result<Share> {
		require(files.isNotEmpty()) { "Select at least one file to share" }
		val sources = files.map { file ->
			uniffi.vnidrop.ShareSource(
				kind = uniffi.vnidrop.SourceKind.IOS_SECURITY_SCOPED_URL,
				value = file.value,
				displayName = file.displayName,
				isDirectory = false,
			)
		}
		return repository.shareSources(sources, transferName, senderName, accessPolicy)
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
