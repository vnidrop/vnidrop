package com.vnidrop.app.diagnostics

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.writeToFile
import platform.posix.memcpy

actual fun createPendingCrashStore(appDataDir: String): PendingCrashStore =
	IosPendingCrashStore(appDataDir)

@OptIn(ExperimentalForeignApi::class)
private class IosPendingCrashStore(
	appDataDir: String,
) : PendingCrashStore {
	private val fileManager = NSFileManager.defaultManager
	private val directory = appDataDir.trimEnd('/') + "/diagnostics/crashes"

	override fun write(report: CrashReport) {
		ensureDirectory()
		val path = "$directory/${report.id}.crash"
		val payload = CrashReportCodec.encode(report)
		val data = (payload as NSString).dataUsingEncoding(NSUTF8StringEncoding) ?: return
		data.writeToFile(path, atomically = true)
	}

	override fun list(): List<CrashReport> {
		ensureDirectory()
		val names = fileManager.contentsOfDirectoryAtPath(directory, null).orEmpty()
			.filterIsInstance<String>()
			.filter { it.endsWith(".crash") }
		return names.mapNotNull { name ->
			val path = "$directory/$name"
			val data = NSData.dataWithContentsOfFile(path) ?: return@mapNotNull null
			val text = data.toUtf8String()
			CrashReportCodec.decode(text)
		}.sortedByDescending { it.timestampMillis }
	}

	override fun delete(id: String) {
		fileManager.removeItemAtPath("$directory/$id.crash", null)
	}

	private fun ensureDirectory() {
		fileManager.createDirectoryAtPath(directory, withIntermediateDirectories = true, attributes = null, error = null)
	}
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toUtf8String(): String {
	val size = length.toInt()
	if (size == 0) return ""
	val result = ByteArray(size)
	val source = bytes ?: return ""
	result.usePinned { pinned ->
		memcpy(pinned.addressOf(0), source, size.convert())
	}
	return result.decodeToString()
}
