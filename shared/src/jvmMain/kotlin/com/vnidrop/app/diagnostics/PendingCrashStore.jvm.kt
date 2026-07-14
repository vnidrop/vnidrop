package com.vnidrop.app.diagnostics

import java.io.File
import java.nio.charset.StandardCharsets

actual fun createPendingCrashStore(appDataDir: String): PendingCrashStore =
	JvmPendingCrashStore(appDataDir)

private class JvmPendingCrashStore(
	appDataDir: String,
) : PendingCrashStore {
	private val directory = File(appDataDir, "diagnostics/crashes")

	@Synchronized
	override fun write(report: CrashReport) {
		directory.mkdirs()
		File(directory, "${report.id}.crash").writeText(CrashReportCodec.encode(report), StandardCharsets.UTF_8)
	}

	@Synchronized
	override fun list(): List<CrashReport> {
		if (!directory.isDirectory) return emptyList()
		return directory
			.listFiles { file -> file.isFile && file.name.endsWith(".crash") }
			.orEmpty()
			.sortedByDescending { it.lastModified() }
			.mapNotNull { file ->
				runCatching { CrashReportCodec.decode(file.readText(StandardCharsets.UTF_8)) }.getOrNull()
			}
	}

	@Synchronized
	override fun delete(id: String) {
		File(directory, "$id.crash").delete()
	}
}
