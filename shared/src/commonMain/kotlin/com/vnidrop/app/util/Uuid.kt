package com.vnidrop.app.util

import kotlin.random.Random

/** RFC 4122 version-4 UUID string without depending on java.util.UUID in commonMain. */
fun randomUuidString(random: Random = Random.Default): String {
	val bytes = ByteArray(16)
	random.nextBytes(bytes)
	bytes[6] = ((bytes[6].toInt() and 0x0f) or 0x40).toByte()
	bytes[8] = ((bytes[8].toInt() and 0x3f) or 0x80).toByte()
	return buildString(36) {
		bytes.forEachIndexed { index, byte ->
			if (index == 4 || index == 6 || index == 8 || index == 10) append('-')
			append(HEX[(byte.toInt() ushr 4) and 0x0f])
			append(HEX[byte.toInt() and 0x0f])
		}
	}
}

private val HEX = "0123456789abcdef".toCharArray()
