package com.vnidrop.app.logging

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSDate
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileModificationDate
import platform.Foundation.NSFileSize
import platform.Foundation.NSNumber
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.timeIntervalSince1970
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fwrite
import platform.posix.memcpy

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

	override fun readLatest(maxBytes: Long): String {
		if (maxBytes <= 0) return ""
		ensureDirectory()
		val paths = listOf(activePath) +
			(1..policy.maxFiles).map { "$directory/app.$it.log" }
		val chunks = ArrayList<ByteArray>()
		var remaining = maxBytes
		for (path in paths) {
			if (remaining <= 0 || !fileManager.fileExistsAtPath(path)) continue
			val slice = readTail(path, remaining)
			if (slice.isEmpty()) continue
			chunks.add(0, slice)
			remaining -= slice.size.toLong()
		}
		if (chunks.isEmpty()) return ""
		val total = chunks.sumOf { it.size }
		val out = ByteArray(total)
		var offset = 0
		for (chunk in chunks) {
			chunk.copyInto(out, offset)
			offset += chunk.size
		}
		return out.decodeToString()
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

	private fun readTail(path: String, maxBytes: Long): ByteArray {
		val data = NSData.dataWithContentsOfFile(path) ?: return ByteArray(0)
		val all = data.toByteArray()
		if (all.isEmpty() || maxBytes <= 0) return ByteArray(0)
		if (all.size.toLong() <= maxBytes) return all
		val start = all.size - maxBytes.toInt()
		val slice = all.copyOfRange(start, all.size)
		val newline = slice.indexOf('\n'.code.toByte())
		return if (newline in 0 until slice.lastIndex) {
			slice.copyOfRange(newline + 1, slice.size)
		} else {
			slice
		}
	}
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
	val size = length.toInt()
	if (size == 0) return ByteArray(0)
	val result = ByteArray(size)
	val source = bytes ?: return ByteArray(0)
	result.usePinned { pinned ->
		memcpy(pinned.addressOf(0), source, size.convert())
	}
	return result
}
