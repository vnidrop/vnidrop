package com.vnidrop.app.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.awt.EventQueue
import java.awt.FileDialog
import java.awt.Frame
import java.awt.KeyboardFocusManager
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO
import javax.swing.JFileChooser
import javax.swing.filechooser.FileSystemView

@Composable
actual fun rememberShareFilePicker(
	onFilesPicked: (List<PickedShareFile>) -> Unit,
	onError: (String) -> Unit,
): ShareFilePicker = remember(onFilesPicked, onError) {
	object : ShareFilePicker {
		override fun pickFiles() {
			openPicker(onError) {
				val selected = pickShareFiles()
				if (selected.isNotEmpty()) onFilesPicked(selected)
			}
		}

		override fun pickFolder() {
			openPicker(onError) {
				val selected = pickDirectory(title = "Select folder to share") ?: return@openPicker
				onFilesPicked(
					listOf(
						PickedShareFile(
							value = selected.absolutePath,
							displayName = selected.name.ifBlank { selected.absolutePath },
							sizeBytes = null,
							thumbnailBytes = selected.systemIconPng(),
							isDirectory = true,
						),
					),
				)
			}
		}
	}
}

@Composable
actual fun rememberReceiveFolderPicker(
	onFolderPicked: (ReceiveFolder) -> Unit,
	onError: (String) -> Unit,
): ReceiveFolderPicker = remember(onFolderPicked, onError) {
	object : ReceiveFolderPicker {
		override fun pickFolder() {
			openPicker(onError) {
				val selected = pickDirectory(title = "Select receive folder") ?: return@openPicker
				onFolderPicked(
					ReceiveFolder(
						kind = ReceiveFolderKind.FileSystemPath,
						value = selected.absolutePath,
						displayName = selected.name.ifBlank { selected.absolutePath },
					),
				)
			}
		}
	}
}

private fun openPicker(
	onError: (String) -> Unit,
	block: () -> Unit,
) {
	EventQueue.invokeLater {
		try {
			block()
		} catch (error: Throwable) {
			onError(error.message ?: error.toString())
		}
	}
}

private fun nativeFileDialog(title: String): FileDialog =
	FileDialog(activeFrame(), title, FileDialog.LOAD)

private fun activeFrame(): Frame? {
	val activeWindow = KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow
	return activeWindow as? Frame
		?: Frame.getFrames().firstOrNull { it.isActive || it.isFocused }
		?: Frame.getFrames().firstOrNull { it.isVisible }
}

private fun pickShareFiles(): List<PickedShareFile> {
	val dialog = nativeFileDialog("Select files to share").apply {
		isMultipleMode = true
	}
	return try {
		dialog.isVisible = true
		val directory = dialog.directory ?: return emptyList()
		val names = dialog.files?.map { it.name }.orEmpty().ifEmpty {
			dialog.file?.let { listOf(it) }.orEmpty()
		}
		names.map { name ->
			val selected = File(directory, name)
			PickedShareFile(
				selected.absolutePath,
				selected.name,
				selected.length().takeIf { it >= 0L }?.toULong(),
				selected.systemIconPng(),
			)
		}
	} finally {
		dialog.dispose()
	}
}

private fun File.systemIconPng(): ByteArray? = runCatching {
	val icon = FileSystemView.getFileSystemView().getSystemIcon(this, 128, 128)
	val image = BufferedImage(icon.iconWidth, icon.iconHeight, BufferedImage.TYPE_INT_ARGB)
	val graphics = image.createGraphics()
	try {
		icon.paintIcon(null, graphics, 0, 0)
	} finally {
		graphics.dispose()
	}
	ByteArrayOutputStream().use { output ->
		ImageIO.write(image, "png", output)
		output.toByteArray()
	}
}.getOrNull()

private fun pickDirectory(title: String): File? {
	val chooser = JFileChooser().apply {
		dialogTitle = title
		fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
		isAcceptAllFileFilterUsed = false
	}
	return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
}
