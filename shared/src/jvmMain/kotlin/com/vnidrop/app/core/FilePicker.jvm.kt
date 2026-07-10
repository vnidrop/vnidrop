package com.vnidrop.app.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.awt.EventQueue
import java.awt.FileDialog
import java.awt.Frame
import java.awt.KeyboardFocusManager
import java.io.File
import javax.swing.JFileChooser

@Composable
actual fun rememberShareFilePicker(
	onFilePicked: (PickedShareFile) -> Unit,
	onError: (String) -> Unit,
): ShareFilePicker = remember(onFilePicked, onError) {
	object : ShareFilePicker {
		override fun pickFile() {
			openPicker(onError) {
				pickShareFile()?.let(onFilePicked)
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
				val selected = pickDirectory() ?: return@openPicker
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

private fun pickShareFile(): PickedShareFile? {
	val dialog = nativeFileDialog("Select file to share")
	return try {
		dialog.isVisible = true
		val directory = dialog.directory
		val file = dialog.file
		if (directory != null && file != null) {
			val selected = File(directory, file)
			PickedShareFile(selected.absolutePath, selected.name)
		} else {
			null
		}
	} finally {
		dialog.dispose()
	}
}

private fun pickDirectory(): File? =
	if (isMacOs()) {
		val dialog = withMacDirectoryDialog {
			nativeFileDialog("Select receive folder").apply { isVisible = true }
		}
		try {
			val directory = dialog.directory ?: return null
			dialog.file
				?.let { File(directory, it) }
				?: File(directory)
		} finally {
			dialog.dispose()
		}
	} else {
		val chooser = JFileChooser().apply {
			dialogTitle = "Select receive folder"
			fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
			isAcceptAllFileFilterUsed = false
		}
		if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
	}

private fun <T> withMacDirectoryDialog(block: () -> T): T {
	if (!isMacOs()) return block()

	val key = "apple.awt.fileDialogForDirectories"
	val previous = System.getProperty(key)
	System.setProperty(key, "true")
	return try {
		block()
	} finally {
		if (previous == null) {
			System.clearProperty(key)
		} else {
			System.setProperty(key, previous)
		}
	}
}

private fun isMacOs(): Boolean =
	System.getProperty("os.name").startsWith("Mac", ignoreCase = true)
