package com.vnidrop.app.feature.send

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FilePreviewRepositoryTest {
	@Test
	fun preview_survives_repository_recreation() = runTest {
		val store = MemoryPreviewStore()
		AppFilePreviewRepository(store).save(42UL, png(12))

		val restored = AppFilePreviewRepository(store)
		restored.restore(setOf(42UL))

		assertContentEquals(png(12), restored.previews.value.getValue(42UL))
	}

	@Test
	fun restore_removes_orphans_and_corrupt_entries() = runTest {
		val store = MemoryPreviewStore()
		store.writeAtomically(1UL, png(12))
		store.writeAtomically(2UL, "not an image".encodeToByteArray())
		store.writeAtomically(3UL, png(12))

		val repository = AppFilePreviewRepository(store)
		repository.restore(setOf(1UL, 2UL))

		assertEquals(setOf(1UL), repository.previews.value.keys)
		assertFalse(store.entries.containsKey(2UL))
		assertFalse(store.entries.containsKey(3UL))
	}

	@Test
	fun entry_and_total_limits_are_enforced() = runTest {
		val store = MemoryPreviewStore()
		val repository = AppFilePreviewRepository(store, PreviewStoragePolicy(maxEntryBytes = 20, maxTotalBytes = 32))

		repository.save(1UL, png(17))
		store.clock += 1
		repository.save(2UL, png(17))
		store.clock += 1
		repository.save(3UL, png(21))

		assertEquals(setOf(2UL), repository.previews.value.keys)
		assertTrue(3UL !in store.entries)
	}

	private fun png(size: Int): ByteArray = ByteArray(size.coerceAtLeast(8)).also {
		it[0] = 0x89.toByte(); it[1] = 'P'.code.toByte(); it[2] = 'N'.code.toByte(); it[3] = 'G'.code.toByte()
	}
}

private class MemoryPreviewStore : PlatformPreviewStore {
	data class Entry(val bytes: ByteArray, val modified: Long)
	val entries = mutableMapOf<ULong, Entry>()
	var clock = 1L
	override fun list() = entries.map { (id, entry) -> PreviewFileInfo(id, entry.bytes.size.toLong(), entry.modified) }
	override fun read(transferId: ULong) = entries[transferId]?.bytes?.copyOf()
	override fun writeAtomically(transferId: ULong, bytes: ByteArray): Boolean {
		if (transferId !in entries) entries[transferId] = Entry(bytes.copyOf(), clock)
		return true
	}
	override fun delete(transferId: ULong) { entries.remove(transferId) }
}
