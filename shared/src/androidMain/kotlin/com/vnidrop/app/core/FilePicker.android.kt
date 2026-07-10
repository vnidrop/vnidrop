package com.vnidrop.app.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberShareFilePicker(
	onFilePicked: (PickedShareFile) -> Unit,
	onError: (String) -> Unit,
): ShareFilePicker {
	val context = LocalContext.current
	val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
		if (uri != null) {
			onFilePicked(PickedShareFile(uri.toString(), context.displayName(uri)))
		}
	}
	return remember(launcher) {
		object : ShareFilePicker {
			override fun pickFile() {
				launcher.launch(arrayOf("*/*"))
			}
		}
	}
}

@Composable
actual fun rememberReceiveFolderPicker(
	onFolderPicked: (ReceiveFolder) -> Unit,
	onError: (String) -> Unit,
): ReceiveFolderPicker {
	val context = LocalContext.current
	val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
		if (uri != null) {
			runCatching {
				context.contentResolver.takePersistableUriPermission(
					uri,
					Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
				)
			}
			onFolderPicked(
				ReceiveFolder(
					kind = ReceiveFolderKind.AndroidTreeUri,
					value = uri.toString(),
					displayName = uri.lastPathSegment ?: "Downloads",
				),
			)
		}
	}
	return remember(launcher) {
		object : ReceiveFolderPicker {
			override fun pickFolder() {
				launcher.launch(null)
			}
		}
	}
}

private fun Context.displayName(uri: Uri): String {
	contentResolver.query(uri, null, null, null, null)?.use { cursor ->
		val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
		if (nameIndex >= 0 && cursor.moveToFirst()) {
			return cursor.getString(nameIndex)
		}
	}
	return uri.lastPathSegment ?: "transfer"
}
