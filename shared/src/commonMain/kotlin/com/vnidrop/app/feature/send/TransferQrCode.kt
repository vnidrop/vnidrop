package com.vnidrop.app.feature.send

import qrcode.QRCode
import qrcode.raw.ErrorCorrectionLevel

private const val QrCellSize = 8
private const val QrQuietZoneModules = 4
// qrcode-kotlin 4.5.0 falls back to version 40 above version 20's byte capacity.
private val QrLowErrorCorrectionByteCapacities = intArrayOf(
	17, 32, 53, 78, 106, 134, 154, 192, 230, 271,
	321, 367, 425, 458, 520, 586, 644, 718, 792, 858,
	929, 1003, 1091, 1171, 1273, 1367, 1465, 1528, 1628, 1732,
	1840, 1952, 2068, 2188, 2303, 2431, 2563, 2699, 2809, 2953,
)

internal fun buildTransferQrCode(ticket: String): QRCode =
	QRCode.ofSquares()
		.withSize(QrCellSize)
		.withInnerSpacing(0)
		.withMargin(QrQuietZoneModules * QrCellSize)
		.withErrorCorrectionLevel(ErrorCorrectionLevel.LOW)
		.withInformationDensity(transferQrInformationDensity(ticket))
		.build(ticket)

internal fun transferQrInformationDensity(ticket: String): Int {
	val byteCount = ticket.encodeToByteArray().size
	val index = QrLowErrorCorrectionByteCapacities.indexOfFirst { byteCount <= it }
	return if (index >= 0) index + 1 else throw IllegalArgumentException("The invitation is too large for a QR code")
}
