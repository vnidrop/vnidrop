package com.vnidrop.app.core

import kotlin.test.Test
import kotlin.test.assertEquals

class FilePickerJvmTest {
	@Test
	fun linuxUsesTheNativePortalPicker() {
		assertEquals(JvmFilePickerBackend.XdgPortal, jvmFilePickerBackend("Linux"))
		assertEquals(JvmFilePickerBackend.XdgPortal, jvmFilePickerBackend("linux"))
	}

	@Test
	fun otherDesktopPlatformsKeepTheirExistingPickers() {
		assertEquals(JvmFilePickerBackend.AwtSwing, jvmFilePickerBackend("Windows 11"))
		assertEquals(JvmFilePickerBackend.AwtSwing, jvmFilePickerBackend("Mac OS X"))
	}
}
