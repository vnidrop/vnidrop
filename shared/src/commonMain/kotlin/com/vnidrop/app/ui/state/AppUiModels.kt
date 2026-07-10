package com.vnidrop.app.ui.state

import com.vnidrop.app.core.CoreEventModel
import com.vnidrop.app.core.Transfer
import kotlin.math.roundToInt

enum class WindowClass {
	Phone,
	Tablet,
	Desktop,
}

fun windowClassFor(widthDp: Float): WindowClass =
	when {
		widthDp >= 920f -> WindowClass.Desktop
		widthDp >= 600f -> WindowClass.Tablet
		else -> WindowClass.Phone
	}

fun useBottomNavigation(windowClass: WindowClass): Boolean =
	windowClass == WindowClass.Phone

data class TransferProgress(
	val transferId: ULong?,
	val phase: String,
	val label: String,
	val progress: Float?,
)

fun displayNameForStatus(status: String): String =
	when (status.lowercase()) {
		"sharing" -> "Sharing"
		"receiving" -> "Receiving"
		"done" -> "Done"
		"cancelled" -> "Cancelled"
		"stopped" -> "Stopped"
		"failed" -> "Failed"
		else -> status.replaceFirstChar { it.uppercase() }
	}

fun Transfer.isActiveTransfer(): Boolean =
	status.lowercase() in activeTransferStatuses

fun summarizeProgress(events: List<CoreEventModel>): List<TransferProgress> =
	events
		.filter { event -> event.transferId != null && event.phase in progressPhases }
		.distinctBy { event -> "${event.transferId}:${event.phase}" }
		.take(6)
		.map { event ->
			TransferProgress(
				transferId = event.transferId,
				phase = event.phase,
				label = eventLabel(event),
				progress = parseProgress(event.dataJson),
			)
		}

fun transferSubtitle(transfer: Transfer): String {
	val pieces = listOfNotNull(
		transfer.transferName,
		"${transfer.fileCount} file${if (transfer.fileCount == 1UL) "" else "s"}",
		formatBytes(transfer.totalSize),
	)
	return pieces.joinToString(" | ")
}

fun formatBytes(size: ULong): String {
	val value = size.toDouble()
	val units = listOf("B", "KB", "MB", "GB", "TB")
	var scaled = value
	var unitIndex = 0
	while (scaled >= 1024.0 && unitIndex < units.lastIndex) {
		scaled /= 1024.0
		unitIndex += 1
	}
	return if (unitIndex == 0) {
		"${size} ${units[unitIndex]}"
	} else {
		"${(scaled * 10).roundToInt() / 10.0} ${units[unitIndex]}"
	}
}

private val progressPhases = setOf("import", "ticket", "access", "transfer", "download", "export", "lifecycle")
private val activeTransferStatuses = setOf("importing", "sharing", "receiving")

private fun eventLabel(event: CoreEventModel): String {
	val direction = event.direction?.replaceFirstChar { it.uppercase() }
	val phase = event.phase.replaceFirstChar { it.uppercase() }
	val kind = event.kind.replace('-', ' ')
	return listOfNotNull(direction, phase, kind).joinToString(" - ")
}

private fun parseProgress(json: String): Float? {
	val transferred = findNumber(json, "transferred") ?: findNumber(json, "downloaded") ?: findNumber(json, "written")
	val total = findNumber(json, "total") ?: findNumber(json, "total_size")
	if (transferred == null || total == null || total <= 0.0) return null
	return (transferred / total).toFloat().coerceIn(0f, 1f)
}

private fun findNumber(json: String, key: String): Double? {
	val marker = "\"$key\":"
	val start = json.indexOf(marker)
	if (start < 0) return null
	val valueStart = start + marker.length
	val valueEnd = json.indexOfAny(charArrayOf(',', '}'), valueStart).takeIf { it >= 0 } ?: json.length
	return json.substring(valueStart, valueEnd).trim().toDoubleOrNull()
}
