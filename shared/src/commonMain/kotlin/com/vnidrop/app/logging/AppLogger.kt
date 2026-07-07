package com.vnidrop.app.logging

data class LogRotationPolicy(
	val maxBytes: Long = 1_048_576,
	val maxFiles: Int = 5,
) {
	init {
		require(maxBytes > 0) { "maxBytes must be positive" }
		require(maxFiles >= 0) { "maxFiles must be zero or positive" }
	}

	fun shouldRotate(currentBytes: Long, incomingBytes: Long): Boolean =
		currentBytes > 0 && currentBytes + incomingBytes > maxBytes
}

enum class AppLogLevel {
	Debug,
	Info,
	Warn,
	Error,
}

data class LogFileInfo(
	val name: String,
	val path: String,
	val sizeBytes: Long,
	val modifiedAtMillis: Long,
)

interface PlatformLogStore {
	val logDirectory: String
	fun append(line: String)
	fun listLogFiles(): List<LogFileInfo>
}

expect fun createPlatformLogStore(appDataDir: String, policy: LogRotationPolicy): PlatformLogStore

expect fun platformNowMillis(): Long

object AppLogger {
	private var store: PlatformLogStore? = null
	private var activeDirectory: String? = null

	val logDirectory: String?
		get() = store?.logDirectory

	fun initialize(appDataDir: String, policy: LogRotationPolicy = LogRotationPolicy()) {
		if (activeDirectory == appDataDir && store != null) return
		store = createPlatformLogStore(appDataDir, policy)
		activeDirectory = appDataDir
		info("logging", "app logger initialized", mapOf("directory" to (store?.logDirectory ?: "")))
	}

	fun debug(scope: String, message: String, fields: Map<String, String> = emptyMap()) =
		write(AppLogLevel.Debug, scope, message, fields)

	fun info(scope: String, message: String, fields: Map<String, String> = emptyMap()) =
		write(AppLogLevel.Info, scope, message, fields)

	fun warn(scope: String, message: String, fields: Map<String, String> = emptyMap()) =
		write(AppLogLevel.Warn, scope, message, fields)

	fun error(scope: String, message: String, throwable: Throwable? = null, fields: Map<String, String> = emptyMap()) {
		val allFields = if (throwable == null) {
			fields
		} else {
			fields + ("error" to (throwable.message ?: throwable.toString()))
		}
		write(AppLogLevel.Error, scope, message, allFields)
	}

	fun listLogFiles(): List<LogFileInfo> =
		store?.listLogFiles().orEmpty()

	private fun write(level: AppLogLevel, scope: String, message: String, fields: Map<String, String>) {
		val line = buildString {
			append(platformNowMillis())
			append(" ")
			append(level.name.uppercase())
			append(" [")
			append(scope)
			append("] ")
			append(message)
			if (fields.isNotEmpty()) {
				append(" ")
				append(fields.entries.joinToString(" ") { (key, value) -> "$key=${value.sanitizeLogValue()}" })
			}
			append("\n")
		}
		store?.append(line)
	}
}

private fun String.sanitizeLogValue(): String =
	replace('\n', ' ').replace('\r', ' ')
