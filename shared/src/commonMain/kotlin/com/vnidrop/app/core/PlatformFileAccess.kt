package com.vnidrop.app.core

import uniffi.vnidrop.SourceKind

// Platform file handles have different lifetime rules. Desktop paths need no
// extra work, Android fd sources are duplicated immediately by Rust, and iOS
// security-scoped URLs must remain leased while Rust performs the blocking
// import/export call.
internal expect suspend fun <T> withPlatformPathAccess(
	kind: SourceKind,
	value: String,
	block: suspend () -> T,
): T
