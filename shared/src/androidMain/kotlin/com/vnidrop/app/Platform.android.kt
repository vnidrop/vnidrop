package com.vnidrop.app

import android.os.Build

class AndroidPlatform : Platform {
	override val name: String = "Android ${Build.VERSION.SDK_INT}"
	override val defaultCoreDataDir: String =
		System.getProperty("java.io.tmpdir") ?: "/data/local/tmp/vnidrop"
	override val defaultReceiveDir: String =
		System.getProperty("java.io.tmpdir") ?: "/data/local/tmp/vnidrop-receive"
}

actual fun getPlatform(): Platform = AndroidPlatform()
