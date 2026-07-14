package com.vnidrop.app.diagnostics

internal const val MaxDiagnosticProperties = 12
internal const val MaxDiagnosticPropertyKeyBytes = 40
internal const val MaxDiagnosticPropertyValueBytes = 128
internal const val MaxDiagnosticNameBytes = 64

internal fun sanitizeDiagnosticsInstallId(value: String): String {
	val trimmed = value.trim()
	if (trimmed.any { it.code < 0x20 || it.code == 0x7f }) return ""
	return trimmed.takeUtf8Bytes(DiagnosticsJson.MaxInstallIdBytes)
}

internal fun sanitizeDiagnosticName(name: String): String =
	name.takeUtf8Bytes(MaxDiagnosticNameBytes)

internal fun sanitizeDiagnosticProperties(properties: Map<String, String>): Map<String, String> {
	val sanitized = LinkedHashMap<String, String>(minOf(properties.size, MaxDiagnosticProperties))
	for ((rawKey, rawValue) in properties) {
		val key = rawKey.takeUtf8Bytes(MaxDiagnosticPropertyKeyBytes)
		if (key.isEmpty() || key in sanitized) continue
		sanitized[key] = LogRedactor.redact(rawValue).takeUtf8Bytes(MaxDiagnosticPropertyValueBytes)
		if (sanitized.size == MaxDiagnosticProperties) break
	}
	return sanitized
}

internal fun String.takeUtf8Bytes(maxBytes: Int): String {
	require(maxBytes >= 0) { "maxBytes must not be negative" }
	val encoded = encodeToByteArray()
	if (encoded.size <= maxBytes) return this
	for (endIndex in maxBytes downTo (maxBytes - 3).coerceAtLeast(0)) {
		val decoded = runCatching {
			encoded.decodeToString(0, endIndex, throwOnInvalidSequence = true)
		}.getOrNull()
		if (decoded != null) return decoded
	}
	return ""
}
