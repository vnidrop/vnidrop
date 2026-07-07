package com.vnidrop.app.core

import androidx.compose.runtime.Composable
import uniffi.vnidrop.ReceiveOutputSink

enum class ReceiveFolderKind {
	FileSystemPath,
	AndroidTreeUri,
	IosSecurityScopedUrl,
}

data class ReceiveFolder(
	val kind: ReceiveFolderKind,
	val value: String,
	val displayName: String,
)

enum class FolderAccessStatus {
	Writable,
	PermissionRequired,
	Unavailable,
}

interface FileSystemService {
	fun defaultReceiveFolder(): ReceiveFolder
	suspend fun validateReceiveFolder(folder: ReceiveFolder): FolderAccessStatus
	fun createReceiveOutputSink(folder: ReceiveFolder): ReceiveOutputSink?
}

@Composable
expect fun rememberFileSystemService(): FileSystemService

fun ReceiveFolder.isFileSystemPath(): Boolean =
	kind == ReceiveFolderKind.FileSystemPath
