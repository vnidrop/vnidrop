package com.vnidrop.app.core

import androidx.compose.runtime.Composable

data class PickedShareFile(
	val value: String,
	val displayName: String,
	val sizeBytes: ULong? = null,
	val thumbnailBytes: ByteArray? = null,
	/** App-owned picker copy that may be deleted after import or when selection is abandoned. */
	val isTemporaryCopy: Boolean = false,
	/**
	 * When true, [value] is a directory (filesystem path, iOS security-scoped
	 * folder URL, or Android document tree URI). Platform share code expands or
	 * walks it; Rust cannot treat an Android FD as a directory.
	 */
	val isDirectory: Boolean = false,
)

interface ShareFilePicker {
	/** Opens a platform picker that may return one or more files. */
	fun pickFiles()

	/** Opens a platform folder picker for sharing a directory as one transfer. */
	fun pickFolder()
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
