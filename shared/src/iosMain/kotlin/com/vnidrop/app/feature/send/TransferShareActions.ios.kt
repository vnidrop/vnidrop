package com.vnidrop.app.feature.send

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.vnidrop.app.feature.receive.VniDropInvitationMimeType
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCSignatureOverride
import platform.CoreNFC.NFCNDEFMessage
import platform.CoreNFC.NFCNDEFPayload
import platform.CoreNFC.NFCNDEFReaderSession
import platform.CoreNFC.NFCNDEFReaderSessionDelegateProtocol
import platform.CoreNFC.NFCNDEFStatusNotSupported
import platform.CoreNFC.NFCNDEFStatusReadOnly
import platform.CoreNFC.NFCNDEFTagProtocol
import platform.CoreNFC.NFCTypeNameFormatMedia
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSURL
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Foundation.writeToFile
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIModalPresentationFormSheet
import platform.darwin.NSObject
import platform.darwin.dispatch_get_main_queue

private var retainedNfcWriter: InvitationNfcWriter? = null

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberTransferShareActions(): TransferShareActions = remember {
	object : TransferShareActions {
		override val canUseNativeShare = true
		override val nfcAvailability =
			if (NFCNDEFReaderSession.readingAvailable) {
				NfcShareAvailability.Available
			} else {
				NfcShareAvailability.Unavailable
			}

		override fun exportInvitation(ticket: String, transferName: String, onResult: (Result<Unit>) -> Unit) {
			onResult(runCatching {
				val url = createInvitation(ticket, transferName)
				val picker = UIDocumentPickerViewController(forExportingURLs = listOf(url), asCopy = true)
				present(picker)
			})
		}

		override fun shareInvitation(ticket: String, transferName: String, onResult: (Result<Unit>) -> Unit) {
			onResult(runCatching {
				val url = createInvitation(ticket, transferName)
				val controller = UIActivityViewController(activityItems = listOf(url), applicationActivities = null)
				controller.modalPresentationStyle = UIModalPresentationFormSheet
				presenter().presentViewController(controller, animated = true, completion = null)
			})
		}

		override fun writeInvitationToNfc(ticket: String, onResult: (Result<Unit>) -> Unit) {
			cancelNfcWrite()
			if (!NFCNDEFReaderSession.readingAvailable) {
				onResult(Result.failure(UnsupportedOperationException("NFC is unavailable on this device")))
				return
			}
			val writer = InvitationNfcWriter(ticket) { result ->
				retainedNfcWriter = null
				onResult(result)
			}
			retainedNfcWriter = writer
			writer.start()
		}

		override fun cancelNfcWrite() {
			retainedNfcWriter?.cancel()
			retainedNfcWriter = null
		}
	}
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun createInvitation(ticket: String, transferName: String): NSURL {
	val path = NSTemporaryDirectory().trimEnd('/') + "/" + invitationFileName(transferName)
	val text = NSString.create(string = ticket)
	require(text.writeToFile(path, atomically = true, encoding = NSUTF8StringEncoding, error = null)) {
		"The invitation file could not be created"
	}
	return NSURL.fileURLWithPath(path)
}

private fun presenter() = UIApplication.sharedApplication.keyWindow?.rootViewController
	?: error("Could not find an iOS view controller")

private fun present(controller: platform.UIKit.UIViewController) {
	presenter().presentViewController(controller, animated = true, completion = null)
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private class InvitationNfcWriter(
	private val ticket: String,
	private val onResult: (Result<Unit>) -> Unit,
) : NSObject(), NFCNDEFReaderSessionDelegateProtocol {
	private var session: NFCNDEFReaderSession? = null
	private var finished = false

	fun start() {
		val reader = NFCNDEFReaderSession(this, dispatch_get_main_queue(), invalidateAfterFirstRead = false)
		reader.alertMessage = "Hold your iPhone near a writable NFC tag"
		session = reader
		reader.beginSession()
	}

	fun cancel() {
		session?.invalidateSession()
		session = null
	}

	override fun readerSession(session: NFCNDEFReaderSession, didInvalidateWithError: NSError) {
		if (finished) return
		// NFCReaderError.readerSessionInvalidationErrorUserCanceled == 200
		val cancelled = didInvalidateWithError.code == 200L
		finish(
			if (cancelled) {
				Result.failure(IllegalStateException("NFC writing was cancelled"))
			} else {
				Result.failure(
					IllegalStateException(didInvalidateWithError.localizedDescription),
				)
			},
		)
	}

	@ObjCSignatureOverride
	override fun readerSession(session: NFCNDEFReaderSession, didDetectNDEFs: List<*>) {
		// Prefer tag-based write path via didDetectTags when available.
	}

	@ObjCSignatureOverride
	override fun readerSession(session: NFCNDEFReaderSession, didDetectTags: List<*>) {
		val tag = didDetectTags.firstOrNull() as? NFCNDEFTagProtocol
			?: return finish(Result.failure(IllegalStateException("No NFC tag was detected")))

		session.connectToTag(tag) { connectError ->
			if (connectError != null) {
				finish(Result.failure(IllegalStateException(connectError.localizedDescription)))
				return@connectToTag
			}
			tag.queryNDEFStatusWithCompletionHandler { status, _, queryError ->
				if (queryError != null) {
					finish(Result.failure(IllegalStateException(queryError.localizedDescription)))
					return@queryNDEFStatusWithCompletionHandler
				}
				when (status) {
					NFCNDEFStatusNotSupported -> {
						finish(Result.failure(IllegalStateException("This NFC tag does not support NDEF")))
					}
					NFCNDEFStatusReadOnly -> {
						finish(Result.failure(IllegalStateException("This NFC tag is read-only")))
					}
					else -> {
						val message = invitationNdefMessage(ticket)
							?: return@queryNDEFStatusWithCompletionHandler finish(
								Result.failure(IllegalStateException("Could not encode the invitation for NFC")),
							)
						tag.writeNDEF(message) { writeError ->
							if (writeError != null) {
								finish(Result.failure(IllegalStateException(writeError.localizedDescription)))
							} else {
								session.alertMessage = "Invitation written"
								session.invalidateSession()
								finish(Result.success(Unit))
							}
						}
					}
				}
			}
		}
	}

	private fun finish(result: Result<Unit>) {
		if (finished) return
		finished = true
		session = null
		onResult(result)
	}
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun invitationNdefMessage(ticket: String): NFCNDEFMessage? {
	val type = NSString.create(string = VniDropInvitationMimeType).dataUsingEncoding(NSUTF8StringEncoding) ?: return null
	val payload = NSString.create(string = ticket).dataUsingEncoding(NSUTF8StringEncoding) ?: return null
	val record = NFCNDEFPayload(
		format = NFCTypeNameFormatMedia,
		type = type,
		identifier = NSData(),
		payload = payload,
	)
	return NFCNDEFMessage(nDEFRecords = listOf(record))
}
