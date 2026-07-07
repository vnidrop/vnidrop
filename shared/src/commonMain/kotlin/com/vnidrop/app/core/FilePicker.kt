package com.vnidrop.app.core

import androidx.compose.runtime.Composable

data class PickedShareFile(
	val value: String,
	val displayName: String,
)

interface ShareFilePicker {
	fun pickFile()
}

interface ReceiveFolderPicker {
	fun pickFolder()
}

@Composable
expect fun rememberShareFilePicker(
	onFilePicked: (PickedShareFile) -> Unit,
	onError: (String) -> Unit,
): ShareFilePicker

@Composable
expect fun rememberReceiveFolderPicker(
	onFolderPicked: (ReceiveFolder) -> Unit,
	onError: (String) -> Unit,
): ReceiveFolderPicker

expect suspend fun sharePickedFile(
	repository: CoreRepository,
	file: PickedShareFile,
	transferName: String,
	senderName: String,
)
