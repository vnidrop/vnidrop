package com.vnidrop.app.feature.send

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class PreviewFileInfo(
	val transferId: ULong,
	val byteSize: Long,
	val modifiedAtMillis: Long,
)

interface PlatformPreviewStore {
	fun list(): List<PreviewFileInfo>
	fun read(transferId: ULong): ByteArray?
	fun writeAtomically(transferId: ULong, bytes: ByteArray): Boolean
	fun delete(transferId: ULong)
}

expect fun createPlatformPreviewStore(appDataDir: String): PlatformPreviewStore

interface FilePreviewRepository {
	val previews: StateFlow<Map<ULong, ByteArray>>
	suspend fun restore(activeTransferIds: Set<ULong>)
	suspend fun save(transferId: ULong, bytes: ByteArray)
	suspend fun remove(transferId: ULong)
}

data class PreviewStoragePolicy(
	val maxEntryBytes: Int = 512 * 1024,
	val maxTotalBytes: Long = 20L * 1024L * 1024L,
) {
	init {
		require(maxEntryBytes > 0)
		require(maxTotalBytes >= maxEntryBytes)
	}
}

class AppFilePreviewRepository(
	private val store: PlatformPreviewStore,
	private val policy: PreviewStoragePolicy = PreviewStoragePolicy(),
) : FilePreviewRepository {
	private val mutex = Mutex()
	private val _previews = MutableStateFlow<Map<ULong, ByteArray>>(emptyMap())
	override val previews: StateFlow<Map<ULong, ByteArray>> = _previews.asStateFlow()

	override suspend fun restore(activeTransferIds: Set<ULong>) = withContext(Dispatchers.Default) {
		mutex.withLock {
			val files = store.list()
			for (file in files) {
				if (file.transferId !in activeTransferIds || file.byteSize !in 1..policy.maxEntryBytes.toLong()) {
					store.delete(file.transferId)
				}
			}
			enforceQuota()
			_previews.value = store.list()
				.filter { it.transferId in activeTransferIds }
				.mapNotNull { file ->
					store.read(file.transferId)
						?.takeIf { it.isSupportedPreview() && it.size <= policy.maxEntryBytes }
						?.let { file.transferId to it }
						?: run {
							store.delete(file.transferId)
							null
						}
				}
				.toMap()
		}
	}

	override suspend fun save(transferId: ULong, bytes: ByteArray) = withContext(Dispatchers.Default) {
		mutex.withLock {
			if (bytes.size !in 1..policy.maxEntryBytes || !bytes.isSupportedPreview()) return@withLock
			if (!store.writeAtomically(transferId, bytes)) return@withLock
			enforceQuota(protectedTransferId = transferId)
			if (store.read(transferId) != null) {
				_previews.value = _previews.value + (transferId to bytes.copyOf())
			}
		}
	}

	override suspend fun remove(transferId: ULong) = withContext(Dispatchers.Default) {
		mutex.withLock {
			store.delete(transferId)
			_previews.value = _previews.value - transferId
		}
	}

	private fun enforceQuota(protectedTransferId: ULong? = null) {
		val files = store.list().sortedBy { it.modifiedAtMillis }
		var total = files.sumOf(PreviewFileInfo::byteSize)
		for (file in files) {
			if (total <= policy.maxTotalBytes) break
			if (file.transferId == protectedTransferId) continue
			store.delete(file.transferId)
			total -= file.byteSize
			_previews.value = _previews.value - file.transferId
		}
	}
}

private fun ByteArray.isSupportedPreview(): Boolean {
	val png = size >= 8 && this[0] == 0x89.toByte() && decodeToString(1, 4) == "PNG"
	val jpeg = size >= 3 && this[0] == 0xff.toByte() && this[1] == 0xd8.toByte() && this[2] == 0xff.toByte()
	val webp = size >= 12 && decodeToString(0, 4) == "RIFF" && decodeToString(8, 12) == "WEBP"
	return png || jpeg || webp
}
