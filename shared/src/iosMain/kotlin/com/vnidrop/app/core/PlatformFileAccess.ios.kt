package com.vnidrop.app.core

import platform.Foundation.NSURL
import uniffi.vnidrop.SourceKind

internal actual suspend fun <T> withPlatformPathAccess(
	kind: SourceKind,
	value: String,
	block: suspend () -> T,
): T {
	if (kind != SourceKind.IOS_SECURITY_SCOPED_URL) {
		return block()
	}

	val url = NSURL.URLWithString(value) ?: NSURL.fileURLWithPath(value)
	val didStartAccess = url.startAccessingSecurityScopedResource()
	return try {
		block()
	} finally {
		if (didStartAccess) {
			url.stopAccessingSecurityScopedResource()
		}
	}
}
