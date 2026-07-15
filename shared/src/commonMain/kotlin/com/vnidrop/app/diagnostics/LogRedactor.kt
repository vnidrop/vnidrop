package com.vnidrop.app.diagnostics

/**
 * Scrubs known-sensitive patterns from free-form diagnostics text before upload
 * or attachment. Defense in depth for tickets, endpoint-like ids, and paths.
 */
object LogRedactor {
	fun redact(input: String): String {
		if (input.isEmpty()) return input
		var result = input
		for (rule in Rules) {
			result = rule.regex.replace(result, rule.replacement)
		}
		return result
	}

	fun redactMap(fields: Map<String, String>): Map<String, String> =
		fields.mapValues { (_, value) -> redact(value) }

	private data class Rule(val regex: Regex, val replacement: String)

	private val Rules = listOf(
		// Blob tickets / long base64-ish tokens often appear near "ticket=".
		Rule(
			Regex("""(?i)(ticket\s*[=:]\s*)([A-Za-z0-9+/=_\-]{24,})"""),
			"$1[redacted-ticket]",
		),
		// iroh-style node/endpoint ids: long hex or base32-ish.
		Rule(
			Regex("""(?i)(endpoint[_-]?id\s*[=:]\s*)([A-Za-z0-9+/=_\-]{16,})"""),
			"$1[redacted-endpoint]",
		),
		Rule(
			Regex("""\b[0-9a-fA-F]{48,}\b"""),
			"[redacted-hex]",
		),
		// Absolute filesystem paths (Unix + Windows drive).
		Rule(
			Regex("""(?<![A-Za-z0-9_])(/[^\s:]+|[A-Za-z]:\\[^\s]+)"""),
			"[redacted-path]",
		),
		// content:// and file:// URIs.
		Rule(
			Regex("""(?i)\b((?:content|file|http|https)://[^\s]+)"""),
			"[redacted-uri]",
		),
		// SAF tree/document ids.
		Rule(
			Regex("""(?i)(document[_-]?id|tree[_-]?uri)\s*[=:]\s*\S+"""),
			"$1=[redacted]",
		),
	)
}
