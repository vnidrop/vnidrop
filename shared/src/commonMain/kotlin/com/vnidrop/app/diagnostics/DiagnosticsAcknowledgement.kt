package com.vnidrop.app.diagnostics

private const val MaxDiagnosticsAcknowledgementBytes = 16 * 1024
private const val MaxDiagnosticsAcknowledgementDepth = 32

internal fun String.isSuccessfulDiagnosticsAcknowledgement(expectedId: String): Boolean {
	if (length > MaxDiagnosticsAcknowledgementBytes || encodeToByteArray().size > MaxDiagnosticsAcknowledgementBytes) return false
	return runCatching {
		DiagnosticsAcknowledgementParser(this).isSuccessful(expectedId.lowercase())
	}.getOrDefault(false)
}

private class DiagnosticsAcknowledgementParser(private val source: String) {
	private var index = 0

	fun isSuccessful(expectedId: String): Boolean {
		skipWhitespace()
		expect('{')
		skipWhitespace()
		val keys = mutableSetOf<String>()
		var ok: Boolean? = null
		var id: String? = null
		if (!consume('}')) {
			while (true) {
				val key = parseString()
				require(keys.add(key)) { "duplicate JSON object key" }
				skipWhitespace()
				expect(':')
				skipWhitespace()
				when (key) {
					"ok" -> ok = parseBoolean()
					"id" -> id = parseString()
					else -> skipValue(depth = 1)
				}
				skipWhitespace()
				when {
					consume('}') -> break
					consume(',') -> {
						skipWhitespace()
						require(peek() != '}') { "trailing JSON object comma" }
					}
					else -> error("expected JSON object separator")
				}
			}
		}
		skipWhitespace()
		require(index == source.length) { "unexpected data after JSON object" }
		return ok == true && id == expectedId
	}

	private fun skipValue(depth: Int) {
		require(depth <= MaxDiagnosticsAcknowledgementDepth) { "JSON nesting is too deep" }
		when (peek()) {
			'"' -> parseString()
			'{' -> skipObject(depth)
			'[' -> skipArray(depth)
			't' -> expectLiteral("true")
			'f' -> expectLiteral("false")
			'n' -> expectLiteral("null")
			'-' -> skipNumber()
			in '0'..'9' -> skipNumber()
			else -> error("invalid JSON value")
		}
	}

	private fun skipObject(depth: Int) {
		expect('{')
		skipWhitespace()
		if (consume('}')) return
		while (true) {
			parseString()
			skipWhitespace()
			expect(':')
			skipWhitespace()
			skipValue(depth + 1)
			skipWhitespace()
			when {
				consume('}') -> return
				consume(',') -> {
					skipWhitespace()
					require(peek() != '}') { "trailing JSON object comma" }
				}
				else -> error("expected JSON object separator")
			}
		}
	}

	private fun skipArray(depth: Int) {
		expect('[')
		skipWhitespace()
		if (consume(']')) return
		while (true) {
			skipValue(depth + 1)
			skipWhitespace()
			when {
				consume(']') -> return
				consume(',') -> {
					skipWhitespace()
					require(peek() != ']') { "trailing JSON array comma" }
				}
				else -> error("expected JSON array separator")
			}
		}
	}

	private fun parseBoolean(): Boolean = when {
		source.startsWith("true", index) -> {
			index += 4
			true
		}
		source.startsWith("false", index) -> {
			index += 5
			false
		}
		else -> error("expected JSON boolean")
	}

	private fun parseString(): String {
		expect('"')
		val value = StringBuilder()
		while (index < source.length) {
			val char = source[index++]
			when {
				char == '"' -> return value.toString()
				char == '\\' -> value.append(parseEscape())
				char.code < 0x20 -> error("unescaped JSON control character")
				else -> value.append(char)
			}
		}
		error("unterminated JSON string")
	}

	private fun parseEscape(): Char {
		require(index < source.length) { "unterminated JSON escape" }
		return when (val escaped = source[index++]) {
			'"', '\\', '/' -> escaped
			'b' -> '\b'
			'f' -> '\u000c'
			'n' -> '\n'
			'r' -> '\r'
			't' -> '\t'
			'u' -> parseUnicodeEscape()
			else -> error("invalid JSON escape")
		}
	}

	private fun parseUnicodeEscape(): Char {
		require(index + 4 <= source.length) { "incomplete JSON Unicode escape" }
		var value = 0
		repeat(4) {
			value = value * 16 + source[index++].digitToIntOrNull(16).let { digit ->
				requireNotNull(digit) { "invalid JSON Unicode escape" }
			}
		}
		return value.toChar()
	}

	private fun skipNumber() {
		consume('-')
		when (peek()) {
			'0' -> {
				index += 1
				require(peek() !in '0'..'9') { "leading zero in JSON number" }
			}
			in '1'..'9' -> while (peek() in '0'..'9') index += 1
			else -> error("invalid JSON number")
		}
		if (consume('.')) {
			require(peek() in '0'..'9') { "invalid JSON fraction" }
			while (peek() in '0'..'9') index += 1
		}
		if (peek() == 'e' || peek() == 'E') {
			index += 1
			if (peek() == '+' || peek() == '-') index += 1
			require(peek() in '0'..'9') { "invalid JSON exponent" }
			while (peek() in '0'..'9') index += 1
		}
	}

	private fun expectLiteral(literal: String) {
		require(source.startsWith(literal, index)) { "invalid JSON literal" }
		index += literal.length
	}

	private fun skipWhitespace() {
		while (peek() == ' ' || peek() == '\t' || peek() == '\r' || peek() == '\n') index += 1
	}

	private fun expect(expected: Char) {
		require(consume(expected)) { "expected '$expected'" }
	}

	private fun consume(expected: Char): Boolean {
		if (peek() != expected) return false
		index += 1
		return true
	}

	private fun peek(): Char? = source.getOrNull(index)
}
