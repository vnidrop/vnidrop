package com.vnidrop.app.core

import uniffi.vnidrop.SourceKind

// Desktop paths need no extra work, while Rust duplicates borrowed Android file
// descriptors immediately before the platform closes them.
internal expect suspend fun <T> withPlatformPathAccess(
	kind: SourceKind,
	value: String,
	block: suspend () -> T,
): T
