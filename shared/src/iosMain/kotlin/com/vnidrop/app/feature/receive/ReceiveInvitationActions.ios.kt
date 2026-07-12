package com.vnidrop.app.feature.receive

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.value
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVAuthorizationStatusDenied
import platform.AVFoundation.AVAuthorizationStatusNotDetermined
import platform.AVFoundation.AVAuthorizationStatusRestricted
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureMetadataOutput
import platform.AVFoundation.AVCaptureMetadataOutputObjectsDelegateProtocol
import platform.AVFoundation.AVCaptureOutput
import platform.AVFoundation.AVCaptureConnection
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureSessionPresetHigh
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVMetadataMachineReadableCodeObject
import platform.AVFoundation.AVMetadataObjectTypeQRCode
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.requestAccessForMediaType
import platform.CoreGraphics.CGRectMake
import platform.CoreNFC.NFCNDEFMessage
import platform.CoreNFC.NFCNDEFPayload
import platform.CoreNFC.NFCNDEFReaderSession
import platform.CoreNFC.NFCNDEFReaderSessionDelegateProtocol
import platform.CoreNFC.NFCTypeNameFormatMedia
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.UIKit.NSTextAlignmentCenter
import platform.UIKit.UIApplication
import platform.UIKit.UIButton
import platform.UIKit.UIButtonTypeSystem
import platform.UIKit.UIColor
import platform.UIKit.UIControlEventTouchUpInside
import platform.UIKit.UIControlStateNormal
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UILabel
import platform.UIKit.UIModalPresentationFormSheet
import platform.UIKit.UIModalPresentationFullScreen
import platform.UIKit.UIViewAutoresizingFlexibleHeight
import platform.UIKit.UIViewAutoresizingFlexibleWidth
import platform.UIKit.UIViewController
import platform.UniformTypeIdentifiers.UTTypeData
import platform.darwin.DISPATCH_QUEUE_PRIORITY_DEFAULT
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_global_queue
import platform.darwin.dispatch_get_main_queue

private var retainedInvitationDelegate: InvitationDocumentDelegate? = null
private var retainedQrScanner: QrScannerViewController? = null
private var retainedNfcReader: InvitationNfcReader? = null

@Composable
actual fun rememberReceiveInvitationActions(): ReceiveInvitationActions = remember {
	object : ReceiveInvitationActions {
		override val fileAvailability = ReceiveMethodAvailability.Available
		override val qrAvailability =
			if (AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo) != null) {
				ReceiveMethodAvailability.Available
			} else {
				ReceiveMethodAvailability.Unavailable
			}
		override val nfcAvailability =
			if (NFCNDEFReaderSession.readingAvailable) {
				ReceiveMethodAvailability.Available
			} else {
				ReceiveMethodAvailability.Unavailable
			}

		@OptIn(ExperimentalForeignApi::class)
		override fun pickInvitation(onResult: (Result<String>) -> Unit) {
			cancel()
			val presenter = topPresenter()
				?: return onResult(Result.failure(IllegalStateException("Could not find an iOS view controller")))
			val picker = UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeData), asCopy = true)
			val delegate = InvitationDocumentDelegate(onResult)
			retainedInvitationDelegate = delegate
			picker.delegate = delegate
			picker.modalPresentationStyle = UIModalPresentationFormSheet
			presenter.presentViewController(picker, animated = true, completion = null)
		}

		override fun scanQrCode(onResult: (Result<String>) -> Unit) {
			cancel()
			val presenter = topPresenter()
				?: return onResult(Result.failure(IllegalStateException("Could not find an iOS view controller")))
			ensureCameraAccess { granted ->
				if (!granted) {
					onResult(Result.failure(IllegalStateException("Camera access is required to scan QR codes")))
					return@ensureCameraAccess
				}
				val scanner = QrScannerViewController { result ->
					retainedQrScanner = null
					onResult(result)
				}
				retainedQrScanner = scanner
				scanner.modalPresentationStyle = UIModalPresentationFullScreen
				presenter.presentViewController(scanner, animated = true, completion = null)
			}
		}

		override fun readNfcInvitation(onResult: (Result<String>) -> Unit) {
			cancel()
			if (!NFCNDEFReaderSession.readingAvailable) {
				onResult(Result.failure(UnsupportedOperationException("NFC reading is unavailable on this device")))
				return
			}
			val reader = InvitationNfcReader { result ->
				retainedNfcReader = null
				onResult(result)
			}
			retainedNfcReader = reader
			reader.start()
		}

		override fun cancel() {
			retainedNfcReader?.cancel()
			retainedNfcReader = null
			retainedQrScanner?.cancelScan()
			retainedQrScanner = null
			retainedInvitationDelegate = null
		}
	}
}

