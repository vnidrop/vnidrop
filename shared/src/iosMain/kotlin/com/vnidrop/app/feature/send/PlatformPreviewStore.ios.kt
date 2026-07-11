package com.vnidrop.app.feature.send

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSDate
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileModificationDate
import platform.Foundation.NSFileSize
import platform.Foundation.NSNumber
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.timeIntervalSince1970
import platform.Foundation.writeToFile

actual fun createPlatformPreviewStore(appDataDir: String): PlatformPreviewStore =
	IosPreviewStore(appDataDir.trimEnd('/') + "/ui/previews")

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private class IosPreviewStore(private val directory: String) : PlatformPreviewStore {
	private val files = NSFileManager.defaultManager

	override fun list(): List<PreviewFileInfo> {
		ensureDirectory()
		return files.contentsOfDirectoryAtPath(directory, null).orEmpty().filterIsInstance<String>().mapNotNull { name ->
			val id = name.removeSuffix(".preview").toULongOrNull() ?: return@mapNotNull null
			val attributes = files.attributesOfItemAtPath("$directory/$name", null) ?: return@mapNotNull null
			val size = (attributes[NSFileSize] as? NSNumber)?.longLongValue ?: 0L
			val modified = ((attributes[NSFileModificationDate] as? NSDate)?.timeIntervalSince1970 ?: 0.0) * 1000.0
			PreviewFileInfo(id, size, modified.toLong())
		}
	}

	override fun read(transferId: ULong): ByteArray? {
		val data = NSData.dataWithContentsOfFile(path(transferId)) ?: return null
		return data.bytes?.readBytes(data.length.toInt())
	}

	override fun writeAtomically(transferId: ULong, bytes: ByteArray): Boolean {
		ensureDirectory()
		if (files.fileExistsAtPath(path(transferId))) return true
		val temporary = "$directory/.$transferId.tmp"
		val data = bytes.usePinned { pinned -> NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong()) }
		if (!data.writeToFile(temporary, atomically = true)) return false
		val moved = files.moveItemAtPath(temporary, path(transferId), null)
		if (!moved) files.removeItemAtPath(temporary, null)
		return moved
	}

	override fun delete(transferId: ULong) {
		files.removeItemAtPath(path(transferId), null)
	}

	private fun ensureDirectory() {
		files.createDirectoryAtPath(directory, withIntermediateDirectories = true, attributes = null, error = null)
	}

	private fun path(transferId: ULong) = "$directory/$transferId.preview"
}
