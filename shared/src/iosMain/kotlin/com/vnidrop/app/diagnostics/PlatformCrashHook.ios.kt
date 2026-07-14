package com.vnidrop.app.diagnostics

import kotlin.experimental.ExperimentalNativeApi

@OptIn(ExperimentalNativeApi::class)
actual fun installPlatformCrashHook(onCrash: (Throwable) -> Unit) {
	val previous = setUnhandledExceptionHook { throwable ->
		runCatching { onCrash(throwable) }
		// Terminate like the default hook after capture.
		terminateWithUnhandledException(throwable)
	}
	// Keep a reference so the previous hook is not GC'd unused; we intentionally
	// replace the default with capture-then-terminate.
	@Suppress("UNUSED_VARIABLE")
	val ignored = previous
}
