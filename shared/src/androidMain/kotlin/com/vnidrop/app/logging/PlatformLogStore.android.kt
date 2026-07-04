package com.vnidrop.app.logging

import java.io.File
import java.nio.charset.StandardCharsets

actual fun createPlatformLogStore(appDataDir: String, policy: LogRotationPolicy): PlatformLogStore =
	AndroidPlatformLogStore(appDataDir, policy)

actual fun platformNowMillis(): Long = System.currentTimeMillis()

private class AndroidPlatformLogStore(
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
