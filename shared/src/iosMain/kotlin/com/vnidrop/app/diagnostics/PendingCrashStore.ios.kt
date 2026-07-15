package com.vnidrop.app.diagnostics

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.create
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
		if (!isValidDiagnosticId(report.id)) return
		ensureDirectory()
		val path = "$directory/${report.id}.crash"
		val payload = CrashReportCodec.encode(report)
		val data = payload.encodeToByteArray().toNSData()
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
		if (!isValidDiagnosticId(id)) return
		fileManager.removeItemAtPath("$directory/$id.crash", null)
	}

	override fun prune(olderThanTimestampMillis: Long, maxCount: Int) {
		require(maxCount > 0) { "maxCount must be positive" }
		ensureDirectory()
		val reports = fileManager.contentsOfDirectoryAtPath(directory, null).orEmpty()
			.filterIsInstance<String>()
			.filter { it.endsWith(".crash") }
			.mapNotNull { name ->
				val path = "$directory/$name"
				val report = NSData.dataWithContentsOfFile(path)
					?.toUtf8String()
					?.let(CrashReportCodec::decode)
				if (report == null) {
					fileManager.removeItemAtPath(path, null)
					null
				} else {
					name to report
				}
			}
			.sortedByDescending { (_, report) -> report.timestampMillis }
		reports.forEachIndexed { index, (name, report) ->
			if (index >= maxCount || report.timestampMillis < olderThanTimestampMillis) {
				fileManager.removeItemAtPath("$directory/$name", null)
			}
		}
	}

	private fun ensureDirectory() {
		fileManager.createDirectoryAtPath(directory, withIntermediateDirectories = true, attributes = null, error = null)
	}
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun ByteArray.toNSData(): NSData =
	usePinned { pinned ->
		NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
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
