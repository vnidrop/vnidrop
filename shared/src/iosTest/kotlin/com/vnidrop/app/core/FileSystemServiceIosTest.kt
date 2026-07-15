package com.vnidrop.app.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import uniffi.vnidrop.SourceKind

class FileSystemServiceIosTest {
	@Test
	fun sandboxPickerCopyMapsToPathSource() {
		val picked = PickedShareFile(
			value = "/tmp/VniDrop/photos",
			displayName = "photos",
			isTemporaryCopy = true,
			isDirectory = true,
		)

		val source = picked.toIosShareSource()

		assertEquals(SourceKind.PATH, source.kind)
		assertEquals(picked.value, source.value)
		assertEquals(picked.displayName, source.displayName)
		assertTrue(source.isDirectory)
	}
}
