package com.vnidrop.app.diagnostics

/**
 * Durable crash envelopes written during process death and read on next launch.
 * Encoding is a simple line-oriented format (no kotlinx.serialization dependency).
 */
interface PendingCrashStore {
	fun write(report: CrashReport)
	fun list(): List<CrashReport>
	fun delete(id: String)
	fun prune(olderThanTimestampMillis: Long, maxCount: Int)
}

expect fun createPendingCrashStore(appDataDir: String): PendingCrashStore

internal object CrashReportCodec {
	private const val FieldSep = "\u001f"
	private const val RecordSep = "\u001e"
	private const val Version2Prefix = "vnidrop-crash-v2\n"
	private const val MaxEncodedChars = 512 * 1024

	fun encode(report: CrashReport): String = buildString {
		fun field(key: String, value: String) {
			append(key)
			append('=')
			append(value.hexEncode())
			append('\n')
		}
		append(Version2Prefix)
		field("id", report.id)
		field("ts", report.timestampMillis.toString())
		field("install", report.installId)
		field("app", report.appVersion)
		field("platform", report.platform)
		field("type", report.exceptionType)
		field("message", report.exceptionMessage)
		field("stack", report.stackTrace)
		field(
			"diag",
			when (report.diagnosticsEnabledAtCapture) {
				true -> "1"
				false -> "0"
				null -> "u"
			},
		)
		field("schema", report.schemaVersion.toString())
		val breadcrumbs = report.breadcrumbs.take(40)
		field("crumb.count", breadcrumbs.size.toString())
		breadcrumbs.forEachIndexed { crumbIndex, crumb ->
			field("crumb.$crumbIndex.ts", crumb.timestampMillis.toString())
			field("crumb.$crumbIndex.name", crumb.name)
			val properties = crumb.properties.entries.take(MaxDiagnosticProperties)
			field("crumb.$crumbIndex.prop.count", properties.size.toString())
			properties.forEachIndexed { propertyIndex, (key, value) ->
				field("crumb.$crumbIndex.prop.$propertyIndex.key", key)
				field("crumb.$crumbIndex.prop.$propertyIndex.value", value)
			}
		}
	}

	fun decode(raw: String): CrashReport? {
		if (raw.isBlank() || raw.length > MaxEncodedChars) return null
		return if (raw.startsWith(Version2Prefix)) decodeVersion2(raw) else decodeLegacy(raw)
	}

	private fun decodeVersion2(raw: String): CrashReport? {
		val map = linkedMapOf<String, String>()
		for (part in raw.removePrefix(Version2Prefix).lineSequence()) {
			if (part.isEmpty()) continue
			val eq = part.indexOf('=')
			if (eq <= 0) continue
			val key = part.substring(0, eq)
			val value = part.substring(eq + 1).hexDecode() ?: return null
			map[key] = value
		}
		val crumbCount = map["crumb.count"]?.toIntOrNull()?.takeIf { it in 0..40 } ?: return null
		val crumbs = buildList {
			repeat(crumbCount) { crumbIndex ->
				val timestamp = map["crumb.$crumbIndex.ts"]
					?.toLongOrNull()
					?.takeIf { it >= 0 }
					?: return null
				val name = map["crumb.$crumbIndex.name"]?.takeIf { it.isNotBlank() } ?: return null
				val propertyCount = map["crumb.$crumbIndex.prop.count"]
					?.toIntOrNull()
					?.takeIf { it in 0..MaxDiagnosticProperties }
					?: return null
				val properties = buildMap {
					repeat(propertyCount) { propertyIndex ->
						val key = map["crumb.$crumbIndex.prop.$propertyIndex.key"] ?: return null
						val value = map["crumb.$crumbIndex.prop.$propertyIndex.value"] ?: return null
						put(key, value)
					}
				}
				add(Breadcrumb(name = name, timestampMillis = timestamp, properties = properties))
			}
		}
		return reportFromFields(map, crumbs)
	}

	private fun decodeLegacy(raw: String): CrashReport? {
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
		val crumbs = map["crumbs"].orEmpty()
			.split(RecordSep)
			.filter { it.isNotBlank() }
			.mapNotNull { entry ->
				val pieces = entry.split('|', limit = 3)
				if (pieces.size < 2) return@mapNotNull null
				val ts = pieces[0].toLongOrNull()?.takeIf { it >= 0 } ?: return@mapNotNull null
				val name = pieces[1].takeIf { it.isNotBlank() } ?: return@mapNotNull null
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
		return reportFromFields(map, crumbs)
	}

	private fun reportFromFields(
		map: Map<String, String>,
		crumbs: List<Breadcrumb>,
	): CrashReport? {
		val id = map["id"]?.takeIf(::isValidDiagnosticId) ?: return null
		val timestamp = map["ts"]?.toLongOrNull()?.takeIf { it >= 0 } ?: return null
		val exceptionType = map["type"]?.takeIf { it.isNotBlank() } ?: return null
		val schemaVersion = map["schema"]?.toIntOrNull()
			?.takeIf { it == DiagnosticsSchemaVersion }
			?: return null
		val diagnosticsEnabled: Boolean? = when (map["diag"]) {
			"1" -> true
			"0" -> false
			"u" -> null
			else -> return null
		}
		return CrashReport(
			id = id,
			timestampMillis = timestamp,
			installId = map["install"].orEmpty(),
			appVersion = map["app"].orEmpty(),
			platform = map["platform"].orEmpty(),
			exceptionType = exceptionType,
			exceptionMessage = map["message"].orEmpty(),
			stackTrace = map["stack"].orEmpty(),
			breadcrumbs = crumbs,
			diagnosticsEnabledAtCapture = diagnosticsEnabled,
			schemaVersion = schemaVersion,
		)
	}
}

internal fun isValidDiagnosticId(id: String): Boolean =
	DiagnosticIdPattern.matches(id)

private val DiagnosticIdPattern =
	Regex("^[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}$")

private fun String.hexEncode(): String {
	val digits = "0123456789abcdef"
	return buildString(length * 2) {
		for (byte in this@hexEncode.encodeToByteArray()) {
			val value = byte.toInt() and 0xff
			append(digits[value ushr 4])
			append(digits[value and 0x0f])
		}
	}
}

private fun String.hexDecode(): String? {
	if (length % 2 != 0) return null
	val bytes = ByteArray(length / 2)
	for (index in bytes.indices) {
		val high = this[index * 2].digitToIntOrNull(16) ?: return null
		val low = this[index * 2 + 1].digitToIntOrNull(16) ?: return null
		bytes[index] = ((high shl 4) or low).toByte()
	}
	return runCatching { bytes.decodeToString(throwOnInvalidSequence = true) }.getOrNull()
}
