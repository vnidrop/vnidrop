package com.vnidrop.app.feature.send

import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals

class PlatformPreviewStoreTest {
	@Test
	fun preview_is_restored_from_private_application_storage() = runTest {
		val directory = Files.createTempDirectory("vnidrop-preview-test")
		try {
			val bytes = ByteArray(16).also {
				it[0] = 0x89.toByte(); it[1] = 'P'.code.toByte(); it[2] = 'N'.code.toByte(); it[3] = 'G'.code.toByte()
			}
			AppFilePreviewRepository(createPlatformPreviewStore(directory.toString())).save(91UL, bytes)

			val restarted = AppFilePreviewRepository(createPlatformPreviewStore(directory.toString()))
			restarted.restore(setOf(91UL))

			assertContentEquals(bytes, restarted.previews.value.getValue(91UL))
		} finally {
			directory.toFile().deleteRecursively()
		}
	}
}
