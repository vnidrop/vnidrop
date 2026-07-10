package com.vnidrop.app.core

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import androidx.core.net.toUri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import uniffi.vnidrop.ReceiveOutputSink
import java.io.OutputStream
import java.util.UUID

@Composable
actual fun rememberFileSystemService(): FileSystemService {
	val context = LocalContext.current.applicationContext
	return remember(context) { AndroidFileSystemService(context) }
}

private class AndroidFileSystemService(
	private val context: Context,
) : FileSystemService {
	override fun defaultReceiveFolder(): ReceiveFolder {
		val path = context
			.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
			?.absolutePath
			?: (System.getProperty("java.io.tmpdir") ?: "/data/local/tmp/vnidrop-receive")
		return ReceiveFolder(
			kind = ReceiveFolderKind.FileSystemPath,
			value = path,
			displayName = "Downloads",
		)
	}

	override suspend fun validateReceiveFolder(folder: ReceiveFolder): FolderAccessStatus =
		when (folder.kind) {
			ReceiveFolderKind.FileSystemPath -> validatePath(folder.value)
			ReceiveFolderKind.AndroidTreeUri -> validateTreeUri(folder.value)
			ReceiveFolderKind.IosSecurityScopedUrl -> FolderAccessStatus.Unavailable
		}

	override fun createReceiveOutputSink(folder: ReceiveFolder): ReceiveOutputSink? {
		if (folder.kind != ReceiveFolderKind.AndroidTreeUri) return null
		return AndroidTreeReceiveOutputSink(context, folder.value.toUri())
	}

	private fun validatePath(path: String): FolderAccessStatus =
		runCatching {
			val directory = java.io.File(path)
			if (!directory.exists()) directory.mkdirs()
			if (directory.isDirectory && directory.canWrite()) FolderAccessStatus.Writable else FolderAccessStatus.Unavailable
		}.getOrDefault(FolderAccessStatus.Unavailable)

	private fun validateTreeUri(value: String): FolderAccessStatus {
		val uri = Uri.parse(value)
		val hasPermission = context.contentResolver.persistedUriPermissions.any { permission ->
			permission.uri == uri && permission.isWritePermission
		}
		if (!hasPermission) return FolderAccessStatus.PermissionRequired
		return runCatching {
			val probe = AndroidTreeReceiveOutputSink(context, uri)
			val probeName = ".vnidrop-write-test-${UUID.randomUUID()}"
			probe.startFile(probeName)
			probe.writeChunk(probeName, byteArrayOf())
			probe.abortFile(probeName, "write probe complete")
			FolderAccessStatus.Writable
		}.getOrDefault(FolderAccessStatus.Unavailable)
	}
}

private class AndroidTreeReceiveOutputSink(
	private val context: Context,
	private val treeUri: Uri,
) : ReceiveOutputSink {
	private data class PendingDocument(
		val stream: OutputStream,
		val temporaryUri: Uri,
		val parentUri: Uri,
		val finalName: String,
	)

	private val pending = mutableMapOf<String, PendingDocument>()

	override fun startFile(relativePath: String) {
		check(relativePath !in pending) { "Output stream is already open for $relativePath" }
		val (parent, finalName) = resolveParent(relativePath)
		check(findChild(parent, finalName) == null) { "Destination already exists: $relativePath" }
		val temporaryName = ".$finalName.vnidrop-${UUID.randomUUID()}.part"
		val temporaryUri = DocumentsContract.createDocument(
			context.contentResolver,
			parent,
			"application/octet-stream",
			temporaryName,
		) ?: error("Could not create temporary file for $relativePath")
		val stream = context.contentResolver.openOutputStream(temporaryUri, "w")
			?: error("Could not open output stream for $relativePath")
		pending[relativePath] = PendingDocument(stream, temporaryUri, parent, finalName)
	}

	override fun writeChunk(relativePath: String, bytes: ByteArray) {
		val document = pending[relativePath] ?: error("Output stream is not open for $relativePath")
		document.stream.write(bytes)
	}

	override fun finishFile(relativePath: String) {
		val document = pending.remove(relativePath) ?: error("Output stream is not open for $relativePath")
		try {
			document.stream.close()
			check(findChild(document.parentUri, document.finalName) == null) { "Destination already exists: $relativePath" }
			checkNotNull(
				DocumentsContract.renameDocument(
					context.contentResolver,
					document.temporaryUri,
					document.finalName,
				),
			) { "Could not commit received file $relativePath" }
		} catch (error: Throwable) {
			runCatching { document.stream.close() }
			DocumentsContract.deleteDocument(context.contentResolver, document.temporaryUri)
			throw error
		}
	}

	override fun abortFile(relativePath: String, reason: String) {
		val document = pending.remove(relativePath) ?: return
		runCatching { document.stream.close() }
		DocumentsContract.deleteDocument(context.contentResolver, document.temporaryUri)
	}

	private fun resolveParent(relativePath: String): Pair<Uri, String> {
		val parts = relativePath.split('/').filter { it.isNotBlank() }
		require(parts.isNotEmpty()) { "relative path must not be empty" }
		var parent = DocumentsContract.buildDocumentUriUsingTree(
			treeUri,
			DocumentsContract.getTreeDocumentId(treeUri),
		)
		parts.dropLast(1).forEach { name ->
			parent = findChild(parent, name)
				?: DocumentsContract.createDocument(context.contentResolver, parent, DocumentsContract.Document.MIME_TYPE_DIR, name)
					?: error("Could not create directory $name")
		}
		return parent to parts.last()
	}

	private fun findChild(parent: Uri, name: String): Uri? {
		val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
			treeUri,
			DocumentsContract.getDocumentId(parent),
		)
		context.contentResolver.query(
			childrenUri,
			arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
			null,
			null,
			null,
		)?.use { cursor ->
			val idIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
			val nameIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
			while (cursor.moveToNext()) {
				if (cursor.getString(nameIndex) == name) {
					return DocumentsContract.buildDocumentUriUsingTree(treeUri, cursor.getString(idIndex))
				}
			}
		}
		return null
	}
}
