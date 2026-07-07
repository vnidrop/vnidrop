package com.vnidrop.app

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer

internal object MacOsAppKitAppearance {
	private val objc: ObjCRuntime? by lazy {
		runCatching {
			NativeLibrary.getInstance("AppKit")
			Native.load("objc", ObjCRuntime::class.java)
		}.getOrNull()
	}

	fun apply(isDarkTheme: Boolean) {
		if (!isMacOs()) return
		runCatching {
			val runtime = objc ?: return
			val applicationClass = runtime.objc_getClass("NSApplication") ?: return
			val appearanceClass = runtime.objc_getClass("NSAppearance") ?: return
			val application = runtime.objc_msgSend(applicationClass, runtime.sel_registerName("sharedApplication")) ?: return
			val appearanceName = nsString(runtime, macOsAppearanceName(isDarkTheme)) ?: return
			val appearance = runtime.objc_msgSend(
				appearanceClass,
				runtime.sel_registerName("appearanceNamed:"),
				appearanceName,
			) ?: return
			runtime.objc_msgSend(application, runtime.sel_registerName("setAppearance:"), appearance)
		}
	}

	private fun macOsAppearanceName(isDarkTheme: Boolean): String =
		if (isDarkTheme) "NSAppearanceNameDarkAqua" else "NSAppearanceNameAqua"

	private fun nsString(runtime: ObjCRuntime, value: String): Pointer? {
		val stringClass = runtime.objc_getClass("NSString") ?: return null
		return runtime.objc_msgSend(stringClass, runtime.sel_registerName("stringWithUTF8String:"), value)
	}

	private fun isMacOs(): Boolean =
		System.getProperty("os.name").startsWith("Mac", ignoreCase = true)
}

private interface ObjCRuntime : Library {
	fun objc_getClass(name: String): Pointer?
	fun sel_registerName(name: String): Pointer
	fun objc_msgSend(receiver: Pointer?, selector: Pointer?): Pointer?
	fun objc_msgSend(receiver: Pointer?, selector: Pointer?, argument: Pointer?): Pointer?
	fun objc_msgSend(receiver: Pointer?, selector: Pointer?, argument: String): Pointer?
}
