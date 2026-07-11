package com.vnidrop.app

import com.sun.jna.Callback
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import com.sun.jna.Structure
import java.io.File

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

internal object MacOsShareSheet {
	private val objc: ObjCRuntime? by lazy {
		runCatching {
			NativeLibrary.getInstance("AppKit")
			Native.load("objc", ObjCRuntime::class.java)
		}.getOrNull()
	}
	private var retainedPicker: Pointer? = null
	private val systemLibrary: NativeLibrary? by lazy {
		runCatching { NativeLibrary.getInstance("System") }.getOrNull()
	}
	private val dispatch: DispatchRuntime? by lazy {
		runCatching { Native.load("System", DispatchRuntime::class.java) }.getOrNull()
	}

	fun share(file: File): Result<Unit> = runCatching {
		require(file.isFile) { "The invitation file could not be created" }
		var failure: Throwable? = null
		val runtime = dispatch ?: error("The macOS main queue is unavailable")
		// dispatch_get_main_queue() is a C macro on Darwin, so there is no
		// function for dlsym/JNA to resolve. The macro returns this exported
		// queue object directly.
		val queue = systemLibrary?.getGlobalVariableAddress("_dispatch_main_q")
			?: error("The macOS main queue is unavailable")
		runtime.dispatch_sync_f(queue, null, DispatchWork { failure = runCatching { show(file) }.exceptionOrNull() })
		failure?.let { throw it }
	}

	private fun show(file: File) {
		val runtime = objc ?: error("AppKit is unavailable")
		val applicationClass = runtime.objc_getClass("NSApplication") ?: error("NSApplication is unavailable")
		val application = runtime.objc_msgSend(applicationClass, runtime.sel_registerName("sharedApplication"))
			?: error("NSApplication could not be opened")
		val window = runtime.objc_msgSend(application, runtime.sel_registerName("keyWindow"))
			?: runtime.objc_msgSend(application, runtime.sel_registerName("mainWindow"))
			?: error("No active macOS window")
		val contentView = runtime.objc_msgSend(window, runtime.sel_registerName("contentView"))
			?: error("The active window has no content view")
		val path = nsString(runtime, file.absolutePath) ?: error("The invitation path is invalid")
		val urlClass = runtime.objc_getClass("NSURL") ?: error("NSURL is unavailable")
		val url = runtime.objc_msgSend(urlClass, runtime.sel_registerName("fileURLWithPath:"), path)
			?: error("The invitation URL could not be created")
		val arrayClass = runtime.objc_getClass("NSArray") ?: error("NSArray is unavailable")
		val items = runtime.objc_msgSend(arrayClass, runtime.sel_registerName("arrayWithObject:"), url)
			?: error("The share item could not be created")
		val pickerClass = runtime.objc_getClass("NSSharingServicePicker") ?: error("The macOS share sheet is unavailable")
		val allocated = runtime.objc_msgSend(pickerClass, runtime.sel_registerName("alloc"))
			?: error("The macOS share sheet could not be allocated")
		val picker = runtime.objc_msgSend(allocated, runtime.sel_registerName("initWithItems:"), items)
			?: error("The macOS share sheet could not be created")
		retainedPicker?.let { runtime.objc_msgSend(it, runtime.sel_registerName("release")) }
		retainedPicker = picker
		runtime.objc_msgSend(
			picker,
			runtime.sel_registerName("showRelativeToRect:ofView:preferredEdge:"),
			anchorRect(),
			contentView,
			3L,
		)
	}

	private fun nsString(runtime: ObjCRuntime, value: String): Pointer? {
		val stringClass = runtime.objc_getClass("NSString") ?: return null
		return runtime.objc_msgSend(stringClass, runtime.sel_registerName("stringWithUTF8String:"), value)
	}

	internal fun validateNativeRectMapping(): Int = anchorRect().size()
	internal fun hasNativeMainQueue(): Boolean =
		runCatching { systemLibrary?.getGlobalVariableAddress("_dispatch_main_q") != null }.getOrDefault(false)

	private fun anchorRect() = NSRectByValue().apply {
		x = 0.0
		y = 0.0
		width = 1.0
		height = 1.0
		write()
	}
}

@Structure.FieldOrder("x", "y", "width", "height")
internal class NSRectByValue : Structure(), Structure.ByValue {
	@JvmField var x: Double = 0.0
	@JvmField var y: Double = 0.0
	@JvmField var width: Double = 0.0
	@JvmField var height: Double = 0.0
}

private interface ObjCRuntime : Library {
	fun objc_getClass(name: String): Pointer?
	fun sel_registerName(name: String): Pointer
	fun objc_msgSend(receiver: Pointer?, selector: Pointer?): Pointer?
	fun objc_msgSend(receiver: Pointer?, selector: Pointer?, argument: Pointer?): Pointer?
	fun objc_msgSend(receiver: Pointer?, selector: Pointer?, argument: String): Pointer?
	fun objc_msgSend(
		receiver: Pointer?,
		selector: Pointer?,
		rect: NSRectByValue,
		view: Pointer?,
		edge: Long,
	): Pointer?
}

private fun interface DispatchWork : Callback {
	fun invoke(context: Pointer?)
}

private interface DispatchRuntime : Library {
	fun dispatch_sync_f(queue: Pointer?, context: Pointer?, work: DispatchWork)
}
