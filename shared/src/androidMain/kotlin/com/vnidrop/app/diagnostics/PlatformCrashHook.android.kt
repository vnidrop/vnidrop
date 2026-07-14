package com.vnidrop.app.diagnostics

actual fun installPlatformCrashHook(onCrash: (Throwable) -> Unit) {
	val previous = Thread.getDefaultUncaughtExceptionHandler()
	Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
		runCatching { onCrash(throwable) }
		previous?.uncaughtException(thread, throwable)
	}
}
