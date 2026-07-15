package com.vnidrop.app.diagnostics

import com.vnidrop.app.logging.platformNowMillis
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Fixed-size ring of high-level app breadcrumbs for crash / bug context.
 * Always in-memory only; never auto-uploaded without policy + transport.
 *
 * Updates are best-effort under concurrency; losing a breadcrumb is preferable
 * to blocking a dying process on a lock.
 */
@OptIn(ExperimentalAtomicApi::class)
class BreadcrumbBuffer(
	private val capacity: Int = DefaultCapacity,
) {
	init {
		require(capacity > 0) { "capacity must be positive" }
	}

	private val items = AtomicReference<List<Breadcrumb>>(emptyList())

	fun add(name: String, properties: Map<String, String> = emptyMap(), timestampMillis: Long = platformNowMillis()) {
		val sanitizedName = sanitizeDiagnosticName(name)
		if (sanitizedName.isBlank()) return
		val crumb = Breadcrumb(
			name = sanitizedName,
			timestampMillis = timestampMillis,
			properties = sanitizeDiagnosticProperties(properties),
		)
		while (true) {
			val current = items.load()
			if (items.compareAndSet(current, (current + crumb).takeLast(capacity))) return
		}
	}

	fun snapshot(): List<Breadcrumb> = items.load()

	fun clear() {
		items.store(emptyList())
	}

	companion object {
		const val DefaultCapacity = 40
	}
}
