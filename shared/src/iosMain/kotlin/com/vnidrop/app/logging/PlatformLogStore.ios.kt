package com.vnidrop.app.logging

import platform.Foundation.NSData
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.Foundation.NSDate
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileModificationDate
import platform.Foundation.NSFileSize
import platform.Foundation.NSNumber
import platform.Foundation.timeIntervalSince1970
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fwrite

actual fun createPlatformLogStore(appDataDir: String, policy: LogRotationPolicy): PlatformLogStore =
	IosPlatformLogStore(appDataDir, policy)

actual fun platformNowMillis(): Long =
	(NSDate().timeIntervalSince1970 * 1000.0).toLong()

@OptIn(ExperimentalForeignApi::class)
private class IosPlatformLogStore(
	appDataDir: String,
	private val policy: LogRotationPolicy,
) : PlatformLogStore {
	private val fileManager = NSFileManager.defaultManager
	private val directory = appDataDir.trimEnd('/') + "/logs"
	private val activePath = "$directory/app.log"

	override val logDirectory: String = directory

	override fun append(line: String) {
		ensureDirectory()
		val bytes = line.encodeToByteArray()
		if (policy.shouldRotate(fileSize(activePath), bytes.size.toLong())) {
			rotate()
		}
		val file = fopen(activePath, "ab") ?: return
		try {
			bytes.usePinned { pinned ->
				fwrite(pinned.addressOf(0), 1.convert(), bytes.size.convert(), file)
			}
		} finally {
			fclose(file)
		}
	}

	override fun listLogFiles(): List<LogFileInfo> {
		ensureDirectory()
		val names = fileManager.contentsOfDirectoryAtPath(directory, null).orEmpty()
			.filterIsInstance<String>()
			.filter { it.startsWith("app") && it.endsWith(".log") }
		return names
			.map { name ->
				val path = "$directory/$name"
				LogFileInfo(name, path, fileSize(path), modifiedAt(path))
			}
			.sortedByDescending { it.modifiedAtMillis }
	}

	private fun rotate() {
		if (policy.maxFiles == 0) {
			fileManager.removeItemAtPath(activePath, null)
			return
		}
		fileManager.removeItemAtPath("$directory/app.${policy.maxFiles}.log", null)
		for (index in policy.maxFiles - 1 downTo 1) {
			val source = "$directory/app.$index.log"
			if (fileManager.fileExistsAtPath(source)) {
				fileManager.moveItemAtPath(source, "$directory/app.${index + 1}.log", null)
			}
		}
		if (fileManager.fileExistsAtPath(activePath)) {
			fileManager.moveItemAtPath(activePath, "$directory/app.1.log", null)
		}
	}

	private fun ensureDirectory() {
		fileManager.createDirectoryAtPath(directory, withIntermediateDirectories = true, attributes = null, error = null)
	}

	private fun fileSize(path: String): Long {
		val attributes = fileManager.attributesOfItemAtPath(path, null) ?: return 0L
		return (attributes[NSFileSize] as? NSNumber)?.longLongValue ?: 0L
	}

	private fun modifiedAt(path: String): Long {
		val attributes = fileManager.attributesOfItemAtPath(path, null) ?: return 0L
		val date = attributes[NSFileModificationDate] as? NSDate ?: return 0L
		return (date.timeIntervalSince1970 * 1000.0).toLong()
	}
}
