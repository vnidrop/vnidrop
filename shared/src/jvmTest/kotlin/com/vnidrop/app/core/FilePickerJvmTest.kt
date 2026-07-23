package com.vnidrop.app.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FilePickerJvmTest {
	@Test
	fun linuxUsesTheNativePortalPicker() {
		assertEquals(JvmFilePickerBackend.XdgPortal, jvmFilePickerBackend("Linux"))
		assertEquals(JvmFilePickerBackend.XdgPortal, jvmFilePickerBackend("linux"))
	}

	@Test
	fun windowsUsesTheModernNativePicker() {
		assertEquals(JvmFilePickerBackend.WindowsNative, jvmFilePickerBackend("Windows 11"))
		assertEquals(JvmFilePickerBackend.WindowsNative, jvmFilePickerBackend("windows 10"))
	}

	@Test
	fun otherDesktopPlatformsKeepTheirExistingPickers() {
		assertEquals(JvmFilePickerBackend.AwtSwing, jvmFilePickerBackend("Mac OS X"))
	}

	@Test
	fun windowsFilePickerAllowsMultipleFilesystemFiles() {
		val options = windowsFilePickerOptions(WindowsFilePickerMode.Files)

		assertTrue(options and FOS_FORCEFILESYSTEM != 0)
		assertTrue(options and FOS_ALLOWMULTISELECT != 0)
		assertTrue(options and FOS_FILEMUSTEXIST != 0)
		assertFalse(options and FOS_PICKFOLDERS != 0)
	}

	@Test
	fun windowsFolderPickerSelectsOneFilesystemFolder() {
		val options = windowsFilePickerOptions(WindowsFilePickerMode.Folder)

		assertTrue(options and FOS_FORCEFILESYSTEM != 0)
		assertTrue(options and FOS_PICKFOLDERS != 0)
		assertTrue(options and FOS_PATHMUSTEXIST != 0)
		assertFalse(options and FOS_ALLOWMULTISELECT != 0)
	}
}
