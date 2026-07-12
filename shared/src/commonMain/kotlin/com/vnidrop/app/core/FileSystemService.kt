package com.vnidrop.app.core

import androidx.compose.runtime.Composable
import uniffi.vnidrop.ReceiveOutputSink

enum class ReceiveFolderKind {
	FileSystemPath,
	/** Shared system Downloads via MediaStore (Android 10+). */
	AndroidPublicDownloads,
	AndroidTreeUri,
	IosSecurityScopedUrl,
}

/** Stable token stored in preferences for [ReceiveFolderKind.AndroidPublicDownloads]. */
const val AndroidPublicDownloadsToken = "media-store:downloads"

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
	suspend fun sharePickedFile(
		repository: CoreGateway,
		file: PickedShareFile,
		transferName: String,
		senderName: String,
		accessPolicy: ShareAccessPolicy,
	): Result<Share>
}

@Composable
expect fun rememberFileSystemService(): FileSystemService

fun ReceiveFolder.isFileSystemPath(): Boolean =
	kind == ReceiveFolderKind.FileSystemPath
