package com.vnidrop.app.diagnostics

import com.vnidrop.app.logging.platformNowMillis

/**
 * Fixed-size ring of high-level app breadcrumbs for crash / bug context.
 * Always in-memory only; never auto-uploaded without policy + transport.
 *
 * Updates are best-effort under concurrency; losing a breadcrumb is preferable
 * to blocking a dying process on a lock.
 */
class BreadcrumbBuffer(
	private val capacity: Int = DefaultCapacity,
) {
	init {
		require(capacity > 0) { "capacity must be positive" }
	}

	@Volatile
	private var items: List<Breadcrumb> = emptyList()

	fun add(name: String, properties: Map<String, String> = emptyMap(), timestampMillis: Long = platformNowMillis()) {
		val crumb = Breadcrumb(
			name = name.take(MaxNameLength),
			timestampMillis = timestampMillis,
			properties = LogRedactor.redactMap(properties).mapValues { it.value.take(MaxPropertyValueLength) },
		)
		val current = items
		items = (current + crumb).takeLast(capacity)
	}

	fun snapshot(): List<Breadcrumb> = items

	fun clear() {
		items = emptyList()
	}

	companion object {
		const val DefaultCapacity = 40
		private const val MaxNameLength = 64
		private const val MaxPropertyValueLength = 128
	}
}