private fun topPresenter(): UIViewController? {
	var controller = UIApplication.sharedApplication.keyWindow?.rootViewController
	while (controller?.presentedViewController != null) {
		controller = controller?.presentedViewController
	}
	return controller
}

private fun ensureCameraAccess(onResult: (Boolean) -> Unit) {
	when (AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)) {
		AVAuthorizationStatusAuthorized -> onResult(true)
		AVAuthorizationStatusNotDetermined -> {
			AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { granted ->
				dispatch_async(dispatch_get_main_queue()) { onResult(granted) }
			}
		}
		AVAuthorizationStatusDenied, AVAuthorizationStatusRestricted -> onResult(false)
		else -> onResult(false)
	}
}

private class InvitationDocumentDelegate(
	private val onResult: (Result<String>) -> Unit,
) : NSObject(), UIDocumentPickerDelegateProtocol {
	@OptIn(ExperimentalForeignApi::class)
	override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
		onResult(runCatching {
			val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL
				?: error("The selected invitation URL was invalid")
			val path = url.path ?: error("The invitation path was invalid")
			val data = NSFileManager.defaultManager.contentsAtPath(path) ?: error("The invitation could not be opened")
			val length = data.length.toInt()
			require(length <= MaxInvitationBytes) { "The invitation is too large" }
			val bytes = data.bytes?.readBytes(length) ?: error("The invitation is empty")
			decodeInvitationBytes(bytes)
		})
		retainedInvitationDelegate = null
	}

	override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
		retainedInvitationDelegate = null
	}
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private class QrScannerViewController(
	private val onResult: (Result<String>) -> Unit,
) : UIViewController(nibName = null, bundle = null), AVCaptureMetadataOutputObjectsDelegateProtocol {
	private val session = AVCaptureSession()
	private var previewLayer: AVCaptureVideoPreviewLayer? = null
	private var finished = false
	private val closeTarget = ButtonTarget { cancelScan() }

	override fun viewDidLoad() {
		super.viewDidLoad()
		view.backgroundColor = UIColor.blackColor

		val hint = UILabel(frame = view.bounds).apply {
			text = "Point the camera at a VniDrop QR code"
			textColor = UIColor.whiteColor
			textAlignment = NSTextAlignmentCenter
			numberOfLines = 0
			autoresizingMask = UIViewAutoresizingFlexibleWidth or UIViewAutoresizingFlexibleHeight
		}
		view.addSubview(hint)

		val close = UIButton.buttonWithType(UIButtonTypeSystem).apply {
			setTitle("Cancel", forState = UIControlStateNormal)
			setTitleColor(UIColor.whiteColor, forState = UIControlStateNormal)
			addTarget(closeTarget, platform.objc.sel_registerName("invoke"), UIControlEventTouchUpInside)
			setFrame(CGRectMake(16.0, 52.0, 88.0, 36.0))
		}
		view.addSubview(close)
		configureSession()
	}

	override fun viewDidLayoutSubviews() {
		super.viewDidLayoutSubviews()
		previewLayer?.setFrame(view.bounds)
	}

	override fun viewWillDisappear(animated: Boolean) {
		super.viewWillDisappear(animated)
		if (session.running) session.stopRunning()
	}

	fun cancelScan() {
		finish(Result.failure(IllegalStateException("QR scanning was cancelled")))
	}

	private fun configureSession() {
		val device = AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo)
			?: return finish(Result.failure(IllegalStateException("No camera is available")))

		memScoped {
			val errorPtr = alloc<ObjCObjectVar<NSError?>>()
			val input = AVCaptureDeviceInput.deviceInputWithDevice(device, errorPtr.ptr)
			if (input == null) {
				finish(
					Result.failure(
						IllegalStateException(errorPtr.value?.localizedDescription ?: "Could not open the camera"),
					),
				)
				return
			}
			if (!session.canAddInput(input)) {
				finish(Result.failure(IllegalStateException("Could not configure the camera input")))
				return
			}
			session.addInput(input)
		}

		val output = AVCaptureMetadataOutput()
		if (!session.canAddOutput(output)) {
			finish(Result.failure(IllegalStateException("Could not configure the QR scanner")))
			return
		}
		session.addOutput(output)
		output.setMetadataObjectsDelegate(this, queue = dispatch_get_main_queue())
		output.metadataObjectTypes = listOf(AVMetadataObjectTypeQRCode)

		val layer = AVCaptureVideoPreviewLayer(session = session).apply {
			videoGravity = AVLayerVideoGravityResizeAspectFill
			setFrame(view.bounds)
		}
		view.layer.insertSublayer(layer, atIndex = 0u)
		previewLayer = layer
		session.sessionPreset = AVCaptureSessionPresetHigh

		dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT.toLong(), 0u)) {
			session.startRunning()
		}
	}

	override fun captureOutput(
		output: AVCaptureOutput,
		didOutputMetadataObjects: List<*>,
		fromConnection: AVCaptureConnection,
	) {
		val code = didOutputMetadataObjects
			.mapNotNull { it as? AVMetadataMachineReadableCodeObject }
			.firstOrNull { it.type == AVMetadataObjectTypeQRCode }
		val value = code?.stringValue?.trim().orEmpty()
		if (value.isNotEmpty()) {
			finish(Result.success(value))
		}
	}

	private fun finish(result: Result<String>) {
		if (finished) return
		finished = true
		if (session.running) session.stopRunning()
		if (presentingViewController != null) {
			dismissViewControllerAnimated(true) { onResult(result) }
		} else {
			onResult(result)
		}
	}
}

