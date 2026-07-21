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
		if (!isValidDiagnosticId(report.id)) return
		directory.mkdirs()
		val target = File(directory, "${report.id}.crash")
		val temporary = File(directory, ".${report.id}.tmp")
		val payload = CrashReportCodec.encode(report)
		temporary.writeText(payload, StandardCharsets.UTF_8)
		if (!temporary.renameTo(target)) {
			target.writeText(payload, StandardCharsets.UTF_8)
			temporary.delete()
		}
	}

	@Synchronized
	override fun list(): List<CrashReport> {
		if (!directory.isDirectory) return emptyList()
		return directory
			.listFiles { file -> file.isFile && file.name.endsWith(".crash") }
			.orEmpty()
			.mapNotNull { file ->
				runCatching { CrashReportCodec.decode(file.readText(StandardCharsets.UTF_8)) }.getOrNull()
			}
			.sortedWith(
				compareByDescending<CrashReport> { it.timestampMillis }
					.thenBy { it.id },
			)
	}

	@Synchronized
	override fun delete(id: String) {
		if (!isValidDiagnosticId(id)) return
		File(directory, "$id.crash").delete()
	}

	@Synchronized
	override fun prune(olderThanTimestampMillis: Long, maxCount: Int) {
		require(maxCount > 0) { "maxCount must be positive" }
		if (!directory.isDirectory) return
		directory.listFiles { file -> file.isFile && file.name.endsWith(".tmp") }
			.orEmpty()
			.forEach(File::delete)
		val reports = directory
			.listFiles { file -> file.isFile && file.name.endsWith(".crash") }
			.orEmpty()
			.mapNotNull { file ->
				val report = runCatching {
					CrashReportCodec.decode(file.readText(StandardCharsets.UTF_8))
				}.getOrNull()
				if (report == null) {
					file.delete()
					null
				} else {
					file to report
				}
			}
			.sortedWith(
				compareByDescending<Pair<File, CrashReport>> { (_, report) -> report.timestampMillis }
					.thenBy { (_, report) -> report.id },
			)
		reports.forEachIndexed { index, (file, report) ->
			if (index >= maxCount || report.timestampMillis < olderThanTimestampMillis) file.delete()
		}
	}
}
