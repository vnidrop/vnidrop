package com.vnidrop.app.feature.send

import java.io.File

actual fun createPlatformPreviewStore(appDataDir: String): PlatformPreviewStore = JvmLikePreviewStore(File(appDataDir, "ui/previews"))

private class JvmLikePreviewStore(private val directory: File) : PlatformPreviewStore {
	override fun list(): List<PreviewFileInfo> = directory.listFiles().orEmpty().mapNotNull { file ->
		file.name.removeSuffix(".preview").toULongOrNull()?.let { PreviewFileInfo(it, file.length(), file.lastModified()) }
	}
	override fun read(transferId: ULong): ByteArray? = runCatching { file(transferId).takeIf(File::isFile)?.readBytes() }.getOrNull()
	override fun writeAtomically(transferId: ULong, bytes: ByteArray): Boolean = runCatching {
		directory.mkdirs()
		val target = file(transferId)
		if (target.isFile) return@runCatching true
		val temporary = File(directory, ".${target.name}.tmp")
		temporary.writeBytes(bytes)
		temporary.renameTo(target).also { if (!it) temporary.delete() }
	}.getOrDefault(false)
	override fun delete(transferId: ULong) { file(transferId).delete() }
	private fun file(transferId: ULong) = File(directory, "$transferId.preview")
}
