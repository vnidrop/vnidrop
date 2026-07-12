package com.vnidrop.app.core

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import uniffi.vnidrop.ReceiveOutputSink
import java.io.File
import java.io.OutputStream
import java.net.URLConnection
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
		// Match desktop: shared system Downloads. On Android 10+ this is MediaStore,
		// not a raw filesystem path (scoped storage). Older APIs fall back to the
		// public Downloads directory when legacy storage writes are allowed.
		return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
			ReceiveFolder(
				kind = ReceiveFolderKind.AndroidPublicDownloads,
				value = AndroidPublicDownloadsToken,
				displayName = "Downloads",
			)
		} else {
			val publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
			ReceiveFolder(
				kind = ReceiveFolderKind.FileSystemPath,
				value = publicDownloads.absolutePath,
				displayName = "Downloads",
			)
		}
	}

	override suspend fun validateReceiveFolder(folder: ReceiveFolder): FolderAccessStatus =
		when (folder.kind) {
			ReceiveFolderKind.FileSystemPath -> validatePath(folder.value)
			ReceiveFolderKind.AndroidPublicDownloads -> validatePublicDownloads()
			ReceiveFolderKind.AndroidTreeUri -> validateTreeUri(folder.value)
			ReceiveFolderKind.IosSecurityScopedUrl -> FolderAccessStatus.Unavailable
		}

	override fun createReceiveOutputSink(folder: ReceiveFolder): ReceiveOutputSink? =
		when (folder.kind) {
			ReceiveFolderKind.AndroidPublicDownloads -> AndroidMediaStoreDownloadsSink(context)
			ReceiveFolderKind.AndroidTreeUri -> AndroidTreeReceiveOutputSink(context, folder.value.toUri())
			ReceiveFolderKind.FileSystemPath,
			ReceiveFolderKind.IosSecurityScopedUrl -> null
		}

	override suspend fun sharePickedFiles(
		repository: CoreGateway,
		files: List<PickedShareFile>,
		transferName: String,
		senderName: String,
		accessPolicy: ShareAccessPolicy,
	): Result<Share> = runCatching {
		require(files.isNotEmpty()) { "Select at least one file to share" }
		val descriptors = files.map { file ->
			context.contentResolver.openFileDescriptor(Uri.parse(file.value), "r")
				?: error("Could not open selected file descriptor for ${file.displayName}")
		}
		try {
			val sources = files.zip(descriptors) { file, descriptor ->
				uniffi.vnidrop.ShareSource(
					kind = uniffi.vnidrop.SourceKind.FILE_DESCRIPTOR,
					value = descriptor.fd.toString(),
					displayName = file.displayName,
					isDirectory = false,
				)
			}
			repository.shareSources(sources, transferName, senderName, accessPolicy).getOrThrow()
		} finally {
			descriptors.forEach { it.close() }
		}
	}

	/**
	 * Probe a real create/write/delete instead of [File.canWrite].
	 *
	 * Scoped storage often reports public directories as writable even when
	 * the process cannot create files there. A probe matches what receive needs.
	 */
	private fun validatePath(path: String): FolderAccessStatus =
		runCatching {
			val directory = File(path)
			if (!directory.exists() && !directory.mkdirs()) {
				return FolderAccessStatus.Unavailable
			}
			if (!directory.isDirectory) return FolderAccessStatus.Unavailable
			val probe = File(directory, ".vnidrop-write-test-${UUID.randomUUID()}")
			try {
				probe.outputStream().use { stream -> stream.write(1) }
				if (!probe.exists()) return FolderAccessStatus.Unavailable
				FolderAccessStatus.Writable
			} finally {
				probe.delete()
			}
		}.getOrDefault(FolderAccessStatus.Unavailable)

	private fun validatePublicDownloads(): FolderAccessStatus =
		runCatching {
			val probeName = ".vnidrop-write-test-${UUID.randomUUID()}"
			val sink = AndroidMediaStoreDownloadsSink(context)
			sink.startFile(probeName)
			sink.writeChunk(probeName, byteArrayOf(1))
			sink.abortFile(probeName, "write probe complete")
			FolderAccessStatus.Writable
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

/**
 * Writes into the shared system Downloads collection via MediaStore.
 *
 * Files appear in the user's Downloads app / Files UI the same way a browser
 * download would. Nested relative paths become subfolders under Download/.
 */
private class AndroidMediaStoreDownloadsSink(
	private val context: Context,
) : ReceiveOutputSink {
	private data class PendingDocument(
		val stream: OutputStream,
		val uri: Uri,
	)

	private val pending = mutableMapOf<String, PendingDocument>()
	private val resolver = context.contentResolver

	override fun startFile(relativePath: String) {
		check(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
			"MediaStore Downloads requires Android 10 or newer"
		}
		check(relativePath !in pending) { "Output stream is already open for $relativePath" }
		val parts = relativePath.split('/').filter { it.isNotBlank() }
		require(parts.isNotEmpty()) { "relative path must not be empty" }
		val finalName = parts.last()
		val relativeDir = mediaStoreRelativePath(parts.dropLast(1))
		check(!mediaStoreItemExists(finalName, relativeDir)) {
			"Destination already exists: $relativePath"
		}

		val values = ContentValues().apply {
			put(MediaStore.MediaColumns.DISPLAY_NAME, finalName)
			put(MediaStore.MediaColumns.MIME_TYPE, mimeTypeFor(finalName))
			put(MediaStore.MediaColumns.RELATIVE_PATH, relativeDir)
			put(MediaStore.MediaColumns.IS_PENDING, 1)
		}
		val uri = resolver.insert(downloadsCollection(), values)
			?: error("Could not create Downloads entry for $relativePath")
		val stream = runCatching { resolver.openOutputStream(uri, "w") }
			.getOrElse { error ->
				resolver.delete(uri, null, null)
				throw error
			} ?: run {
				resolver.delete(uri, null, null)
				error("Could not open output stream for $relativePath")
			}
		pending[relativePath] = PendingDocument(stream, uri)
	}

	override fun writeChunk(relativePath: String, bytes: ByteArray) {
		val document = pending[relativePath] ?: error("Output stream is not open for $relativePath")
		document.stream.write(bytes)
	}

	override fun finishFile(relativePath: String) {
		val document = pending.remove(relativePath) ?: error("Output stream is not open for $relativePath")
		try {
			document.stream.flush()
			document.stream.close()
			val published = ContentValues().apply {
				put(MediaStore.MediaColumns.IS_PENDING, 0)
			}
			val updated = resolver.update(document.uri, published, null, null)
			check(updated == 1) { "Could not publish received file $relativePath" }
		} catch (error: Throwable) {
			runCatching { document.stream.close() }
			resolver.delete(document.uri, null, null)
			throw error
		}
	}

	override fun abortFile(relativePath: String, reason: String) {
		val document = pending.remove(relativePath) ?: return
		runCatching { document.stream.close() }
		resolver.delete(document.uri, null, null)
	}

	private fun downloadsCollection(): Uri =
		MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

	private fun mediaStoreRelativePath(subdirs: List<String>): String {
		val base = Environment.DIRECTORY_DOWNLOADS
		return if (subdirs.isEmpty()) {
			"$base/"
		} else {
			"$base/${subdirs.joinToString("/")}/"
		}
	}

	private fun mediaStoreItemExists(displayName: String, relativePath: String): Boolean {
		val projection = arrayOf(MediaStore.MediaColumns._ID)
		val selection =
			"${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH}=?"
		resolver.query(
			downloadsCollection(),
			projection,
			selection,
			arrayOf(displayName, relativePath),
			null,
		)?.use { cursor ->
			return cursor.moveToFirst()
		}
		// Some providers omit the trailing slash; check the alternate form.
		val altPath = relativePath.trimEnd('/')
		if (altPath != relativePath) {
			resolver.query(
				downloadsCollection(),
				projection,
				selection,
				arrayOf(displayName, altPath),
				null,
			)?.use { cursor ->
				return cursor.moveToFirst()
			}
		}
		return false
	}

	private fun mimeTypeFor(fileName: String): String {
		val extension = fileName.substringAfterLast('.', missingDelimiterValue = "").lowercase()
		if (extension.isNotEmpty()) {
			MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)?.let { return it }
			URLConnection.guessContentTypeFromName(fileName)?.let { return it }
		}
		return "application/octet-stream"
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
