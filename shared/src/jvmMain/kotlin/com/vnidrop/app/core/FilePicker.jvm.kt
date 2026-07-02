package com.vnidrop.app.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

@Composable
actual fun rememberShareFilePicker(
	onFilePicked: (PickedShareFile) -> Unit,
	onError: (String) -> Unit,
): ShareFilePicker = remember(onFilePicked, onError) {
	object : ShareFilePicker {
		override fun pickFile() {
			try {
				val dialog = FileDialog(null as Frame?, "Select file to share", FileDialog.LOAD)
				dialog.isVisible = true
				val directory = dialog.directory
				val file = dialog.file
				if (directory != null && file != null) {
					val selected = File(directory, file)
					onFilePicked(PickedShareFile(selected.absolutePath, selected.name))
				}
			} catch (error: Throwable) {
				onError(error.message ?: error.toString())
			}
		}
	}
}

actual suspend fun sharePickedFile(
	repository: CoreRepository,
	file: PickedShareFile,
	transferName: String,
	senderName: String,
) {
	repository.sharePath(file.value, transferName, senderName)
}
