package com.vnidrop.app.feature.send

import android.content.ClipData
import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File

@Composable
actual fun rememberTransferShareActions(): TransferShareActions {
	val context = LocalContext.current
	val activity = context as? ComponentActivity
	val nfcEnabled = activity?.let { NfcAdapter.getDefaultAdapter(it)?.isEnabled == true } == true
	var pendingExport by remember { mutableStateOf<PendingExport?>(null) }
	val exporter = rememberLauncherForActivityResult(
		ActivityResultContracts.CreateDocument(InvitationMimeType),
	) { uri ->
		val pending = pendingExport
		pendingExport = null
		if (pending != null && uri != null) {
			pending.callback(runCatching {
				context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(pending.ticket.encodeToByteArray()) }
					?: error("The selected destination could not be opened")
			})
		}
	}
	return remember(activity, exporter, nfcEnabled) {
		object : TransferShareActions {
			override val canUseNativeShare = activity != null
			override val nfcAvailability = when {
				activity == null -> NfcShareAvailability.Unavailable
				nfcEnabled -> NfcShareAvailability.Available
				else -> NfcShareAvailability.Unavailable
			}

			override fun exportInvitation(ticket: String, transferName: String, onResult: (Result<Unit>) -> Unit) {
				pendingExport = PendingExport(ticket, onResult)
				exporter.launch(invitationFileName(transferName))
			}

			override fun shareInvitation(ticket: String, transferName: String, onResult: (Result<Unit>) -> Unit) {
				onResult(runCatching {
					val directory = File(context.cacheDir, "transfer-invitations").apply { mkdirs() }
					val file = File(directory, invitationFileName(transferName)).apply { writeText(ticket) }
					val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
					val intent = Intent(Intent.ACTION_SEND).apply {
						type = InvitationMimeType
						putExtra(Intent.EXTRA_STREAM, uri)
						clipData = ClipData.newRawUri(file.name, uri)
						addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
					}
					context.startActivity(Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
				})
			}

			override fun writeInvitationToNfc(ticket: String, onResult: (Result<Unit>) -> Unit) {
				val host = activity ?: return onResult(Result.failure(UnsupportedOperationException("NFC is unavailable")))
				val adapter = NfcAdapter.getDefaultAdapter(host)
				if (adapter?.isEnabled != true) return onResult(Result.failure(UnsupportedOperationException("NFC is unavailable")))
				adapter.enableReaderMode(host, { tag ->
					val result = runCatching {
						val message = NdefMessage(arrayOf(NdefRecord.createMime(InvitationMimeType, ticket.encodeToByteArray())))
						val ndef = Ndef.get(tag)
						if (ndef != null) {
							ndef.connect()
							try {
								require(ndef.isWritable) { "This NFC tag is read-only" }
								require(ndef.maxSize >= message.toByteArray().size) { "This NFC tag is too small" }
								ndef.writeNdefMessage(message)
							} finally { ndef.close() }
						} else {
							val formatable = NdefFormatable.get(tag) ?: error("This NFC tag cannot store an invitation")
							formatable.connect()
							try { formatable.format(message) } finally { formatable.close() }
						}
					}
					host.runOnUiThread {
						adapter.disableReaderMode(host)
						onResult(result)
					}
				}, NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V, null)
			}

			override fun cancelNfcWrite() {
				activity?.let { host -> NfcAdapter.getDefaultAdapter(host)?.disableReaderMode(host) }
			}
		}
	}
}

private data class PendingExport(val ticket: String, val callback: (Result<Unit>) -> Unit)
private const val InvitationMimeType = "application/vnd.vnidrop.transfer"
