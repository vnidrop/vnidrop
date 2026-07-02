package com.vnidrop.app.core

import uniffi.vnidrop.SourceKind

internal actual suspend fun <T> withPlatformPathAccess(
	kind: SourceKind,
	value: String,
	block: suspend () -> T,
): T = block()