@OptIn(BetaInteropApi::class)
private class ButtonTarget(
	private val onClick: () -> Unit,
) : NSObject() {
	@ObjCAction
	fun invoke() {
		onClick()
	}
}

@OptIn(ExperimentalForeignApi::class)
private class InvitationNfcReader(
	private val onResult: (Result<String>) -> Unit,
) : NSObject(), NFCNDEFReaderSessionDelegateProtocol {
	private var session: NFCNDEFReaderSession? = null
	private var finished = false

	fun start() {
		val reader = NFCNDEFReaderSession(this, dispatch_get_main_queue(), invalidateAfterFirstRead = true)
		reader.alertMessage = "Hold your iPhone near a VniDrop invitation tag"
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
				Result.failure(IllegalStateException("NFC reading was cancelled"))
			} else {
				Result.failure(
					IllegalStateException(didInvalidateWithError.localizedDescription ?: "NFC reading failed"),
				)
			},
		)
	}

	override fun readerSession(session: NFCNDEFReaderSession, didDetectNDEFs: List<*>) {
		val ticket = runCatching {
			val messages = didDetectNDEFs.mapNotNull { it as? NFCNDEFMessage }
			messages
				.flatMap { message -> message.records.mapNotNull { it as? NFCNDEFPayload } }
				.firstNotNullOfOrNull(::payloadAsInvitation)
				?: error("This NFC tag does not contain a VniDrop invitation")
		}
		session.invalidateSession()
		finish(ticket)
	}

	private fun finish(result: Result<String>) {
		if (finished) return
		finished = true
		session = null
		dispatch_async(dispatch_get_main_queue()) { onResult(result) }
	}
}

@OptIn(ExperimentalForeignApi::class)
private fun payloadAsInvitation(payload: NFCNDEFPayload): String? {
	val type = payload.type?.toByteArray()?.decodeToString() ?: return null
	val data = payload.payload?.toByteArray() ?: return null
	return when {
		payload.typeNameFormat == NFCTypeNameFormatMedia && type == InvitationMimeType ->
			decodeInvitationBytes(data)
		payload.typeNameFormat == NFCTypeNameFormatMedia && type.startsWith("text/") ->
			decodeInvitationBytes(data)
		else -> null
	}
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
	val length = this.length.toInt()
	if (length <= 0) return ByteArray(0)
	return this.bytes?.readBytes(length) ?: ByteArray(0)
}
