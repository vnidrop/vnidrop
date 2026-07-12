package com.vnidrop.app.feature.receive

import android.nfc.NfcAdapter
import android.nfc.tech.Ndef
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

@Composable
actual fun rememberReceiveInvitationActions(): ReceiveInvitationActions {
	val context = LocalContext.current
	val activity = context as? ComponentActivity
	var fileResult by remember { mutableStateOf<((Result<String>) -> Unit)?>(null) }
	val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
		val callback = fileResult.also { fileResult = null } ?: return@rememberLauncherForActivityResult
		if (uri == null) return@rememberLauncherForActivityResult
		callback(runCatching {
			val bytes = context.contentResolver.openInputStream(uri)?.use { it.readNBytes(MaxInvitationBytes + 1) }
				?: error("The invitation could not be opened")
			decodeInvitationBytes(bytes)
		})
	}
	val nfcAdapter = remember(activity) { activity?.let(NfcAdapter::getDefaultAdapter) }
	return remember(activity, filePicker, nfcAdapter) {
		object : ReceiveInvitationActions {
			override val fileAvailability = ReceiveMethodAvailability.Available
			override val qrAvailability = if (activity != null) ReceiveMethodAvailability.Available else ReceiveMethodAvailability.Unavailable
			override val nfcAvailability = if (nfcAdapter?.isEnabled == true) ReceiveMethodAvailability.Available else ReceiveMethodAvailability.Unavailable

			override fun pickInvitation(onResult: (Result<String>) -> Unit) {
				// Stop NFC reader before another acquisition path so only one method is active.
				cancel()
				fileResult = onResult
				filePicker.launch(arrayOf(InvitationMimeType, "application/octet-stream", "text/plain", "*/*"))
			}

			override fun scanQrCode(onResult: (Result<String>) -> Unit) {
				val host = activity ?: return onResult(Result.failure(UnsupportedOperationException("QR scanning is unavailable")))
				cancel()
				val options = GmsBarcodeScannerOptions.Builder()
					.setBarcodeFormats(Barcode.FORMAT_QR_CODE)
					.enableAutoZoom()
					.build()
				GmsBarcodeScanning.getClient(host, options).startScan()
					.addOnSuccessListener { barcode ->
						val value = barcode.rawValue
						onResult(if (value.isNullOrBlank()) Result.failure(IllegalArgumentException("The QR code is empty")) else Result.success(value))
					}
					.addOnFailureListener { onResult(Result.failure(it)) }
					.addOnCanceledListener {
						onResult(Result.failure(IllegalStateException("QR scanning was cancelled")))
					}
			}

			override fun readNfcInvitation(onResult: (Result<String>) -> Unit) {
				val host = activity ?: return onResult(Result.failure(UnsupportedOperationException("NFC is unavailable")))
				val adapter = nfcAdapter?.takeIf { it.isEnabled }
					?: return onResult(Result.failure(UnsupportedOperationException("NFC is unavailable")))
				cancel()
				adapter.enableReaderMode(host, { tag ->
					val result = runCatching {
						val ndef = Ndef.get(tag) ?: error("This NFC tag does not contain an invitation")
						ndef.connect()
						try {
							val record = ndef.ndefMessage?.records?.firstOrNull { record ->
								record.tnf == android.nfc.NdefRecord.TNF_MIME_MEDIA && record.type.decodeToString() == InvitationMimeType
							} ?: error("This NFC tag does not contain a VniDrop invitation")
							decodeInvitationBytes(record.payload)
						} finally { ndef.close() }
					}
					host.runOnUiThread {
						adapter.disableReaderMode(host)
						onResult(result)
					}
				}, NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V, null)
			}

			override fun cancel() {
				activity?.let { nfcAdapter?.disableReaderMode(it) }
			}
		}
	}
}
