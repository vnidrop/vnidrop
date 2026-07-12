package com.vnidrop.app.ui.state

import com.vnidrop.app.core.CoreEventModel
import com.vnidrop.app.core.Transfer
import com.vnidrop.app.core.TransferStatus
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
	val kind: String,
	val label: String,
	val progress: Float?,
	val detail: String? = null,
)

fun displayNameForStatus(status: TransferStatus): String =
	when (status) {
		TransferStatus.Importing -> "Preparing"
		TransferStatus.Sharing -> "Available"
		TransferStatus.Receiving -> "Receiving"
		TransferStatus.Done -> "Completed"
		TransferStatus.Cancelled -> "Cancelled"
		TransferStatus.Stopped -> "Stopped"
		TransferStatus.Failed -> "Failed"
	}

fun Transfer.isActiveTransfer(): Boolean =
	status in activeTransferStatuses

fun Transfer.canCancelTransfer(): Boolean =
	status in setOf(TransferStatus.Importing, TransferStatus.Sharing, TransferStatus.Receiving)

/**
 * Latest progress snapshot for a transfer, derived from core events.
 *
 * Events are assumed newest-first (as stored by [com.vnidrop.app.core.CoreRepository]).
 */
fun progressForTransfer(events: List<CoreEventModel>, transferId: ULong): TransferProgress? {
	val relevant = events.filter { event ->
		event.transferId == transferId && event.phase in progressPhases && event.kind in progressKinds
	}
	val latest = relevant.firstOrNull() ?: return null
	val sizeHint = findKnownSize(events, transferId)
	return TransferProgress(
		transferId = transferId,
		phase = latest.phase,
		kind = latest.kind,
		label = humanProgressLabel(latest),
		progress = parseProgress(latest.dataJson, sizeHint),
		detail = progressDetail(latest),
	)
}

fun summarizeProgress(events: List<CoreEventModel>): List<TransferProgress> =
	events
		.mapNotNull { it.transferId }
		.distinct()
		.take(6)
		.mapNotNull { progressForTransfer(events, it) }

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

private val progressPhases = setOf(
	"import", "ticket", "access", "transfer", "download", "export",
	"lifecycle", "network", "handshake", "error",
)

private val progressKinds = setOf(
	"started", "copy-progress", "copy-done", "outboard-progress", "done",
	"created", "progress", "completed", "aborted", "failed",
	"connecting", "connected", "found-collection",
	"cancelled", "share-stopped",
)

private val activeTransferStatuses = setOf(
	TransferStatus.Importing,
	TransferStatus.Sharing,
	TransferStatus.Receiving,
)

private fun humanProgressLabel(event: CoreEventModel): String = when {
	event.phase == "import" && event.kind == "copy-progress" -> "Preparing files"
	event.phase == "import" && event.kind == "outboard-progress" -> "Indexing files"
	event.phase == "import" && event.kind == "started" -> "Preparing transfer"
	event.phase == "import" && event.kind == "done" -> "Files ready"
	event.phase == "ticket" && event.kind == "created" -> "Share ready"
	event.phase == "network" && event.kind == "connecting" -> "Connecting to sender"
	event.phase == "network" && event.kind == "connected" -> "Connected"
	event.phase == "handshake" -> "Requesting access"
	event.phase == "download" && event.kind == "found-collection" -> "Found files"
	event.phase == "download" && event.kind == "progress" -> "Downloading"
	event.phase == "export" && event.kind == "progress" -> "Saving files"
	event.phase == "transfer" && event.kind == "progress" -> "Sending to receiver"
	event.phase == "transfer" && event.kind == "started" -> "Receiver connected"
	event.phase == "transfer" && event.kind == "completed" -> "Send completed"
	event.phase == "lifecycle" && event.kind == "done" -> "Completed"
	event.phase == "lifecycle" && event.kind == "cancelled" -> "Cancelled"
	event.kind == "failed" -> "Failed"
	else -> listOfNotNull(
		event.direction?.replaceFirstChar { it.uppercase() },
		event.phase.replaceFirstChar { it.uppercase() },
		event.kind.replace('-', ' '),
	).joinToString(" · ")
}

private fun progressDetail(event: CoreEventModel): String? {
	val fileName = findString(event.dataJson, "file_name")
	val current = findNumber(event.dataJson, "current_file_index")?.toLong()
	val totalFiles = findNumber(event.dataJson, "total_files")?.toLong()
	return when {
		fileName != null && current != null && totalFiles != null && totalFiles > 0 ->
			"$fileName (${current + 1}/$totalFiles)"
		fileName != null -> fileName
		else -> null
	}
}

internal fun parseProgress(json: String, sizeHint: Double? = null): Float? {
	val transferred = findNumber(json, "exported")
		?: findNumber(json, "downloaded")
		?: findNumber(json, "offset")
		?: findNumber(json, "end_offset")
		?: findNumber(json, "transferred")
		?: findNumber(json, "written")
	val total = findNumber(json, "file_size")
		?: findNumber(json, "total_size")
		?: findNumber(json, "size")
		?: findNumber(json, "total")
		?: sizeHint
	if (transferred == null || total == null || total <= 0.0) return null
	return (transferred / total).toFloat().coerceIn(0f, 1f)
}

private fun findKnownSize(events: List<CoreEventModel>, transferId: ULong): Double? {
	for (event in events) {
		if (event.transferId != transferId) continue
		findNumber(event.dataJson, "size")?.takeIf { it > 0 }?.let { return it }
		findNumber(event.dataJson, "total_size")?.takeIf { it > 0 }?.let { return it }
		findNumber(event.dataJson, "file_size")?.takeIf { it > 0 }?.let { return it }
	}
	return null
}

private fun findNumber(json: String, key: String): Double? {
	val marker = "\"$key\":"
	val start = json.indexOf(marker)
	if (start < 0) return null
	val valueStart = start + marker.length
	val valueEnd = json.indexOfAny(charArrayOf(',', '}', ']'), valueStart).takeIf { it >= 0 } ?: json.length
	return json.substring(valueStart, valueEnd).trim().trim('"').toDoubleOrNull()
}

private fun findString(json: String, key: String): String? {
	val marker = "\"$key\":"
	val start = json.indexOf(marker)
	if (start < 0) return null
	val after = json.substring(start + marker.length).trimStart()
	if (!after.startsWith('"')) return null
	val end = after.indexOf('"', 1)
	if (end <= 1) return null
	return after.substring(1, end)
}
