package com.vnidrop.app.logging

import java.io.File
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets

actual fun createPlatformLogStore(appDataDir: String, policy: LogRotationPolicy): PlatformLogStore =
	JvmPlatformLogStore(appDataDir, policy)

actual fun platformNowMillis(): Long = System.currentTimeMillis()

private class JvmPlatformLogStore(
	appDataDir: String,
	private val policy: LogRotationPolicy,
) : PlatformLogStore {
	private val directory = File(appDataDir, "logs")
	private val activeFile = File(directory, "app.log")

	override val logDirectory: String = directory.absolutePath

	@Synchronized
	override fun append(line: String) {
		directory.mkdirs()
		val bytes = line.toByteArray(StandardCharsets.UTF_8)
		if (policy.shouldRotate(activeFile.length(), bytes.size.toLong())) {
			rotate()
		}
		activeFile.appendBytes(bytes)
	}

	@Synchronized
	override fun listLogFiles(): List<LogFileInfo> {
		directory.mkdirs()
		return directory
			.listFiles { file -> file.isFile && file.name.startsWith("app") && file.name.endsWith(".log") }
			.orEmpty()
			.sortedByDescending { it.lastModified() }
			.map { file -> LogFileInfo(file.name, file.absolutePath, file.length(), file.lastModified()) }
	}

	@Synchronized
	override fun readLatest(maxBytes: Long): String {
		if (maxBytes <= 0) return ""
		directory.mkdirs()
		val files = listOf(activeFile) +
			(1..policy.maxFiles).map { File(directory, "app.$it.log") }
		val chunks = ArrayList<ByteArray>()
		var remaining = maxBytes
		for (file in files) {
			if (remaining <= 0 || !file.isFile) continue
			val slice = readTail(file, remaining)
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
		return String(out, StandardCharsets.UTF_8)
	}

	private fun rotate() {
		if (policy.maxFiles == 0) {
			activeFile.delete()
			return
		}
		File(directory, "app.${policy.maxFiles}.log").delete()
		for (index in policy.maxFiles - 1 downTo 1) {
			val source = File(directory, "app.$index.log")
			if (source.exists()) {
				source.renameTo(File(directory, "app.${index + 1}.log"))
			}
		}
		if (activeFile.exists()) {
			activeFile.renameTo(File(directory, "app.1.log"))
		}
	}
}

private fun readTail(file: File, maxBytes: Long): ByteArray {
	if (!file.isFile || file.length() == 0L || maxBytes <= 0) return ByteArray(0)
	val length = file.length()
	val start = (length - maxBytes).coerceAtLeast(0L)
	val size = (length - start).toInt()
	RandomAccessFile(file, "r").use { raf ->
		raf.seek(start)
		val bytes = ByteArray(size)
		raf.readFully(bytes)
		if (start == 0L) return bytes
		// Align to the next full line when we start mid-file.
		val newline = bytes.indexOf('\n'.code.toByte())
		return if (newline in 0 until bytes.lastIndex) {
			bytes.copyOfRange(newline + 1, bytes.size)
		} else {
			bytes
		}
	}
}
