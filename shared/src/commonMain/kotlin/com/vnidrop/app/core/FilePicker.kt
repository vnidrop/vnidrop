package com.vnidrop.app.core

import androidx.compose.runtime.Composable

data class PickedShareFile(
	val value: String,
	val displayName: String,
	val sizeBytes: ULong? = null,
	val thumbnailBytes: ByteArray? = null,
)

interface ShareFilePicker {
	/** Opens a platform picker that may return one or more files. */
	fun pickFiles()
}

interface ReceiveFolderPicker {
	fun pickFolder()
}

@Composable
expect fun rememberShareFilePicker(
	onFilesPicked: (List<PickedShareFile>) -> Unit,
	onError: (String) -> Unit,
): ShareFilePicker

@Composable
expect fun rememberReceiveFolderPicker(
	onFolderPicked: (ReceiveFolder) -> Unit,
	onError: (String) -> Unit,
): ReceiveFolderPicker
