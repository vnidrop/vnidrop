package com.vnidrop.app.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import uniffi.vnidrop.ReceiveOutputSink
import java.io.File

@Composable
actual fun rememberFileSystemService(): FileSystemService =
	remember { JvmFileSystemService() }

private class JvmFileSystemService : FileSystemService {
	override fun defaultReceiveFolder(): ReceiveFolder =
		ReceiveFolder(
			kind = ReceiveFolderKind.FileSystemPath,
			value = File(System.getProperty("user.home"), "Downloads").absolutePath,
			displayName = "Downloads",
		)

	override suspend fun validateReceiveFolder(folder: ReceiveFolder): FolderAccessStatus {
		if (folder.kind != ReceiveFolderKind.FileSystemPath) return FolderAccessStatus.Unavailable
		return runCatching {
			val directory = File(folder.value)
			if (!directory.exists()) directory.mkdirs()
			if (directory.isDirectory && directory.canWrite()) FolderAccessStatus.Writable else FolderAccessStatus.Unavailable
		}.getOrDefault(FolderAccessStatus.Unavailable)
	}

	override fun createReceiveOutputSink(folder: ReceiveFolder): ReceiveOutputSink? = null

	override suspend fun sharePickedFile(
		repository: CoreGateway,
		file: PickedShareFile,
		transferName: String,
		senderName: String,
	): Result<Share> = repository.sharePath(file.value, transferName, senderName)
}
