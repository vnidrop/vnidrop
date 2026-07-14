package com.vnidrop.app.diagnostics

/**
 * Durable crash envelopes written during process death and read on next launch.
 * Encoding is a simple line-oriented format (no kotlinx.serialization dependency).
 */
interface PendingCrashStore {
	fun write(report: CrashReport)
	fun list(): List<CrashReport>
	fun delete(id: String)
}

expect fun createPendingCrashStore(appDataDir: String): PendingCrashStore

internal object CrashReportCodec {
	private const val FieldSep = "\u001f"
	private const val RecordSep = "\u001e"

	fun encode(report: CrashReport): String = buildString {
		fun field(key: String, value: String) {
			append(key)
			append('=')
			append(value.replace("\n", "\\n").replace("\r", "\\r"))
			append(FieldSep)
		}
		field("id", report.id)
		field("ts", report.timestampMillis.toString())
		field("install", report.installId)
		field("app", report.appVersion)
		field("platform", report.platform)
		field("type", report.exceptionType)
		field("message", report.exceptionMessage)
		field("stack", report.stackTrace)
		field("diag", if (report.diagnosticsEnabledAtCapture) "1" else "0")
		field("schema", report.schemaVersion.toString())
		field(
			"crumbs",
			report.breadcrumbs.joinToString(RecordSep) { crumb ->
				listOf(
					crumb.timestampMillis.toString(),
					crumb.name,
					crumb.properties.entries.joinToString(",") { "${it.key}:${it.value}" },
				).joinToString("|")
			},
		)
	}

	fun decode(raw: String): CrashReport? {
		if (raw.isBlank()) return null
		val map = linkedMapOf<String, String>()
		for (part in raw.split(FieldSep)) {
			if (part.isEmpty()) continue
			val eq = part.indexOf('=')
			if (eq <= 0) continue
			val key = part.substring(0, eq)
			val value = part.substring(eq + 1)
				.replace("\\n", "\n")
				.replace("\\r", "\r")
			map[key] = value
		}
		val id = map["id"] ?: return null
		val crumbs = map["crumbs"].orEmpty()
			.split(RecordSep)
			.filter { it.isNotBlank() }
			.mapNotNull { entry ->
				val pieces = entry.split('|', limit = 3)
				if (pieces.size < 2) return@mapNotNull null
				val ts = pieces[0].toLongOrNull() ?: return@mapNotNull null
				val name = pieces[1]
				val props = if (pieces.size > 2 && pieces[2].isNotBlank()) {
					pieces[2].split(',').mapNotNull { kv ->
						val colon = kv.indexOf(':')
						if (colon <= 0) null
						else kv.substring(0, colon) to kv.substring(colon + 1)
					}.toMap()
				} else {
					emptyMap()
				}
				Breadcrumb(name = name, timestampMillis = ts, properties = props)
			}
		return CrashReport(
			id = id,
			timestampMillis = map["ts"]?.toLongOrNull() ?: 0L,
			installId = map["install"].orEmpty(),
			appVersion = map["app"].orEmpty(),
			platform = map["platform"].orEmpty(),
			exceptionType = map["type"].orEmpty(),
			exceptionMessage = map["message"].orEmpty(),
			stackTrace = map["stack"].orEmpty(),
			breadcrumbs = crumbs,
			diagnosticsEnabledAtCapture = map["diag"] == "1",
			schemaVersion = map["schema"]?.toIntOrNull() ?: DiagnosticsSchemaVersion,
		)
	}
}
