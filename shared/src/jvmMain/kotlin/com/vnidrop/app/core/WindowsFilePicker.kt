package com.vnidrop.app.core

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.platform.win32.COM.COMUtils
import com.sun.jna.platform.win32.COM.Unknown
import com.sun.jna.platform.win32.Guid.GUID
import com.sun.jna.platform.win32.Ole32
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinNT.HRESULT
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import java.awt.Frame
import java.io.File

internal enum class WindowsFilePickerMode {
	Files,
	Folder,
}

internal const val FOS_NOCHANGEDIR = 0x00000008
internal const val FOS_PICKFOLDERS = 0x00000020
internal const val FOS_FORCEFILESYSTEM = 0x00000040
internal const val FOS_ALLOWMULTISELECT = 0x00000200
internal const val FOS_PATHMUSTEXIST = 0x00000800
internal const val FOS_FILEMUSTEXIST = 0x00001000

internal fun windowsFilePickerOptions(mode: WindowsFilePickerMode): Int =
	FOS_NOCHANGEDIR or FOS_FORCEFILESYSTEM or FOS_PATHMUSTEXIST or when (mode) {
		WindowsFilePickerMode.Files -> FOS_ALLOWMULTISELECT or FOS_FILEMUSTEXIST
		WindowsFilePickerMode.Folder -> FOS_PICKFOLDERS
	}

internal fun pickWindowsFiles(owner: Frame?): List<PickedShareFile> =
	showWindowsFilePicker(
		title = "Select files to share",
		mode = WindowsFilePickerMode.Files,
		owner = owner,
	).map { it.toPickedShareFile(isDirectory = false) }

internal fun pickWindowsFolder(title: String, owner: Frame?): File? =
	showWindowsFilePicker(title, WindowsFilePickerMode.Folder, owner).singleOrNull()

private fun showWindowsFilePicker(
	title: String,
	mode: WindowsFilePickerMode,
	owner: Frame?,
): List<File> {
	val ole32 = Ole32.INSTANCE
	val initialization = ole32.CoInitializeEx(Pointer.NULL, Ole32.COINIT_APARTMENTTHREADED)
	COMUtils.checkRC(initialization)
	try {
		val dialogReference = PointerByReference()
		COMUtils.checkRC(
			ole32.CoCreateInstance(
				GUID(CLSID_FILE_OPEN_DIALOG),
				Pointer.NULL,
				CLSCTX_INPROC_SERVER,
				GUID(IID_FILE_OPEN_DIALOG),
				dialogReference,
			),
		)
		val dialog = FileOpenDialog(dialogReference.value)
		try {
			val existingOptions = IntByReference()
			COMUtils.checkRC(dialog.getOptions(existingOptions))
			COMUtils.checkRC(dialog.setOptions(existingOptions.value or windowsFilePickerOptions(mode)))
			COMUtils.checkRC(dialog.setTitle(WString(title)))

			val ownerHandle = owner
				?.takeIf { it.isDisplayable }
				?.let { HWND(Native.getWindowPointer(it)) }
			val showResult = dialog.show(ownerHandle)
			if (showResult.toInt() == HRESULT_CANCELLED) return emptyList()
			COMUtils.checkRC(showResult)
			return dialog.results(ole32)
		} finally {
			dialog.Release()
		}
	} finally {
		ole32.CoUninitialize()
	}
}

private class FileOpenDialog(pointer: Pointer) : Unknown(pointer) {
	fun show(owner: HWND?): HRESULT = invokeHResult(3, owner)

	fun setOptions(options: Int): HRESULT = invokeHResult(9, options)

	fun getOptions(options: IntByReference): HRESULT = invokeHResult(10, options)

	fun setTitle(title: WString): HRESULT = invokeHResult(17, title)

	private fun getResults(results: PointerByReference): HRESULT = invokeHResult(27, results)

	fun results(ole32: Ole32): List<File> {
		val resultsReference = PointerByReference()
		COMUtils.checkRC(getResults(resultsReference))
		val results = ShellItemArray(resultsReference.value)
		try {
			val count = IntByReference()
			COMUtils.checkRC(results.getCount(count))
			return List(count.value) { index -> results.fileAt(index, ole32) }
		} finally {
			results.Release()
		}
	}

	private fun invokeHResult(index: Int, vararg arguments: Any?): HRESULT =
		_invokeNativeObject(index, arrayOf(pointer, *arguments), HRESULT::class.java) as HRESULT
}

private class ShellItemArray(pointer: Pointer) : Unknown(pointer) {
	fun getCount(count: IntByReference): HRESULT = invokeHResult(7, count)

	private fun getItemAt(index: Int, item: PointerByReference): HRESULT = invokeHResult(8, index, item)

	fun fileAt(index: Int, ole32: Ole32): File {
		val itemReference = PointerByReference()
		COMUtils.checkRC(getItemAt(index, itemReference))
		val item = ShellItem(itemReference.value)
		try {
			return item.file(ole32)
		} finally {
			item.Release()
		}
	}

	private fun invokeHResult(index: Int, vararg arguments: Any?): HRESULT =
		_invokeNativeObject(index, arrayOf(pointer, *arguments), HRESULT::class.java) as HRESULT
}

private class ShellItem(pointer: Pointer) : Unknown(pointer) {
	private fun getDisplayName(name: PointerByReference): HRESULT =
		invokeHResult(5, SIGDN_FILESYSPATH, name)

	fun file(ole32: Ole32): File {
		val nameReference = PointerByReference()
		COMUtils.checkRC(getDisplayName(nameReference))
		val name = nameReference.value
		try {
			return File(name.getWideString(0))
		} finally {
			ole32.CoTaskMemFree(name)
		}
	}

	private fun invokeHResult(index: Int, vararg arguments: Any?): HRESULT =
		_invokeNativeObject(index, arrayOf(pointer, *arguments), HRESULT::class.java) as HRESULT
}

private const val CLSCTX_INPROC_SERVER = 0x1
private const val HRESULT_CANCELLED = 0x800704C7.toInt()
private const val SIGDN_FILESYSPATH = 0x80058000.toInt()
private const val CLSID_FILE_OPEN_DIALOG = "{DC1C5A9C-E88A-4DDE-A5A1-60F82A20AEF7}"
private const val IID_FILE_OPEN_DIALOG = "{D57C7288-D4AD-4768-BE02-9D969532D960}"
